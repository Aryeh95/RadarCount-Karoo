package io.github.ykn.variaradarpro.datatypes.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.github.ykn.variaradarpro.engine.Units
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Base class for Glance-based data type widgets.
 *
 * Uses Glance RemoteViews to avoid action accumulation memory issues
 * that occur with traditional RemoteViews in Karoo SDK.
 *
 * Views re-render only when their inputs change, rate-limited to the
 * Karoo's 1 Hz view update limit, instead of on a fixed timer.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class, FlowPreview::class)
abstract class GlanceDataType(
    protected val radarExtension: VariaRadarExtension,
    typeId: String
) : DataTypeImpl("eiradar", typeId) {

    companion object {
        private const val TAG = "GlanceDataType"

        /** Karoo SDK limitation: 1Hz updates */
        private const val VIEW_UPDATE_INTERVAL_MS = 1000L

        /** Preview state for data field selection */
        val PREVIEW_STATE = WidgetState.Threat(
            level = ThreatLevel.WARNING,
            vehicleCount = 2,
            nearestDistanceM = 45
        )
        const val PREVIEW_PASS_COUNT = 12
    }

    /** Everything a widget render depends on. */
    protected data class RenderInput(
        val state: WidgetState,
        val settings: PresetSettings,
        val muted: Boolean,
        val passCount: Int,
        val lapPassCount: Int,
        val useImperial: Boolean
    )

    private val glance = GlanceRemoteViews()

    /**
     * Render the widget content using Glance composables.
     */
    @Composable
    protected abstract fun Content(input: RenderInput, config: ViewConfig)

    /**
     * Format distance respecting user's unit preference.
     */
    protected fun formatDistance(meters: Int, useImperial: Boolean): String =
        Units.formatDistance(meters, useImperial)

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.Streaming(
            DataPoint(dataTypeId = dataTypeId, values = emptyMap())
        ))
    }

    private fun previewInput(): RenderInput = RenderInput(
        state = PREVIEW_STATE,
        settings = radarExtension.preferencesRepository.settingsState.value,
        muted = false,
        passCount = PREVIEW_PASS_COUNT,
        lapPassCount = PREVIEW_PASS_COUNT / 2,
        useImperial = radarExtension.useImperial.value
    )

    private fun liveInputs(): Flow<RenderInput> {
        val engine = radarExtension.radarEngine
        return combine(
            engine.widgetState,
            radarExtension.preferencesRepository.settingsState,
            radarExtension.alertsMuted,
            engine.passCount,
            engine.lapPassCount,
            radarExtension.useImperial
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            RenderInput(
                state = values[0] as WidgetState,
                settings = values[1] as PresetSettings,
                muted = values[2] as Boolean,
                passCount = values[3] as Int,
                lapPassCount = values[4] as Int,
                useImperial = values[5] as Boolean
            )
        }.distinctUntilChanged()
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        android.util.Log.d(TAG, "[$dataTypeId] Starting view: grid=${config.gridSize}, preview=${config.preview}")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        suspend fun render(input: RenderInput) {
            try {
                val result = glance.compose(context, DpSize.Unspecified) {
                    Content(input, config)
                }
                emitter.updateView(result.remoteViews)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.w(TAG, "[$dataTypeId] Render error: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        if (config.preview) {
            scope.launch { render(previewInput()) }
            emitter.setCancellable { scope.cancel() }
            return
        }

        scope.launch {
            val inputs = liveInputs()
            // Render the current state immediately, then only on change,
            // never faster than the Karoo's 1 Hz limit.
            render(inputs.first())
            inputs.sample(VIEW_UPDATE_INTERVAL_MS).collect { render(it) }
        }

        emitter.setCancellable {
            scope.cancel()
        }
    }
}

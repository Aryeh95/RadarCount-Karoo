package io.github.aryeh95.radarcount.datatypes.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.models.ThreatLevel
import io.github.aryeh95.radarcount.data.models.WidgetState
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
 * Views re-render only when their inputs change, rate-limited to the
 * Karoo's 1 Hz view update limit.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class, FlowPreview::class)
abstract class GlanceDataType(
    protected val radarExtension: RadarCountExtension,
    typeId: String
) : DataTypeImpl(RadarCountExtension.EXTENSION_ID, typeId) {

    companion object {
        private const val TAG = "GlanceDataType"

        /** Karoo SDK limitation: 1Hz updates */
        private const val VIEW_UPDATE_INTERVAL_MS = 1000L

        val PREVIEW_INPUT = RenderInput(
            state = WidgetState.Threat(ThreatLevel.WARNING, vehicleCount = 2, nearestDistanceM = 45),
            passCount = 12,
            lapPassCount = 4,
            closingSpeedMps = 8.0,
            riderSpeedMps = 7.0,
            useImperial = false
        )
    }

    /** Everything a widget render depends on. */
    data class RenderInput(
        val state: WidgetState,
        val passCount: Int,
        val lapPassCount: Int,
        val closingSpeedMps: Double,
        val riderSpeedMps: Double,
        val useImperial: Boolean
    ) {
        val connected: Boolean
            get() = state is WidgetState.Clear || state is WidgetState.Threat
    }

    private val glance = GlanceRemoteViews()

    @Composable
    protected abstract fun Content(input: RenderInput, config: ViewConfig)

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.Streaming(
            DataPoint(dataTypeId = dataTypeId, values = emptyMap())
        ))
    }

    private fun liveInputs(): Flow<RenderInput> {
        val engine = radarExtension.radarEngine
        return combine(
            engine.widgetState,
            engine.passCount,
            engine.lapPassCount,
            engine.closingSpeedMps,
            radarExtension.riderSpeedMps,
            radarExtension.useImperial
        ) { values ->
            RenderInput(
                state = values[0] as WidgetState,
                passCount = values[1] as Int,
                lapPassCount = values[2] as Int,
                closingSpeedMps = values[3] as Double,
                riderSpeedMps = values[4] as Double,
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
            scope.launch { render(PREVIEW_INPUT) }
            emitter.setCancellable { scope.cancel() }
            return
        }

        radarExtension.acquireRadar()
        scope.launch {
            val inputs = liveInputs()
            render(inputs.first())
            inputs.sample(VIEW_UPDATE_INTERVAL_MS).collect { render(it) }
        }

        emitter.setCancellable {
            scope.cancel()
            radarExtension.releaseRadar()
        }
    }
}

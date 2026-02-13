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
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Base class for Glance-based data type widgets.
 *
 * Uses Glance RemoteViews to avoid action accumulation memory issues
 * that occur with traditional RemoteViews in Karoo SDK.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
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
    }

    private val glance = GlanceRemoteViews()

    /**
     * Render the widget content using Glance composables.
     */
    @Composable
    protected abstract fun Content(
        state: WidgetState,
        settings: PresetSettings,
        config: ViewConfig
    )

    /**
     * Get current settings asynchronously within a coroutine.
     */
    protected suspend fun getCurrentSettings(): PresetSettings {
        return try {
            radarExtension.preferencesRepository.settingsFlow.first()
        } catch (e: Exception) {
            PresetSettings()
        }
    }

    /**
     * Format distance respecting user's unit preference.
     */
    protected fun formatDistance(meters: Int): String {
        return if (radarExtension.useImperial.value) {
            "${(meters * 3.281).toInt()}ft"
        } else {
            "${meters}m"
        }
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.Streaming(
            DataPoint(dataTypeId = dataTypeId, values = emptyMap())
        ))
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        android.util.Log.d(TAG, "[$dataTypeId] Starting view: grid=${config.gridSize}, preview=${config.preview}")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        // Preview mode
        if (config.preview) {
            scope.launch {
                try {
                    val settings = getCurrentSettings()
                    val result = glance.compose(context, DpSize.Unspecified) {
                        Content(PREVIEW_STATE, settings, config)
                    }
                    emitter.updateView(result.remoteViews)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "[$dataTypeId] Preview error: ${e.message}", e)
                }
            }
            emitter.setCancellable { scope.cancel() }
            return
        }

        // Live mode — initial render
        scope.launch {
            try {
                val initialState = radarExtension.radarEngine.widgetState.value
                val settings = getCurrentSettings()
                val result = glance.compose(context, DpSize.Unspecified) {
                    Content(initialState, settings, config)
                }
                emitter.updateView(result.remoteViews)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[$dataTypeId] Initial render failed: ${e.message}", e)
            }
        }

        // Fixed-rate updates at 1Hz
        scope.launch {
            var nextUpdateTime = System.currentTimeMillis() + VIEW_UPDATE_INTERVAL_MS
            while (isActive) {
                val now = System.currentTimeMillis()
                val delayMs = nextUpdateTime - now
                if (delayMs > 0) delay(delayMs)

                nextUpdateTime += VIEW_UPDATE_INTERVAL_MS

                val currentTime = System.currentTimeMillis()
                if (nextUpdateTime < currentTime) {
                    nextUpdateTime = currentTime + VIEW_UPDATE_INTERVAL_MS
                }

                try {
                    val currentState = radarExtension.radarEngine.widgetState.value
                    val settings = getCurrentSettings()
                    val result = glance.compose(context, DpSize.Unspecified) {
                        Content(currentState, settings, config)
                    }
                    emitter.updateView(result.remoteViews)
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    android.util.Log.w(TAG, "[$dataTypeId] Update error: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }

        emitter.setCancellable {
            scope.cancel()
        }
    }
}

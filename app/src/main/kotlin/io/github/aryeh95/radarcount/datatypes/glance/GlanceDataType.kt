package io.github.aryeh95.radarcount.datatypes.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.models.ThreatLevel
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.ShowCustomStreamState
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
        /** How long each state shows in the page-editor preview. */
        private const val PREVIEW_CYCLE_MS = 2000L

        val PREVIEW_INPUT = RenderInput(
            state = WidgetState.Threat(ThreatLevel.WARNING, vehicleCount = 2, nearestDistanceM = 174),
            passCount = 48,
            closingSpeedMps = 20.0,
            riderSpeedMps = 7.0,
            useImperial = false,
            settings = Settings(),
            rideTimeMs = 3_600_000L
        )
    }

    /** Everything a widget render depends on. */
    data class RenderInput(
        val state: WidgetState,
        val passCount: Int,
        val closingSpeedMps: Double?,
        val riderSpeedMps: Double,
        val useImperial: Boolean,
        val settings: Settings,
        val rideTimeMs: Long
    ) {
        val connected: Boolean
            get() = state is WidgetState.Clear || state is WidgetState.Threat
    }

    private val glance = GlanceRemoteViews()

    /** Screen density, captured from the first view so sizes can be computed in dp. */
    @Volatile
    protected var density: Float = 1f

    @Composable
    protected abstract fun Content(input: RenderInput, config: ViewConfig)

    /** Whether the Karoo draws its caption strip over this field. Off hands the whole tile to [Content]. */
    protected open val karooHeader: Boolean get() = true

    /** Text shown on every field while no radar is connected. */
    protected fun noRadarText(): String = radarExtension.getString(io.github.aryeh95.radarcount.R.string.widget_no_radar)

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
            engine.closingSpeedMps,
            radarExtension.riderSpeedMps,
            radarExtension.useImperial,
            radarExtension.settings,
            radarExtension.rideTimeMs
        ) { values ->
            RenderInput(
                state = values[0] as WidgetState,
                passCount = values[1] as Int,
                closingSpeedMps = values[2] as Double?,
                riderSpeedMps = values[3] as Double,
                useImperial = values[4] as Boolean,
                settings = values[5] as Settings,
                rideTimeMs = values[6] as Long
            )
        }.distinctUntilChanged()
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // Same setup as other extensions (e.g. ki2) whose custom fields render
        // like the Karoo's own: standard header on, and an empty custom
        // stream state so the Karoo does not draw its own placeholder over
        // the value area.
        emitter.onNext(UpdateGraphicConfig(showHeader = karooHeader))
        emitter.onNext(ShowCustomStreamState(message = "", color = null))
        density = context.resources.displayMetrics.density
        if (typeId == "radar-combo") radarExtension.reportComboViewConfig(config)

        android.util.Log.d(TAG, "[$dataTypeId] Starting view: grid=${config.gridSize}, size=${config.viewSize}, text=${config.textSize}, density=$density, preview=${config.preview}")

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
            // Page-editor preview: alternate between the no-vehicle and the
            // approaching state so both layouts of the combo field show.
            scope.launch {
                var approaching = true
                while (true) {
                    val base = if (approaching) PREVIEW_INPUT else PREVIEW_INPUT.copy(state = WidgetState.Clear, closingSpeedMps = null)
                    render(base.copy(settings = radarExtension.settings.value, useImperial = radarExtension.useImperial.value))
                    approaching = !approaching
                    kotlinx.coroutines.delay(PREVIEW_CYCLE_MS)
                }
            }
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

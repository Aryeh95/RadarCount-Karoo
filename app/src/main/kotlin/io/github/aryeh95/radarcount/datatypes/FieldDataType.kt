package io.github.aryeh95.radarcount.datatypes

import android.content.Context
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.PassHoldSetting
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.datatypes.render.FieldColors
import io.github.aryeh95.radarcount.datatypes.render.FieldFrame
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.github.aryeh95.radarcount.engine.TargetTracker
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Base class for the data fields. Each frame is drawn into a bitmap with
 * exact glyph metrics (see render.FieldBitmaps) and sent as RemoteViews
 * holding that one image, so it looks the same on the Karoo 2 and the
 * Karoo 3 and the settings preview can show the very same pixels.
 *
 * Each view is updated from one loop at the Karoo's 1 Hz view update
 * limit. A frame is drawn only when what it shows may have changed (see
 * [key]), and one identical to the one on screen is not sent again.
 */
abstract class FieldDataType(
    protected val radarExtension: RadarCountExtension,
    typeId: String
) : DataTypeImpl(RadarCountExtension.EXTENSION_ID, typeId) {

    companion object {
        private const val TAG = "FieldDataType"

        /** Karoo SDK limitation: 1Hz updates */
        private const val VIEW_UPDATE_INTERVAL_MS = 1000L
        /**
         * ViewEmitter silently drops an update sent sooner than this after
         * the last one. Checked here too, so a frame is only taken as shown
         * once it was really sent.
         */
        private const val MIN_SEND_GAP_MS = 900L
        /** How long each state shows in the page-editor preview. */
        private const val PREVIEW_CYCLE_MS = 2000L

        val PREVIEW_INPUT = RenderInput(
            state = RadarStatus.Live(level = 2, vehicles = 2, nearestM = 174),
            passCount = 48,
            closingSpeedMps = 20.0,
            riderSpeedMps = 7.0,
            useImperial = false,
            settings = Settings(),
            rideTimeMs = 3_600_000L
        )

        /**
         * The page-editor preview's frames, in order: the sample car
         * approaching, no vehicle, and, while [hold] is on, the same car
         * just after it passed at [nowMs]. [seq] tells repeated passes apart.
         */
        fun previewStates(hold: PassHoldSetting, seq: Int, nowMs: Long): List<RenderInput> {
            val clear = PREVIEW_INPUT.copy(state = RadarStatus.Live.CLEAR, closingSpeedMps = null)
            return buildList {
                add(PREVIEW_INPUT)
                add(clear)
                if (hold != PassHoldSetting.OFF) add(clear.copy(lastPass = previewPass(seq, nowMs)))
            }
        }

        /** The sample car after it has gone by, as the page editor and the settings preview show it. [seq] tells repeated passes apart. */
        fun previewPass(seq: Int, atMs: Long = System.currentTimeMillis()) =
            TargetTracker.Pass(seq = seq, atMs = atMs, closingMps = PREVIEW_INPUT.closingSpeedMps, riderMps = PREVIEW_INPUT.riderSpeedMps, cars = 1)
    }

    /** Everything a field render depends on. */
    data class RenderInput(
        val state: RadarStatus,
        val passCount: Int,
        val closingSpeedMps: Double?,
        val riderSpeedMps: Double,
        val useImperial: Boolean,
        val settings: Settings,
        val rideTimeMs: Long,
        /** The last pass and its speed, for the fields that hold it after a pass (see [PassHold]). */
        val lastPass: TargetTracker.Pass? = null
    ) {
        val connected: Boolean
            get() = state is RadarStatus.Live
    }

    /** Screen density, captured from the first view so sizes can be computed in dp. */
    @Volatile
    protected var density: Float = 1f

    /** Draws one frame for [input] in a field of [config]'s size. */
    protected abstract fun frame(context: Context, input: RenderInput, config: ViewConfig): FieldFrame

    /**
     * Everything [frame] draws from at this moment, cheap to compute: a
     * frame is drawn only when this changes. The ride time is left out
     * (only the per-hour rate shows it) and the Karoo's night mode, which
     * AUTO colours follow, is put in.
     */
    protected open fun key(context: Context, input: RenderInput, config: ViewConfig): Any =
        listOf(input.copy(rideTimeMs = 0L), FieldColors.systemNight(context))

    /**
     * Renders one frame as the Karoo would receive it, outside the Karoo. For the on-device render
     * test, which applies the result on a Karoo 2 / Karoo 3 to catch views Android rejects and
     * glyphs that would be cut off.
     */
    internal fun renderForTest(context: Context, input: RenderInput, config: ViewConfig): FieldFrame {
        density = context.resources.displayMetrics.density
        return frame(context, input, config)
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.Streaming(
            DataPoint(dataTypeId = dataTypeId, values = emptyMap())
        ))
    }

    private fun liveInputs(): Flow<RenderInput> {
        val engine = radarExtension.radarEngine
        return combine(
            engine.status,
            engine.passCount,
            engine.closingSpeedMps,
            radarExtension.riderSpeedMps,
            radarExtension.imperialUnits,
            radarExtension.settings,
            radarExtension.rideTimeMs,
            engine.lastPass
        ) { values ->
            RenderInput(
                state = values[0] as RadarStatus,
                passCount = values[1] as Int,
                closingSpeedMps = values[2] as Double?,
                riderSpeedMps = values[3] as Double,
                useImperial = values[4] as Boolean,
                settings = values[5] as Settings,
                rideTimeMs = values[6] as Long,
                lastPass = values[7] as TargetTracker.Pass?
            )
        }.distinctUntilChanged()
    }

    /** How long this field holds a passed car's speed; only Vehicle Speed and the Radar field do. */
    protected open fun passHold(settings: Settings): PassHoldSetting = PassHoldSetting.OFF

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        // The Karoo's header strip off: every field draws its own header (or,
        // the Radar field, none) in the whole tile, so a long name is never
        // wrapped onto a second line. And an empty custom stream state so
        // the Karoo does not draw its own placeholder over the value area.
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        emitter.onNext(ShowCustomStreamState(message = "", color = null))
        density = context.resources.displayMetrics.density
        radarExtension.reportViewConfig(typeId, config)

        android.util.Log.d(TAG, "[$dataTypeId] Starting view: grid=${config.gridSize}, size=${config.viewSize}, text=${config.textSize}, density=$density, preview=${config.preview}")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var shown: FieldFrame? = null
        var shownKey: Any? = null
        var sentMs = 0L

        // The only caller of updateView, so updates are never closer than
        // the emitter allows; a frame that could not be sent yet is drawn
        // again on the next call, since its key is still not the shown one.
        fun render(input: RenderInput) {
            try {
                val key = key(context, input, config)
                if (key == shownKey) return
                val now = System.currentTimeMillis()
                if (now - sentMs < MIN_SEND_GAP_MS) return
                val next = frame(context, input, config)
                if (!next.looksLike(shown)) {
                    emitter.updateView(next.views)
                    sentMs = now
                    shown = next
                }
                shownKey = key
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "[$dataTypeId] Render error: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        if (config.preview) {
            // Page-editor preview: cycle through the approaching and the
            // no-vehicle state so both layouts of the combo field show, and,
            // while this field holds a passed car's speed, the held frame too.
            scope.launch {
                var step = 0
                var passSeq = 0
                while (true) {
                    val settings = radarExtension.settings.value
                    val states = previewStates(passHold(settings), ++passSeq, System.currentTimeMillis())
                    val base = states[step % states.size]
                    android.util.Log.d(TAG, "[$dataTypeId] Preview frame ${step % states.size + 1} of ${states.size} (pass hold ${passHold(settings)})")
                    render(base.copy(settings = settings, useImperial = radarExtension.imperialUnits.value))
                    step = (step + 1) % states.size
                    delay(PREVIEW_CYCLE_MS)
                }
            }
            emitter.setCancellable { scope.cancel() }
            return
        }

        radarExtension.acquireRadar()
        scope.launch {
            val inputs = liveInputs()
            var latest = inputs.first()
            launch { inputs.collect { latest = it } }
            // Every second, not only on new input, for what changes without
            // one: the end of the Radar field's hold, of a held pass speed,
            // and the night mode.
            while (true) {
                render(latest)
                delay(VIEW_UPDATE_INTERVAL_MS)
            }
        }

        emitter.setCancellable {
            scope.cancel()
            radarExtension.releaseRadar()
        }
    }
}

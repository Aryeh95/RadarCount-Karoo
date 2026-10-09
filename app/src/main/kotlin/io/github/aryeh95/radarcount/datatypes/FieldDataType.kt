package io.github.aryeh95.radarcount.datatypes

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

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
    protected val service: RadarCountExtension,
    typeId: String
) : DataTypeImpl(RadarCountExtension.EXTENSION_ID, typeId) {

    companion object {
        private const val TAG = "RadarCountField"

        /** The Karoo takes at most one view update a second. */
        private const val FRAME_PERIOD_MS = 1000L
        /**
         * ViewEmitter silently drops an update sent sooner than this after
         * the last one. Checked here too, so a frame is only taken as shown
         * once it was really sent.
         */
        private const val MIN_SEND_GAP_MS = 900L
        /** How long each state shows in the page-editor preview. */
        private const val PREVIEW_CYCLE_MS = 2000L

        /**
         * The sample the previews draw: one car 120 m back, closing at
         * 20 m/s on a rider doing 7 m/s, 48 cars into an hour's ride.
         */
        val PREVIEW_INPUT = RenderInput(
            state = RadarStatus.Live(level = 1, vehicles = 1, nearestM = 120),
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

    /** The fields are drawn, not numbers: their stream is streaming but carries no values. */
    override fun startStream(emitter: Emitter<StreamState>) =
        emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId)))

    private fun liveInputs(): Flow<RenderInput> {
        val engine = service.radarEngine
        return combine(
            engine.status,
            engine.passCount,
            engine.closingSpeedMps,
            service.riderSpeedMps,
            service.imperialUnits,
            service.settings,
            service.rideTimeMs,
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
        // Each field draws its own header (the Radar field none) over the
        // whole tile, so the Karoo's header strip is off and a long name is
        // never wrapped onto a second line. The empty custom stream state
        // keeps the Karoo's own placeholder off the value area.
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        emitter.onNext(ShowCustomStreamState(message = "", color = null))
        density = context.resources.displayMetrics.density
        service.reportViewConfig(typeId, config)
        Log.d(TAG, "$dataTypeId: ${if (config.preview) "page-editor view" else "view"} ${config.viewSize.first}x${config.viewSize.second} px, grid ${config.gridSize.first}x${config.gridSize.second}, text ${config.textSize} sp, density $density")

        val scope = MainScope() + Dispatchers.Main.immediate
        val sender = FrameSender(context, config, emitter)
        if (config.preview) {
            scope.launch { cyclePreview(sender) }
            emitter.setCancellable { scope.cancel() }
        } else {
            service.acquireRadar()
            scope.launch { followRadar(sender) }
            emitter.setCancellable {
                scope.cancel()
                service.releaseRadar()
            }
        }
    }

    /**
     * The page editor's preview: each of [previewStates] in turn, so both
     * layouts of the Radar field show and, while this field holds a passed
     * car's speed, the held frame too.
     */
    private suspend fun cyclePreview(sender: FrameSender) {
        var step = 0
        var passSeq = 0
        while (true) {
            val settings = service.settings.value
            val hold = passHold(settings)
            val states = previewStates(hold, ++passSeq, System.currentTimeMillis())
            val i = step % states.size
            Log.d(TAG, "$dataTypeId: preview ${i + 1}/${states.size}, pass hold $hold")
            sender.send(states[i].copy(settings = settings, useImperial = service.imperialUnits.value))
            step = (i + 1) % states.size
            delay(PREVIEW_CYCLE_MS)
        }
    }

    /**
     * The live field: the latest inputs every [FRAME_PERIOD_MS], not only
     * when they change, for what changes without them: the end of the
     * Radar field's hold and of a held pass speed, and the night mode.
     */
    private suspend fun followRadar(sender: FrameSender) = coroutineScope {
        val inputs = liveInputs()
        var latest = inputs.first()
        launch { inputs.collect { latest = it } }
        while (true) {
            sender.send(latest)
            delay(FRAME_PERIOD_MS)
        }
    }

    /**
     * One view's frames. Its [send] is the view's only path to updateView,
     * so updates are never closer together than the emitter accepts. A
     * frame whose [key] is on screen is not drawn, one that draws the same
     * as the screen is not sent, and one held back by the gap is drawn again
     * on the next call, since its key is still not the shown one.
     */
    private inner class FrameSender(
        private val context: Context,
        private val config: ViewConfig,
        private val emitter: ViewEmitter
    ) {
        private var onScreen: FieldFrame? = null
        private var onScreenKey: Any? = null
        private var sentAtMs = 0L

        fun send(input: RenderInput) {
            try {
                val key = key(context, input, config)
                if (key == onScreenKey) return
                val now = System.currentTimeMillis()
                if (now - sentAtMs < MIN_SEND_GAP_MS) return
                val next = frame(context, input, config)
                if (!next.looksLike(onScreen)) {
                    emitter.updateView(next.views)
                    sentAtMs = now
                    onScreen = next
                }
                onScreenKey = key
            } catch (t: Throwable) {
                Log.w(TAG, "$dataTypeId: frame not drawn: $t")
            }
        }
    }
}

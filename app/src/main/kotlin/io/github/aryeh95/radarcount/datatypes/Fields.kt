package io.github.aryeh95.radarcount.datatypes

import android.content.Context
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.PassHoldSetting
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.github.aryeh95.radarcount.datatypes.render.FieldBitmaps
import io.github.aryeh95.radarcount.datatypes.render.FieldColors
import io.github.aryeh95.radarcount.datatypes.render.FieldFrame
import io.github.aryeh95.radarcount.datatypes.render.FieldHeader
import io.github.aryeh95.radarcount.datatypes.render.FieldImage
import io.github.aryeh95.radarcount.datatypes.render.FieldViews
import io.github.aryeh95.radarcount.engine.TargetTracker
import io.github.aryeh95.radarcount.engine.Units
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.roundToInt

/**
 * A single field's header: its name and icon as registered with the Karoo
 * (extension_info.xml), and the setting that turns it off.
 */
/** A single field's header: its name (which may depend on the settings), icon, and whether it shows. */
private class Header(val icon: Int, val label: (Settings) -> Int, val shown: (Settings) -> Boolean) {
    constructor(label: Int, icon: Int, shown: (Settings) -> Boolean) : this(icon, { label }, shown)
}

/**
 * A single field: its own header in the Karoo's style (see FieldHeader),
 * unless turned off, and under it [text] laid out like ki2's TextView,
 * which renders custom fields indistinguishably from the Karoo's built-in
 * ones: vertically centred in the area below the header, horizontally
 * aligned per the field setting, at 97% of the Karoo's numeric size,
 * monospace, and with [tag] under it if given. A [dim] value is drawn in
 * the label grey: a passed car's held speed, not a live one.
 */
private fun valueFrame(
    context: Context, config: ViewConfig, density: Float, settings: Settings, header: Header, text: String,
    tag: String? = null, dim: Boolean = false, layoutSample: String? = null
): FieldFrame {
    val palette = FieldColors.palette(context, settings.theme)
    return singleFrame(context, config, density, settings, header.takeIf { it.shown(settings) }, palette, palette.icon) { roomH ->
        FieldBitmaps.value(text, tag, if (dim) palette.held else palette.text, palette.label, config, density, roomH, layoutSample)
    }
}

/**
 * What every field shows while no radar is connected: grey, no tag. A
 * single field keeps its header if shown, the icon in the text colour as
 * the Karoo's own icons are without data; the Radar field has none.
 */
private fun noRadarFrame(context: Context, config: ViewConfig, density: Float, settings: Settings, header: Header? = null): FieldFrame {
    val text = context.getString(R.string.widget_no_radar)
    if (header == null) {
        val image = FieldBitmaps.value(text, null, FieldColors.NEUTRAL, FieldColors.NEUTRAL, config, density)
        return FieldViews.value(context, null, image, config.alignment)
    }
    val palette = FieldColors.palette(context, settings.theme)
    return singleFrame(context, config, density, settings, header.takeIf { it.shown(settings) }, palette, palette.text) { roomH ->
        FieldBitmaps.value(text, null, FieldColors.NEUTRAL, FieldColors.NEUTRAL, config, density, roomH)
    }
}

/**
 * [header] across the top of the tile and the image [value] draws for the
 * height left under it; without a header the value has the whole tile.
 */
/** Space kept free around a single field's value, split above and below it. */
private const val VALUE_VERTICAL_INSET_DP = 4f

private fun singleFrame(
    context: Context, config: ViewConfig, density: Float, settings: Settings, header: Header?, palette: FieldColors.Palette, iconColor: Int,
    value: (roomH: Int) -> FieldImage
): FieldFrame {
    val top = header?.let { FieldHeader.draw(context, context.getString(it.label(settings)), it.icon, config, density, palette.text, iconColor) }
    // Keep the value clear of the tile's edge and rounded corners: the image is centred, so half of this lands below it.
    val inset = FieldBitmaps.layoutPx(VALUE_VERTICAL_INSET_DP, density)
    val roomH = (config.viewSize.second - (top?.image?.bitmap?.height ?: 0) - inset).coerceAtLeast(1)
    return FieldViews.value(context, top, value(roomH), config.alignment)
}

private data class Derived(
    val tracked: Boolean,
    /** Null while the closing speed is not yet known (first seconds of a track). */
    val relative: Int?,
    val absolute: Int?,
    val unit: String,
    val distance: String?,
    val behind: Int,
    val shownSpeed: Int?,
    val showsAbsolute: Boolean
)

private fun derive(input: FieldDataType.RenderInput, mode: SpeedSetting = SpeedSetting.ABSOLUTE): Derived {
    val threat = input.state as? WidgetState.Threat
    val tracked = threat != null
    // A known 0 (car holding station) is not the same as no estimate yet.
    // Absolute is the car's road speed, so it adds the rider's speed even
    // when the car is not gaining.
    val relative = input.closingSpeedMps?.let {
        RadarCountExtension.toUserSpeedUnits(it, input.useImperial)
    }
    val absolute = relative?.plus(
        RadarCountExtension.toUserSpeedUnits(input.riderSpeedMps, input.useImperial)
    )
    val distance = if (threat != null && threat.nearestDistanceM > 0) {
        Units.formatDistance(threat.nearestDistanceM, input.useImperial)
    } else {
        null
    }
    val showsAbsolute = mode == SpeedSetting.ABSOLUTE
    return Derived(
        tracked, relative, absolute, Units.speedUnitLabel(input.useImperial), distance, threat?.vehicleCount ?: 0,
        shownSpeed = if (showsAbsolute) absolute else relative,
        showsAbsolute = showsAbsolute
    )
}

/** Vehicles that have passed the rider this ride. */
class VehicleCountDataType(
    radarExtension: RadarCountExtension
) : FieldDataType(radarExtension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "vehicle-count"
        private val header = Header(R.string.datatype_count, R.drawable.ic_car) { it.countHeader }

        /** The field for [input]. Shared with the settings preview, which shows exactly these pixels. */
        fun render(context: Context, input: RenderInput, config: ViewConfig, density: Float): FieldFrame {
            if (!input.connected) return noRadarFrame(context, config, density, input.settings, header)
            return valueFrame(context, config, density, input.settings, header, input.passCount.toString())
        }
    }

    override fun frame(context: Context, input: RenderInput, config: ViewConfig): FieldFrame =
        render(context, input, config, density)
}

/**
 * Speed of the nearest vehicle, relative or absolute per settings, with a
 * RELATIVE or ABSOLUTE tag under it if that is turned on in settings. If
 * set to, it keeps a passed car's speed for a few seconds after it has gone
 * by, in grey and tagged PASSED (see [PassHold]).
 */
class ApproachSpeedDataType(
    radarExtension: RadarCountExtension
) : FieldDataType(radarExtension, TYPE_ID) {

    override fun passHold(settings: Settings): PassHoldSetting = settings.speedPassHold

    companion object {
        const val TYPE_ID = "approach-speed"
        // Absolute, the default, is plain VEHICLE SPEED; relative says so in the header.
        private val header = Header(
            R.drawable.ic_speed,
            { if (it.speedMode == SpeedSetting.RELATIVE) R.string.header_speed_rel else R.string.datatype_speed }
        ) { it.speedHeader }

        /** The pass whose speed shows at [nowMs] in place of a live one, or null. */
        internal fun held(input: RenderInput, nowMs: Long): TargetTracker.Pass? {
            val d = derive(input, input.settings.speedMode)
            return PassHold.held(input.lastPass, input.settings.speedPassHold, liveKnown = d.tracked && d.shownSpeed != null, nowMs)
        }

        /**
         * The field for [input] at [nowMs], which decides whether a held
         * pass speed still shows. Shared with the settings preview, which
         * shows exactly these pixels.
         */
        fun render(context: Context, input: RenderInput, config: ViewConfig, density: Float, nowMs: Long): FieldFrame {
            if (!input.connected) return noRadarFrame(context, config, density, input.settings, header)
            val d = derive(input, input.settings.speedMode)
            val held = held(input, nowMs)
            val text = when {
                d.tracked && d.shownSpeed != null -> "${d.shownSpeed}${d.unit}"
                held != null -> "${PassHold.shownSpeed(held, input.useImperial, d.showsAbsolute)}${d.unit}"
                else -> "--"
            }
            // A held speed is always marked PASSED: grey alone may not read in sunlight.
            val tag = if (held != null) context.getString(R.string.speed_tag_passed) else null
            // Laid out for a speed with its unit, so "--" and a speed sit alike and the p of mph hangs below.
            return valueFrame(context, config, density, input.settings, header, text, tag, dim = held != null, layoutSample = "88${d.unit}")
        }
    }

    override fun frame(context: Context, input: RenderInput, config: ViewConfig): FieldFrame =
        render(context, input, config, density, System.currentTimeMillis())

    /** The held pass too: its end changes nothing in [input], so the frame would not be redrawn. */
    override fun key(context: Context, input: RenderInput, config: ViewConfig): Any =
        listOf(super.key(context, input, config), held(input, System.currentTimeMillis())?.seq)
}

/** Vehicles passed per hour of recording time. */
class VehiclesPerHourDataType(
    radarExtension: RadarCountExtension
) : FieldDataType(radarExtension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "vehicles-per-hour"
        /** Below this much recording time the rate swings wildly, so show nothing. */
        private const val MIN_RIDE_MS = 120_000L
        private val header = Header(R.string.datatype_rate, R.drawable.ic_car) { it.rateHeader }

        internal fun format(passCount: Int, rideTimeMs: Long): String {
            if (rideTimeMs < MIN_RIDE_MS) return "--"
            val perHour = passCount * 3_600_000.0 / rideTimeMs
            return if (perHour < 10.0) String.format(java.util.Locale.US, "%.1f", perHour) else perHour.roundToInt().toString()
        }

        /** The field for [input]. Shared with the settings preview, which shows exactly these pixels. */
        fun render(context: Context, input: RenderInput, config: ViewConfig, density: Float): FieldFrame {
            if (!input.connected) return noRadarFrame(context, config, density, input.settings, header)
            return valueFrame(context, config, density, input.settings, header, format(input.passCount, input.rideTimeMs))
        }
    }

    override fun frame(context: Context, input: RenderInput, config: ViewConfig): FieldFrame =
        render(context, input, config, density)

    override fun key(context: Context, input: RenderInput, config: ViewConfig): Any =
        listOf(super.key(context, input, config), format(input.passCount, input.rideTimeMs))
}

/** Distance to the closest vehicle. */
class ClosestDistanceDataType(
    radarExtension: RadarCountExtension
) : FieldDataType(radarExtension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "closest-distance"
        private val header = Header(R.string.datatype_distance, R.drawable.ic_vehicle_distance) { it.distanceHeader }

        /** The field for [input]. Shared with the settings preview, which shows exactly these pixels. */
        fun render(context: Context, input: RenderInput, config: ViewConfig, density: Float): FieldFrame {
            if (!input.connected) return noRadarFrame(context, config, density, input.settings, header)
            return valueFrame(
                context, config, density, input.settings, header, derive(input).distance ?: "--",
                layoutSample = Units.formatDistance(888, input.useImperial)
            )
        }
    }

    override fun frame(context: Context, input: RenderInput, config: ViewConfig): FieldFrame =
        render(context, input, config, density)
}

/**
 * Combined field: count, approach speed and distance. What it shows depends
 * on whether a vehicle is on the radar and on the Radar field settings; see
 * [ComboLayout]. The active layout is held for [ACTIVE_HOLD_MS] after the
 * last target vanishes so a pass ends with the new count showing, and for
 * as long as a passed car's speed is held, if set to (see [PassHold]).
 */
class ComboDataType(
    radarExtension: RadarCountExtension
) : FieldDataType(radarExtension, TYPE_ID) {

    override fun passHold(settings: Settings): PassHoldSetting = settings.comboPassHold

    companion object {
        const val TYPE_ID = "radar-combo"
        private const val ACTIVE_HOLD_MS = 2_000L

        /** The pass whose speed shows at [nowMs] in place of a live one, or null. */
        internal fun held(input: RenderInput, nowMs: Long): TargetTracker.Pass? {
            val d = derive(input, input.settings.comboSpeedMode)
            return PassHold.held(input.lastPass, input.settings.comboPassHold, liveKnown = d.tracked && d.shownSpeed != null, nowMs)
        }

        /**
         * The field for [input], with the vehicle layout if [active] and
         * [held]'s speed, dimmed and captioned PASSED, while no live speed is
         * known. Shared with the settings preview, which shows exactly these
         * pixels.
         */
        fun render(
            context: Context, input: RenderInput, config: ViewConfig, density: Float, active: Boolean,
            held: TargetTracker.Pass? = null
        ): FieldFrame {
            if (!input.connected) return noRadarFrame(context, config, density, input.settings)
            val d = derive(input, input.settings.comboSpeedMode)
            val live = if (d.tracked) d.shownSpeed else null
            val passed = live == null && held != null
            val plan = ComboLayout.plan(
                active = active,
                count = input.passCount,
                speed = live ?: held?.let { PassHold.shownSpeed(it, input.useImperial, d.showsAbsolute) },
                // Always the live distance, never held: "--" once the radar is clear.
                distance = (input.state as? WidgetState.Threat)?.nearestDistanceM?.takeIf { it > 0 }
                    ?.let { Units.distanceValue(it, input.useImperial) },
                showsAbsolute = d.showsAbsolute,
                settings = input.settings,
                labels = comboLabels(context, input.useImperial),
                passed = passed
            )
            val s = input.settings
            val image = FieldBitmaps.combo(context, plan, config, density, FieldColors.palette(context, s.theme), header = s.comboHeader)
            // Developer aid: tint the whole view so where the host really puts it shows.
            return FieldViews.tile(context, image, tint = if (s.debugFieldBounds) FieldColors.DEBUG_TINT else null)
        }
    }

    /** Wall-clock time a target was last on the radar; the layout stays active a moment after. */
    @Volatile private var lastTrackedMs = 0L

    /**
     * Whether the vehicle layout shows at [now]: a target is on the radar,
     * or was a moment ago, or a passed car's speed is held.
     */
    private fun active(input: RenderInput, config: ViewConfig, now: Long, held: TargetTracker.Pass?): Boolean {
        val tracked = input.state is WidgetState.Threat
        if (tracked) lastTrackedMs = now
        return tracked || (!config.preview && now - lastTrackedMs < ACTIVE_HOLD_MS) || held != null
    }

    override fun frame(context: Context, input: RenderInput, config: ViewConfig): FieldFrame {
        val now = System.currentTimeMillis()
        val held = held(input, now)
        return render(context, input, config, density, active(input, config, now, held), held)
    }

    override fun key(context: Context, input: RenderInput, config: ViewConfig): Any {
        val now = System.currentTimeMillis()
        val held = held(input, now)
        return listOf(super.key(context, input, config), active(input, config, now, held), held?.seq)
    }
}

/**
 * The frame field [typeId] sends the Karoo for [input] at [nowMs], drawn by
 * that field's own code: what the settings previews show. The Radar field
 * shows its vehicle layout while a vehicle is on the radar or a passed
 * car's speed is held. Null for a type id that is not one of the fields.
 */
fun renderField(typeId: String, context: Context, input: FieldDataType.RenderInput, config: ViewConfig, density: Float, nowMs: Long): FieldFrame? =
    when (typeId) {
        ComboDataType.TYPE_ID -> {
            val held = ComboDataType.held(input, nowMs)
            ComboDataType.render(context, input, config, density, active = input.state is WidgetState.Threat || held != null, held = held)
        }
        VehicleCountDataType.TYPE_ID -> VehicleCountDataType.render(context, input, config, density)
        ApproachSpeedDataType.TYPE_ID -> ApproachSpeedDataType.render(context, input, config, density, nowMs)
        ClosestDistanceDataType.TYPE_ID -> ClosestDistanceDataType.render(context, input, config, density)
        VehiclesPerHourDataType.TYPE_ID -> VehiclesPerHourDataType.render(context, input, config, density)
        else -> null
    }

internal fun comboLabels(context: Context, useImperial: Boolean) = ComboLayout.Labels(
    count = context.getString(R.string.combo_count),
    vehicles = context.getString(R.string.combo_vehicles),
    speedRel = context.getString(R.string.combo_speed_rel),
    speedAbs = context.getString(R.string.combo_speed_abs),
    dist = context.getString(R.string.combo_dist),
    speedUnit = Units.speedUnitCaption(useImperial),
    distUnit = Units.distanceUnitCaption(useImperial),
    passed = context.getString(R.string.combo_speed_passed)
)

package io.github.aryeh95.radarcount.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.appwidget.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.datatypes.ComboLayout
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.ThemeSetting
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.github.aryeh95.radarcount.engine.Units
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.roundToInt

/**
 * Value text laid out exactly like ki2's TextView, which renders custom
 * fields indistinguishably from the Karoo's built-in ones: the whole cell,
 * vertically centred, horizontally aligned per the field setting, at 97%
 * of the Karoo's numeric size, monospace, nudged up slightly.
 */
@Composable
private fun KarooValue(
    text: String,
    config: ViewConfig,
    theme: ThemeSetting,
    color: ColorProvider? = null,
    density: Float,
    fontSize: Int = config.textSize,
    tag: String? = null
) {
    val fitted = minOf(fontSize, fitFontSp(text.length.toFloat(), config.viewSize.first, 10, density))
    val horizontal = when (config.alignment) {
        ViewConfig.Alignment.LEFT -> Alignment.Horizontal.Start
        ViewConfig.Alignment.CENTER -> Alignment.Horizontal.CenterHorizontally
        ViewConfig.Alignment.RIGHT -> Alignment.Horizontal.End
    }
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(start = 5.dp, top = 0.dp, end = 5.dp, bottom = 0.dp),
        contentAlignment = Alignment(vertical = Alignment.Vertical.CenterVertically, horizontal = horizontal)
    ) {
        if (tag == null) {
            KarooText(text, theme, color, fitted)
        } else {
            Column(horizontalAlignment = horizontal) {
                KarooText(text, theme, color, fitted)
                Text(
                    text = tag,
                    style = TextStyle(color = GlanceColors.label(theme), fontSize = (fitted * CAPTION_RATIO).toInt().coerceIn(9, 16).sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun KarooText(text: String, theme: ThemeSetting, color: ColorProvider?, fontSize: Int, nudge: Float = -0.09f) {
    Text(
        text = text,
        style = TextStyle(
            color = color ?: GlanceColors.text(theme),
            fontSize = (fontSize * 0.97).sp,
            fontFamily = FontFamily.Monospace
        ),
        // The text box carries descender space below the digits, so a box
        // centred in its area shows the glyphs high. [nudge] (fraction of
        // the font size) shifts the box to compensate.
        modifier = GlanceModifier
            .background(Color(1f, 1f, 1f, 1f), Color(0f, 0f, 0f, 1f))
            .padding(top = (fontSize * nudge).dp),
        maxLines = 1
    )
}

private data class Derived(
    val tracked: Boolean,
    /** Null while the closing speed is not yet known (first second of a track). */
    val relative: Int?,
    val absolute: Int?,
    val unit: String,
    val distance: String?,
    val behind: Int,
    val shownSpeed: Int?,
    val showsAbsolute: Boolean
)

private fun derive(input: GlanceDataType.RenderInput): Derived {
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
    val showsAbsolute = input.settings.speed == SpeedSetting.ABSOLUTE
    return Derived(
        tracked, relative, absolute, Units.speedUnitLabel(input.useImperial), distance, threat?.vehicleCount ?: 0,
        shownSpeed = if (showsAbsolute) absolute else relative,
        showsAbsolute = showsAbsolute
    )
}

private const val CAPTION_RATIO = 0.4f

/** Largest monospace font (sp) whose [chars] characters fit in [widthPx] minus [paddingDp]. */
private fun fitFontSp(chars: Float, widthPx: Int, paddingDp: Int, density: Float, emPerChar: Float = 0.62f): Int {
    if (widthPx <= 0 || chars <= 0f) return Int.MAX_VALUE
    val availablePx = widthPx - paddingDp * density
    return (availablePx / (emPerChar * chars * density)).toInt()
}


/** Vehicles that have passed the rider this ride. */
class VehicleCountGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "vehicle-count") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        KarooValue(
            text = if (input.connected) input.passCount.toString() else noRadarText(),
            config = config,
            theme = input.settings.theme,
            density = density,
            color = if (input.connected) null else ColorProvider(GlanceColors.Neutral)
        )
    }
}

/** Speed of the nearest vehicle, relative or absolute per settings. */
class ApproachSpeedGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "approach-speed") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val d = derive(input)
        KarooValue(
            text = when {
                !input.connected -> noRadarText()
                d.tracked && d.shownSpeed != null -> "${d.shownSpeed}${d.unit}"
                else -> "--"
            },
            config = config,
            theme = input.settings.theme,
            density = density,
            color = if (input.connected) null else ColorProvider(GlanceColors.Neutral),
            tag = if (input.connected) radarExtension.getString(if (d.showsAbsolute) R.string.speed_tag_abs else R.string.speed_tag_rel) else null
        )
    }
}

/** Vehicles passed per hour of recording time. */
class VehiclesPerHourGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "vehicles-per-hour") {

    companion object {
        /** Below this much recording time the rate swings wildly, so show nothing. */
        private const val MIN_RIDE_MS = 120_000L

        internal fun format(passCount: Int, rideTimeMs: Long): String {
            if (rideTimeMs < MIN_RIDE_MS) return "--"
            val perHour = passCount * 3_600_000.0 / rideTimeMs
            return if (perHour < 10.0) String.format(java.util.Locale.US, "%.1f", perHour) else perHour.roundToInt().toString()
        }
    }

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        KarooValue(
            text = if (input.connected) format(input.passCount, input.rideTimeMs) else noRadarText(),
            config = config,
            theme = input.settings.theme,
            density = density,
            color = if (input.connected) null else ColorProvider(GlanceColors.Neutral)
        )
    }
}

/** Distance to the closest vehicle. */
class ClosestDistanceGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "closest-distance") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val d = derive(input)
        KarooValue(
            text = if (!input.connected) noRadarText() else d.distance ?: "--",
            config = config,
            theme = input.settings.theme,
            density = density,
            color = if (input.connected) null else ColorProvider(GlanceColors.Neutral)
        )
    }
}

/**
 * Combined field: count, approach speed and distance. What it shows depends
 * on whether a vehicle is on the radar and on the Radar field settings; see
 * [ComboLayout]. The active layout is held for [ACTIVE_HOLD_MS] after the
 * last target vanishes so a pass ends with the new count showing.
 */
class ComboGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "radar-combo") {

    companion object {
        private const val ACTIVE_HOLD_MS = 2_000L
    }

    /** Wall-clock time a target was last on the radar; the layout stays active a moment after. */
    @Volatile private var lastTrackedMs = 0L

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val theme = input.settings.theme
        if (!input.connected) {
            KarooValue(noRadarText(), config, theme, ColorProvider(GlanceColors.Neutral), density)
            return
        }
        val d = derive(input)
        val now = System.currentTimeMillis()
        if (d.tracked) lastTrackedMs = now
        val active = d.tracked || (!config.preview && now - lastTrackedMs < ACTIVE_HOLD_MS)
        val plan = ComboLayout.plan(
            active = active,
            count = input.passCount,
            speed = if (d.tracked) d.shownSpeed else null,
            distance = (input.state as? WidgetState.Threat)?.nearestDistanceM?.takeIf { it > 0 }
                ?.let { Units.distanceValue(it, input.useImperial) },
            showsAbsolute = d.showsAbsolute,
            settings = input.settings,
            labels = comboLabels(radarExtension, input.useImperial)
        )
        ComboCells(plan, config, theme, density, header = input.settings.comboHeader, debugBounds = input.settings.debugFieldBounds)
    }

    /** The Karoo's strip is off: the field draws its own header, or none. */
    override val karooHeader: Boolean get() = false
}

internal fun comboLabels(radarExtension: RadarCountExtension, useImperial: Boolean) = ComboLayout.Labels(
    count = radarExtension.getString(R.string.combo_count),
    vehicles = radarExtension.getString(R.string.combo_vehicles),
    speedRel = radarExtension.getString(R.string.combo_speed_rel),
    speedAbs = radarExtension.getString(R.string.combo_speed_abs),
    dist = radarExtension.getString(R.string.combo_dist),
    speedUnit = Units.speedUnitCaption(useImperial),
    distUnit = Units.distanceUnitCaption(useImperial)
)

/**
 * Lays out a [ComboLayout.Plan] on the whole tile: an optional header strip
 * in the Karoo's style, then the cells centred in the rest, sized by
 * [ComboLayout.sizes]. Heights are set explicitly so centring is real.
 */
@Composable
private fun ComboCells(plan: ComboLayout.Plan, config: ViewConfig, theme: ThemeSetting, density: Float, header: Boolean, debugBounds: Boolean = false) {
    val headerDp = if (header) ComboLayout.HEADER_DP else 0f
    val sz = ComboLayout.sizes(plan, config.viewSize.first, config.viewSize.second, config.textSize, density, wideGrid = config.gridSize.first >= 60, headerDp = headerDp)
    // The host's view can be taller than the reported tile when its own
    // strip is off, so nothing here uses a fixed height: the root fills
    // whatever the host gives and centres in the area below the header.
    // Developer aid: tint the whole view so where the host really puts it shows.
    val root = if (debugBounds) GlanceModifier.fillMaxSize().background(Color(0.2f, 0.4f, 1f, 0.35f), Color(0.2f, 0.4f, 1f, 0.35f)) else GlanceModifier.fillMaxSize()
    Box(modifier = root) {
        if (header) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(headerDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_radar),
                        contentDescription = null,
                        modifier = GlanceModifier.size(13.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(GlanceColors.Safe))
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "RADAR",
                        style = TextStyle(color = GlanceColors.text(theme), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(start = 2.dp, end = 2.dp, top = headerDp.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plan.badge != null) {
                    // Sits on the digit row, not the caption row: pad down by the caption height.
                    Column {
                        if (plan.cells.any { it.caption != null }) Spacer(modifier = GlanceModifier.height((sz.captionSp * 1.0f).dp))
                        KarooText(plan.badge, theme, null, sz.badgeSp, nudge = DIGIT_NUDGE)
                    }
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
                plan.cells.forEachIndexed { i, c ->
                    if (i > 0) Spacer(modifier = GlanceModifier.width(sz.gapDp.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (c.caption != null) Caption(c.caption, theme, sz.captionSp)
                        KarooText(c.value, theme, null, sz.valueSp, nudge = DIGIT_NUDGE)
                    }
                }
            }
        }
    }
}

/** In the combo field the view is ours and centred; the digits' box sits high by about this fraction of the font size. */
private const val DIGIT_NUDGE = 0.18f

@Composable
private fun Caption(text: String, theme: ThemeSetting, size: Int) {
    Text(
        text = text,
        style = TextStyle(color = GlanceColors.label(theme), fontSize = size.sp),
        maxLines = 1
    )
}

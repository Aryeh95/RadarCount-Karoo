package io.github.aryeh95.radarcount.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.ThemeSetting
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.github.aryeh95.radarcount.engine.Units
import io.hammerhead.karooext.models.ViewConfig

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
    fontSize: Int = config.textSize
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
        KarooText(text, theme, color, fitted)
    }
}

@Composable
private fun KarooText(text: String, theme: ThemeSetting, color: ColorProvider?, fontSize: Int) {
    Text(
        text = text,
        style = TextStyle(
            color = color ?: GlanceColors.text(theme),
            fontSize = (fontSize * 0.97).sp,
            fontFamily = FontFamily.Monospace
        ),
        modifier = GlanceModifier
            .background(Color(1f, 1f, 1f, 1f), Color(0f, 0f, 0f, 1f))
            .padding(top = (-fontSize * 0.09).dp),
        maxLines = 1
    )
}

private data class Derived(
    val tracked: Boolean,
    val relative: Int,
    val absolute: Int,
    val unit: String,
    val distance: String?,
    val behind: Int,
    val shownSpeed: Int,
    val showsAbsolute: Boolean
)

private fun derive(input: GlanceDataType.RenderInput): Derived {
    val threat = input.state as? WidgetState.Threat
    val tracked = threat != null
    val relative = RadarCountExtension.toUserSpeedUnits(input.closingSpeedMps, input.useImperial)
    val absolute = if (relative > 0) {
        relative + RadarCountExtension.toUserSpeedUnits(input.riderSpeedMps, input.useImperial)
    } else {
        0
    }
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
private fun fitFontSp(chars: Float, widthPx: Int, paddingDp: Int, density: Float): Int {
    if (widthPx <= 0 || chars <= 0f) return Int.MAX_VALUE
    val availablePx = widthPx - paddingDp * density
    return (availablePx / (0.62f * chars * density)).toInt()
}

/** Vehicles that have passed the rider this ride. */
class VehicleCountGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "vehicle-count") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        KarooValue(
            text = if (input.connected) input.passCount.toString() else "--",
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
            text = if (d.tracked) "${d.shownSpeed}${d.unit}" else "--",
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
            text = d.distance ?: "--",
            config = config,
            theme = input.settings.theme,
            density = density,
            color = if (input.connected) null else ColorProvider(GlanceColors.Neutral)
        )
    }
}

/**
 * Combined field: count, speed and distance side by side, each captioned,
 * in the same style as the single fields at a reduced size.
 */
class ComboGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "radar-combo") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val theme = input.settings.theme
        val d = derive(input)
        val live = if (input.connected) null else ColorProvider(GlanceColors.Neutral)
        val countText = if (input.connected) input.passCount.toString() else "--"
        val speedText = if (d.tracked) "${d.shownSpeed}${d.unit}" else "--"
        val distText = d.distance ?: "--"
        val gapDp = if (config.gridSize.first >= 60) 14 else 8
        // Shrink the value font so all three cells fit the field width (monospace ~0.6em per char).
        val captions = listOf(R.string.combo_count, R.string.combo_speed, R.string.combo_dist).map { radarExtension.getString(it) }
        val chars = listOf(countText, speedText, distText).zip(captions).sumOf { (v, c) ->
            maxOf(v.length.toFloat(), c.length * CAPTION_RATIO).toDouble()
        }.toFloat()
        val valueSize = minOf((config.textSize * 0.6f).toInt(), fitFontSp(chars, config.viewSize.first, 10 + 2 * gapDp, density)).coerceAtLeast(12)
        val captionSize = (valueSize * CAPTION_RATIO).toInt().coerceIn(9, 16)
        val gap = gapDp.dp

        Box(
            modifier = GlanceModifier.fillMaxSize().padding(start = 5.dp, end = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cell(radarExtension.getString(R.string.combo_count), countText, theme, null, valueSize, captionSize)
                Spacer(modifier = GlanceModifier.width(gap))
                Cell(radarExtension.getString(R.string.combo_speed), speedText, theme, live, valueSize, captionSize)
                Spacer(modifier = GlanceModifier.width(gap))
                Cell(radarExtension.getString(R.string.combo_dist), distText, theme, live, valueSize, captionSize)
            }
        }
    }

    @Composable
    private fun Cell(caption: String, value: String, theme: ThemeSetting, color: ColorProvider?, valueSize: Int, captionSize: Int) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = caption,
                style = TextStyle(color = GlanceColors.label(theme), fontSize = captionSize.sp),
                maxLines = 1
            )
            KarooText(value, theme, color, valueSize)
        }
    }
}

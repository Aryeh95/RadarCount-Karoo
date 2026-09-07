package io.github.aryeh95.radarcount.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.github.aryeh95.radarcount.engine.Units
import io.hammerhead.karooext.models.ViewConfig

/**
 * The Karoo draws the standard header (icon + field name) itself, so a
 * field only renders its value, bottom-aligned like the built-in fields.
 *
 * Sizes come from the cell's real pixel dimensions. Glance text takes
 * roughly 1.25x its font size in height.
 */
class Sizes(config: ViewConfig, density: Float) {
    val widthDp: Float = config.viewSize.first / density
    val heightDp: Float = config.viewSize.second / density

    /**
     * Space left under the Karoo's header, in dp. The header is about
     * 34 dp tall; the cell has a few dp of padding.
     */
    val availDp: Float = (heightDp - 40f).coerceAtLeast(24f)

    /** Small text for captions and second lines */
    val small: Int = (availDp * 0.22f).toInt().coerceIn(9, 16)

    /**
     * Value font: no more than the Karoo's own numeric size for the cell,
     * and no taller than the space left (Glance text needs ~1.3x its size).
     */
    val value: Int = minOf(config.textSize.toFloat(), availDp / 1.3f).toInt().coerceIn(14, 64)

    /** Room for a second line under the value? */
    val hasFooter: Boolean = availDp >= value * 1.3f + small * 1.3f + 4

    /** Font for a value when it must share the height with a caption */
    val valueWithCaption: Int = minOf(config.textSize.toFloat(), (availDp - small * 1.3f - 2) / 1.3f).toInt().coerceIn(12, 64)

    val narrow: Boolean = widthDp < 200
    val wide: Boolean = widthDp >= 300
}

private data class Derived(
    val tracked: Boolean,
    val relative: Int,
    val absolute: Int,
    val unit: String,
    val distance: String?,
    val behind: Int
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
    return Derived(tracked, relative, absolute, Units.speedUnitLabel(input.useImperial), distance, threat?.vehicleCount ?: 0)
}

/** Standard field body: big value, optional small line beneath. */
@Composable
private fun ValueBody(
    input: GlanceDataType.RenderInput,
    sz: Sizes,
    value: String,
    valueColor: ColorProvider,
    footer: String?
) {
    val labelColor = GlanceColors.label(input.settings.theme)
    DataFieldContainer {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ValueText(text = value, color = valueColor, fontSize = sz.value)
                if (footer != null && sz.hasFooter) {
                    LabelText(text = footer, color = labelColor, fontSize = sz.small)
                }
            }
        }
    }
}

/**
 * Vehicles that have passed the rider this ride, with the lap count below.
 */
class VehicleCountGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "vehicle-count") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val theme = input.settings.theme
        ValueBody(
            input = input,
            sz = Sizes(config, density),
            value = if (input.connected) input.passCount.toString() else "--",
            valueColor = if (input.connected) GlanceColors.text(theme) else ColorProvider(GlanceColors.Neutral),
            footer = if (input.settings.showLapCount) {
                radarExtension.getString(R.string.widget_lap_label, input.lapPassCount)
            } else {
                null
            }
        )
    }
}

/**
 * Approach (relative) speed of the nearest vehicle, with its absolute
 * speed (relative + rider speed) below. Both in the rider's units.
 */
class ApproachSpeedGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "approach-speed") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val d = derive(input)
        ValueBody(
            input = input,
            sz = Sizes(config, density),
            value = if (d.tracked) d.relative.toString() else "--",
            valueColor = if (d.tracked) ColorProvider(GlanceColors.forState(input.state)) else ColorProvider(GlanceColors.Neutral),
            footer = if (d.tracked) {
                radarExtension.getString(R.string.widget_absolute_label, d.absolute, d.unit)
            } else {
                d.unit
            }
        )
    }
}

/**
 * Distance to the closest vehicle, with the number of vehicles behind below.
 */
class ClosestDistanceGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "closest-distance") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val d = derive(input)
        ValueBody(
            input = input,
            sz = Sizes(config, density),
            value = d.distance ?: "--",
            valueColor = if (d.tracked) ColorProvider(GlanceColors.forState(input.state)) else ColorProvider(GlanceColors.Neutral),
            footer = when {
                d.tracked -> radarExtension.resources.getQuantityString(R.plurals.widget_vehicles_behind, d.behind, d.behind)
                input.state is WidgetState.Clear -> radarExtension.getString(R.string.widget_road_clear)
                else -> radarExtension.getString(R.string.widget_no_radar)
            }
        )
    }
}

/**
 * Combined field under the standard "Radar Combo" header: pass count,
 * approach speed and closest distance side by side, each with a small
 * caption. In a tall wide cell the count is larger and a status line is
 * added; in a very short cell the three values share one line.
 */
class ComboGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "radar-combo") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val sz = Sizes(config, density)
        val theme = input.settings.theme
        val d = derive(input)
        val text = GlanceColors.text(theme)
        val label = GlanceColors.label(theme)
        val liveColor = if (d.tracked) ColorProvider(GlanceColors.forState(input.state)) else ColorProvider(GlanceColors.Neutral)

        val countText = if (input.connected) input.passCount.toString() else "--"
        val speedText = if (d.tracked) "${d.relative}" else "--"
        val distText = d.distance ?: "--"

        val countCap = radarExtension.getString(R.string.combo_count)
        val speedCap = radarExtension.getString(R.string.combo_speed)
        val distCap = radarExtension.getString(R.string.combo_dist)

        // Three values across share the width; shrink from the Karoo size
        // so "148ft" style values fit in a half-width cell.
        // Captions always: the value shrinks to whatever height is left
        val captions = true
        val vBase = sz.valueWithCaption
        // Three values share the width: shrink in narrow cells so "148ft" fits
        val v = (if (sz.narrow) vBase * 0.75f else vBase.toFloat()).toInt().coerceAtLeast(12)
        val full = sz.wide && sz.availDp >= 130

        DataFieldContainer {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (full) {
                    Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Cell(countCap, countText, text, label, sz.value, sz.small)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Cell(radarExtension.getString(R.string.widget_approach_label, d.unit), speedText, liveColor, label, v, sz.small)
                            Spacer(modifier = GlanceModifier.width(20.dp))
                            Cell(radarExtension.getString(R.string.widget_distance_label), distText, liveColor, label, v, sz.small)
                        }
                        val status = when {
                            d.tracked -> radarExtension.getString(R.string.widget_absolute_label, d.absolute, d.unit) +
                                "  ·  " + radarExtension.resources.getQuantityString(R.plurals.widget_vehicles_behind, d.behind, d.behind)
                            input.state is WidgetState.Clear -> radarExtension.getString(R.string.widget_road_clear)
                            else -> radarExtension.getString(R.string.widget_no_radar)
                        }
                        val lap = if (input.settings.showLapCount) {
                            radarExtension.getString(R.string.widget_lap_label, input.lapPassCount) + "  ·  "
                        } else {
                            ""
                        }
                        LabelText(text = lap + status, color = label, fontSize = sz.small)
                    }
                } else {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Cell(if (captions) countCap else null, countText, text, label, v, sz.small)
                        Spacer(modifier = GlanceModifier.width(if (sz.narrow) 8.dp else 14.dp))
                        Cell(if (captions) speedCap else null, speedText, liveColor, label, v, sz.small)
                        Spacer(modifier = GlanceModifier.width(if (sz.narrow) 8.dp else 14.dp))
                        Cell(if (captions) distCap else null, distText, liveColor, label, v, sz.small)
                    }
                }
            }
        }
    }

    @Composable
    private fun Cell(
        caption: String?,
        value: String,
        valueColor: ColorProvider,
        captionColor: ColorProvider,
        valueSize: Int,
        captionSize: Int
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (caption != null) {
                LabelText(text = caption, color = captionColor, fontSize = captionSize)
            }
            ValueText(text = value, color = valueColor, fontSize = valueSize)
        }
    }
}

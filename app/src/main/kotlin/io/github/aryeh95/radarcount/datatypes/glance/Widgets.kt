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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.RadarCountExtension
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.github.aryeh95.radarcount.engine.Units
import io.hammerhead.karooext.models.ViewConfig

/**
 * Sizing from the cell's real pixel dimensions, so text fits whatever
 * grid size the rider picked. Glance text takes roughly 1.25x its font
 * size in height.
 */
class Sizes(config: ViewConfig, density: Float) {
    val widthDp: Float = config.viewSize.first / density
    val heightDp: Float = config.viewSize.second / density

    /** Never larger than the Karoo's own numeric size for this cell */
    private val maxValue = config.textSize.coerceIn(16, 72)

    /** Status bar (5) + container padding (4) + inner padding (4) */
    private val chrome = 13f

    val label: Int = (heightDp * 0.13f).toInt().coerceIn(10, 16)

    /** Largest value font that fits alongside [labelLines] lines of label text. */
    fun valueFor(labelLines: Int): Int {
        val avail = heightDp - chrome - labelLines * label * 1.25f
        return (avail / 1.3f).toInt().coerceIn(0, maxValue)
    }

    /** Value font for a single row containing label and value side by side. */
    val inline: Int = ((heightDp - chrome) / 1.3f).toInt().coerceIn(14, maxValue)

    val narrow: Boolean = widthDp < 200
    val wide: Boolean = widthDp >= 300

    companion object {
        /** Minimum value font worth stacking a label above. */
        const val MIN_STACKED = 18
    }
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

/**
 * Shared single-value layout. Picks, in order of available height:
 * label / value / footer, label / value, or label and value on one row.
 */
@Composable
private fun SingleValue(
    input: GlanceDataType.RenderInput,
    sz: Sizes,
    label: String,
    shortLabel: String,
    value: String,
    valueColor: ColorProvider,
    footer: String?
) {
    val theme = input.settings.theme
    val labelColor = GlanceColors.label(theme)
    val withFooter = footer != null && sz.valueFor(2) >= Sizes.MIN_STACKED
    val stacked = withFooter || sz.valueFor(1) >= Sizes.MIN_STACKED
    val lbl = if (sz.narrow) shortLabel else label

    DataFieldContainer {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            StatusBar(input.state)
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    withFooter -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LabelText(text = lbl, color = labelColor, fontSize = sz.label)
                        ValueText(text = value, color = valueColor, fontSize = sz.valueFor(2))
                        LabelText(text = footer!!, color = labelColor, fontSize = sz.label)
                    }
                    stacked -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LabelText(text = lbl, color = labelColor, fontSize = sz.label)
                        ValueText(text = value, color = valueColor, fontSize = sz.valueFor(1))
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LabelText(text = shortLabel, color = labelColor, fontSize = sz.label)
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        ValueText(text = value, color = valueColor, fontSize = sz.inline)
                    }
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
        SingleValue(
            input = input,
            sz = Sizes(config, density),
            label = radarExtension.getString(R.string.widget_count_label),
            shortLabel = radarExtension.getString(R.string.widget_count_label),
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
        SingleValue(
            input = input,
            sz = Sizes(config, density),
            label = radarExtension.getString(R.string.widget_approach_label, d.unit),
            shortLabel = radarExtension.getString(R.string.widget_approach_short, d.unit),
            value = if (d.tracked) d.relative.toString() else "--",
            valueColor = if (d.tracked) ColorProvider(GlanceColors.forState(input.state)) else ColorProvider(GlanceColors.Neutral),
            footer = if (d.tracked) {
                radarExtension.getString(R.string.widget_absolute_label, d.absolute, d.unit)
            } else {
                radarExtension.getString(R.string.widget_no_vehicle)
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
        SingleValue(
            input = input,
            sz = Sizes(config, density),
            label = radarExtension.getString(R.string.widget_distance_label),
            shortLabel = radarExtension.getString(R.string.combo_dist),
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
 * Combined field: pass count, approach speed and closest distance in one
 * cell. Layout picked from the cell's real size:
 *  - tall and wide: big count, then speed and distance, then a status line
 *  - room for a label row: three labelled values side by side
 *    (two if the cell is narrow, distance moves to a footer if it fits)
 *  - otherwise: one line "12 · 18 mph · 148ft"
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
        val stateColor = ColorProvider(GlanceColors.forState(input.state))
        val neutral = ColorProvider(GlanceColors.Neutral)
        val liveColor = if (d.tracked) stateColor else neutral

        val countText = if (input.connected) input.passCount.toString() else "--"
        val speedText = if (d.tracked) "${d.relative}" else "--"
        val distText = d.distance ?: "--"
        val unit = d.unit

        val countLabel = radarExtension.getString(R.string.widget_count_label)
        val speedLabel = radarExtension.getString(R.string.widget_approach_short, unit)
        val distLabel = radarExtension.getString(R.string.combo_dist)

        val statusLine = when {
            d.tracked -> radarExtension.getString(R.string.widget_absolute_label, d.absolute, unit) +
                "  ·  " + radarExtension.resources.getQuantityString(R.plurals.widget_vehicles_behind, d.behind, d.behind)
            input.state is WidgetState.Clear -> radarExtension.getString(R.string.widget_road_clear)
            else -> radarExtension.getString(R.string.widget_no_radar)
        }
        val lapPrefix = if (input.settings.showLapCount) {
            radarExtension.getString(R.string.widget_lap_label, input.lapPassCount) + "  ·  "
        } else {
            ""
        }

        val full = sz.wide && sz.valueFor(3) >= Sizes.MIN_STACKED + 6
        val labelled = sz.valueFor(1) >= Sizes.MIN_STACKED

        DataFieldContainer {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusBar(input.state)
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        full -> Column(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Big count; speed and distance share the second row
                            val big = sz.valueFor(3)
                            val med = (big * 0.6f).toInt().coerceAtLeast(Sizes.MIN_STACKED)
                            Cell(countLabel, countText, text, label, big, sz.label)
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Cell(radarExtension.getString(R.string.widget_approach_label, unit), speedText, liveColor, label, med, sz.label)
                                Spacer(modifier = GlanceModifier.width(20.dp))
                                Cell(radarExtension.getString(R.string.widget_distance_label), distText, liveColor, label, med, sz.label)
                            }
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            LabelText(text = lapPrefix + statusLine, color = label, fontSize = sz.label)
                        }
                        labelled && !sz.narrow -> Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val v = sz.valueFor(1)
                            Cell(countLabel, countText, text, label, v, sz.label)
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            Cell(speedLabel, speedText, liveColor, label, v, sz.label)
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            Cell(distLabel, distText, liveColor, label, v, sz.label)
                        }
                        labelled -> {
                            // Narrow: count and speed side by side; distance below if it fits
                            val withFooter = sz.valueFor(2) >= Sizes.MIN_STACKED
                            val v = if (withFooter) sz.valueFor(2) else sz.valueFor(1)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Cell(countLabel, countText, text, label, v, sz.label)
                                    Spacer(modifier = GlanceModifier.width(10.dp))
                                    Cell(speedLabel, speedText, liveColor, label, v, sz.label)
                                }
                                if (withFooter) {
                                    LabelText(text = "$distLabel $distText", color = label, fontSize = sz.label)
                                }
                            }
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            val v = sz.inline
                            ValueText(text = countText, color = text, fontSize = v)
                            LabelText(text = " · ", color = label, fontSize = sz.label)
                            ValueText(text = "$speedText $unit", color = liveColor, fontSize = v)
                            LabelText(text = " · ", color = label, fontSize = sz.label)
                            ValueText(text = distText, color = liveColor, fontSize = v)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Cell(
        label: String,
        value: String,
        valueColor: ColorProvider,
        labelColor: ColorProvider,
        valueSize: Int,
        labelSize: Int
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LabelText(text = label, color = labelColor, fontSize = labelSize)
            ValueText(text = value, color = valueColor, fontSize = valueSize)
        }
    }
}

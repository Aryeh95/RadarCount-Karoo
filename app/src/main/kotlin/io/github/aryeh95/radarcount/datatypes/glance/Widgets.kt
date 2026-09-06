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
 * Font sizes derived from the Karoo's own numeric size for this grid
 * cell, so the field scales like the built-in ones.
 */
private class Sizes(config: ViewConfig) {
    /** Karoo's standard numeric font size for this cell, in sp */
    private val base = config.textSize.coerceIn(18, 72)
    val value = base
    val medium = (base * 0.55).toInt().coerceAtLeast(16)
    val label = (base * 0.32).toInt().coerceIn(11, 20)
    val rows = config.gridSize.second     // of 60
    val cols = config.gridSize.first      // of 60
    val tall = rows >= 30
    val wide = cols >= 60
    val tiny = rows <= 15 && cols < 60
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

/** Shared layout: status bar, small label, big value, small footer. */
@Composable
private fun LabelValueFooter(
    input: GlanceDataType.RenderInput,
    config: ViewConfig,
    label: String,
    value: String,
    valueColor: ColorProvider,
    footer: String?
) {
    val sz = Sizes(config)
    val theme = input.settings.theme
    DataFieldContainer {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            StatusBar(input.state)
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (sz.tiny) {
                    // One line: label and value side by side
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabelText(text = label, color = GlanceColors.label(theme), fontSize = sz.label)
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        ValueText(text = value, color = valueColor, fontSize = sz.medium)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LabelText(text = label, color = GlanceColors.label(theme), fontSize = sz.label)
                        ValueText(text = value, color = valueColor, fontSize = sz.value)
                        if (footer != null) {
                            LabelText(text = footer, color = GlanceColors.label(theme), fontSize = sz.label)
                        }
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
        LabelValueFooter(
            input = input,
            config = config,
            label = radarExtension.getString(R.string.widget_count_label),
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
        LabelValueFooter(
            input = input,
            config = config,
            label = radarExtension.getString(R.string.widget_approach_label, d.unit),
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
        LabelValueFooter(
            input = input,
            config = config,
            label = radarExtension.getString(R.string.widget_distance_label),
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
 * cell. Layout adapts to the cell's grid size:
 *  - quarter-height or narrower: one row of three values
 *  - half height: big count on the left, speed and distance stacked right
 *  - full height: big count, then speed and distance, then lap and status
 */
class ComboGlanceDataType(
    radarExtension: RadarCountExtension
) : GlanceDataType(radarExtension, "radar-combo") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val sz = Sizes(config)
        val theme = input.settings.theme
        val d = derive(input)
        val text = GlanceColors.text(theme)
        val label = GlanceColors.label(theme)
        val stateColor = ColorProvider(GlanceColors.forState(input.state))
        val neutral = ColorProvider(GlanceColors.Neutral)

        val countText = if (input.connected) input.passCount.toString() else "--"
        val speedText = if (d.tracked) "${d.relative}" else "--"
        val distText = d.distance ?: "--"
        val unit = d.unit

        DataFieldContainer {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusBar(input.state)
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        sz.tiny || (!sz.tall && !sz.wide) -> {
                            // Single row: count | speed | distance
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Cell(radarExtension.getString(R.string.combo_count), countText, text, label, sz.medium, sz.label)
                                Spacer(modifier = GlanceModifier.width(10.dp))
                                Cell(unit, speedText, if (d.tracked) stateColor else neutral, label, sz.medium, sz.label)
                                Spacer(modifier = GlanceModifier.width(10.dp))
                                Cell(radarExtension.getString(R.string.combo_dist), distText, if (d.tracked) stateColor else neutral, label, sz.medium, sz.label)
                            }
                        }
                        !sz.tall -> {
                            // Half height: count left, speed + distance stacked right
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Cell(radarExtension.getString(R.string.widget_count_label), countText, text, label, sz.value, sz.label)
                                Spacer(modifier = GlanceModifier.width(14.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Cell(radarExtension.getString(R.string.widget_approach_label, unit), speedText, if (d.tracked) stateColor else neutral, label, sz.medium, sz.label)
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                    Cell(radarExtension.getString(R.string.widget_distance_label), distText, if (d.tracked) stateColor else neutral, label, sz.medium, sz.label)
                                }
                            }
                        }
                        else -> {
                            // Full height: everything
                            Column(
                                modifier = GlanceModifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Cell(radarExtension.getString(R.string.widget_count_label), countText, text, label, sz.value, sz.label)
                                Spacer(modifier = GlanceModifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Cell(radarExtension.getString(R.string.widget_approach_label, unit), speedText, if (d.tracked) stateColor else neutral, label, sz.medium, sz.label)
                                    Spacer(modifier = GlanceModifier.width(16.dp))
                                    Cell(radarExtension.getString(R.string.widget_distance_label), distText, if (d.tracked) stateColor else neutral, label, sz.medium, sz.label)
                                }
                                Spacer(modifier = GlanceModifier.height(4.dp))
                                val footer = when {
                                    d.tracked -> radarExtension.getString(R.string.widget_absolute_label, d.absolute, unit) +
                                        "  ·  " + radarExtension.resources.getQuantityString(R.plurals.widget_vehicles_behind, d.behind, d.behind)
                                    input.state is WidgetState.Clear -> radarExtension.getString(R.string.widget_road_clear)
                                    else -> radarExtension.getString(R.string.widget_no_radar)
                                }
                                val lapText = if (input.settings.showLapCount) {
                                    radarExtension.getString(R.string.widget_lap_label, input.lapPassCount) + "  ·  "
                                } else {
                                    ""
                                }
                                LabelText(text = lapText + footer, color = label, fontSize = sz.label)
                            }
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

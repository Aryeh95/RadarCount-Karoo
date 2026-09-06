package io.github.aryeh95.mybiketraffic.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import io.github.aryeh95.mybiketraffic.R
import io.github.aryeh95.mybiketraffic.MyBikeTrafficExtension
import io.github.aryeh95.mybiketraffic.data.models.WidgetState
import io.github.aryeh95.mybiketraffic.engine.Units
import io.hammerhead.karooext.models.ViewConfig

/** Shared layout: status bar, small label, big value, small footer. */
@Composable
private fun LabelValueFooter(
    input: GlanceDataType.RenderInput,
    label: String,
    value: String,
    valueColor: Color,
    footer: String
) {
    DataFieldContainer {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            StatusBar(input.state)
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LabelText(text = label, fontSize = 12)
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    ValueText(text = value, color = valueColor, fontSize = 36)
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    LabelText(text = footer, fontSize = 12)
                }
            }
        }
    }
}

/**
 * Vehicles that have passed the rider this ride, with the lap count below.
 */
class VehicleCountGlanceDataType(
    radarExtension: MyBikeTrafficExtension
) : GlanceDataType(radarExtension, "vehicle-count") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        LabelValueFooter(
            input = input,
            label = radarExtension.getString(R.string.widget_count_label),
            value = if (input.connected) input.passCount.toString() else "--",
            valueColor = if (input.connected) GlanceColors.White else GlanceColors.Neutral,
            footer = radarExtension.getString(R.string.widget_lap_label, input.lapPassCount)
        )
    }
}

/**
 * Approach (relative) speed of the nearest vehicle, with its absolute
 * speed (relative + rider speed) below. Both in the rider's units.
 */
class ApproachSpeedGlanceDataType(
    radarExtension: MyBikeTrafficExtension
) : GlanceDataType(radarExtension, "approach-speed") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val tracked = input.state is WidgetState.Threat
        val relative = MyBikeTrafficExtension.toUserSpeedUnits(input.closingSpeedMps, input.useImperial)
        val absolute = if (relative > 0) {
            relative + MyBikeTrafficExtension.toUserSpeedUnits(input.riderSpeedMps, input.useImperial)
        } else {
            0
        }
        val unit = Units.speedUnitLabel(input.useImperial)
        LabelValueFooter(
            input = input,
            label = radarExtension.getString(R.string.widget_approach_label, unit),
            value = if (tracked) relative.toString() else "--",
            valueColor = if (tracked) GlanceColors.forState(input.state) else GlanceColors.Neutral,
            footer = if (tracked) {
                radarExtension.getString(R.string.widget_absolute_label, absolute, unit)
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
    radarExtension: MyBikeTrafficExtension
) : GlanceDataType(radarExtension, "closest-distance") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val state = input.state
        val threat = state as? WidgetState.Threat
        val hasRange = threat != null && threat.nearestDistanceM > 0
        LabelValueFooter(
            input = input,
            label = radarExtension.getString(R.string.widget_distance_label),
            value = if (hasRange) Units.formatDistance(threat!!.nearestDistanceM, input.useImperial) else "--",
            valueColor = if (threat != null) GlanceColors.forState(state) else GlanceColors.Neutral,
            footer = when {
                threat != null -> radarExtension.resources.getQuantityString(
                    R.plurals.widget_vehicles_behind, threat.vehicleCount, threat.vehicleCount
                )
                state is WidgetState.Clear -> radarExtension.getString(R.string.widget_road_clear)
                else -> radarExtension.getString(R.string.widget_no_radar)
            }
        )
    }
}

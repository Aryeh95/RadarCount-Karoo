package io.github.ykn.variaradarpro.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.ViewConfig

/**
 * Medium widget (2x1) - Clean, readable design.
 * Shows large vehicle count or status + distance to nearest vehicle.
 */
class MediumWidgetGlanceDataType(
    radarExtension: VariaRadarExtension
) : GlanceDataType(radarExtension, "radar-medium") {

    @Composable
    override fun Content(state: WidgetState, settings: PresetSettings, config: ViewConfig) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(WidgetColors.forState(state))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getMainText(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Text(
                    text = getSecondaryText(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.9f)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    private fun getMainText(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> "-"
        is WidgetState.Connecting -> "..."
        is WidgetState.Clear -> "OK"
        is WidgetState.Threat -> state.vehicleCount.toString()
        is WidgetState.ConnectionLost -> "!"
    }

    private fun getSecondaryText(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> radarExtension.getString(R.string.widget_no_radar)
        is WidgetState.Connecting -> radarExtension.getString(R.string.widget_connecting)
        is WidgetState.Clear -> radarExtension.getString(R.string.widget_all_clear)
        is WidgetState.Threat -> if (state.nearestDistanceM > 0) formatDistance(state.nearestDistanceM) else radarExtension.getString(R.string.widget_behind)
        is WidgetState.ConnectionLost -> radarExtension.getString(R.string.widget_lost)
    }
}

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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.ViewConfig

/**
 * Large widget (2x2) - Full information display.
 * Status label at top, large vehicle count + distance, status message at bottom.
 */
class LargeWidgetGlanceDataType(
    radarExtension: VariaRadarExtension
) : GlanceDataType(radarExtension, "radar-large") {

    @Composable
    override fun Content(state: WidgetState, settings: PresetSettings, config: ViewConfig) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(WidgetColors.forState(state))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getStatusLabel(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getMainText(state),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    if (state is WidgetState.Threat && state.nearestDistanceM > 0) {
                        Spacer(modifier = GlanceModifier.width(16.dp))
                        Text(
                            text = formatDistance(state.nearestDistanceM),
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                Text(
                    text = getStatusMessage(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.9f)),
                        fontSize = 16.sp
                    )
                )
            }
        }
    }

    private fun getStatusLabel(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> "RADAR"
        is WidgetState.Connecting -> "RADAR"
        is WidgetState.Clear -> radarExtension.getString(R.string.state_clear).uppercase()
        is WidgetState.Threat -> when (state.level) {
            ThreatLevel.APPROACHING -> radarExtension.getString(R.string.threat_approaching).uppercase()
            ThreatLevel.WARNING -> radarExtension.getString(R.string.threat_warning).uppercase()
            ThreatLevel.CRITICAL -> radarExtension.getString(R.string.threat_critical).uppercase()
            ThreatLevel.CLEAR -> radarExtension.getString(R.string.state_clear).uppercase()
        }
        is WidgetState.ConnectionLost -> "RADAR"
    }

    private fun getMainText(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> "-"
        is WidgetState.Connecting -> "..."
        is WidgetState.Clear -> "OK"
        is WidgetState.Threat -> state.vehicleCount.toString()
        is WidgetState.ConnectionLost -> "!"
    }

    private fun getStatusMessage(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> radarExtension.getString(R.string.widget_connect_radar)
        is WidgetState.Connecting -> radarExtension.getString(R.string.widget_searching)
        is WidgetState.Clear -> radarExtension.getString(R.string.widget_road_clear)
        is WidgetState.Threat -> {
            val word = if (state.vehicleCount == 1) {
                radarExtension.getString(R.string.vehicle_word_singular)
            } else {
                radarExtension.getString(R.string.vehicle_word_plural)
            }
            if (state.nearestDistanceM > 0) {
                "${formatDistance(state.nearestDistanceM)} — $word"
            } else {
                "${state.vehicleCount} $word ${radarExtension.getString(R.string.vehicles_behind_simple)}"
            }
        }
        is WidgetState.ConnectionLost -> radarExtension.getString(R.string.widget_connection_lost)
    }
}

package io.github.ykn.variaradarpro.datatypes.glance

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
import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.ViewConfig

/**
 * Large widget — full radar information.
 * Status bar + threat label + large count/distance + status message.
 */
class LargeWidgetGlanceDataType(
    radarExtension: VariaRadarExtension
) : GlanceDataType(radarExtension, "radar-large") {

    @Composable
    override fun Content(state: WidgetState, settings: PresetSettings, config: ViewConfig, muted: Boolean) {
        DataFieldContainer {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusBar(state, muted, height = 5)

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Threat level label
                        LabelText(
                            text = if (muted) radarExtension.getString(R.string.widget_muted) else getStatusLabel(state),
                            fontSize = 13
                        )

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        // Main row: vehicle count + distance
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ValueText(
                                text = getMainText(state),
                                color = GlanceColors.forState(state),
                                fontSize = 48
                            )

                            if (state is WidgetState.Threat && state.nearestDistanceM > 0) {
                                Spacer(modifier = GlanceModifier.width(14.dp))
                                ValueText(
                                    text = formatDistance(state.nearestDistanceM),
                                    color = GlanceColors.White,
                                    fontSize = 24
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        // Status message
                        LabelText(
                            text = if (muted) radarExtension.getString(R.string.alerts_muted) else getStatusMessage(state),
                            fontSize = 13
                        )
                    }
                }
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
        is WidgetState.NotConnected -> "--"
        is WidgetState.Connecting -> "--"
        is WidgetState.Clear -> "OK"
        is WidgetState.Threat -> state.vehicleCount.toString()
        is WidgetState.ConnectionLost -> "--"
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

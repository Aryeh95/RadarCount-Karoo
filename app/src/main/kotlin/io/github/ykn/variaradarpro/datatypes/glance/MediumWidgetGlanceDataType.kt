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
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.ViewConfig

/**
 * Medium widget — vehicle count + distance.
 * Status bar + main value row + label underneath.
 */
class MediumWidgetGlanceDataType(
    radarExtension: VariaRadarExtension
) : GlanceDataType(radarExtension, "radar-medium") {

    @Composable
    override fun Content(state: WidgetState, settings: PresetSettings, config: ViewConfig, muted: Boolean) {
        DataFieldContainer {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusBar(state, muted)

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Main row: count + distance
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ValueText(
                                text = getMainText(state),
                                color = GlanceColors.forState(state),
                                fontSize = 32
                            )

                            if (state is WidgetState.Threat && state.nearestDistanceM > 0) {
                                Spacer(modifier = GlanceModifier.width(10.dp))
                                ValueText(
                                    text = formatDistance(state.nearestDistanceM),
                                    color = GlanceColors.White,
                                    fontSize = 20
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(2.dp))

                        // Status label
                        LabelText(
                            text = if (muted) radarExtension.getString(R.string.widget_muted) else getLabel(state),
                            fontSize = 12
                        )
                    }
                }
            }
        }
    }

    private fun getMainText(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> "--"
        is WidgetState.Connecting -> "--"
        is WidgetState.Clear -> "OK"
        is WidgetState.Threat -> state.vehicleCount.toString()
        is WidgetState.ConnectionLost -> "--"
    }

    private fun getLabel(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> radarExtension.getString(R.string.widget_no_radar)
        is WidgetState.Connecting -> radarExtension.getString(R.string.widget_connecting)
        is WidgetState.Clear -> radarExtension.getString(R.string.widget_all_clear)
        is WidgetState.Threat -> if (state.nearestDistanceM > 0) {
            val word = if (state.vehicleCount == 1) {
                radarExtension.getString(R.string.vehicle_word_singular)
            } else {
                radarExtension.getString(R.string.vehicle_word_plural)
            }
            word
        } else {
            radarExtension.getString(R.string.widget_behind)
        }
        is WidgetState.ConnectionLost -> radarExtension.getString(R.string.widget_lost)
    }
}

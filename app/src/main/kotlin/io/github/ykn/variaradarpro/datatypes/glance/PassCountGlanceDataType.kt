package io.github.ykn.variaradarpro.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.ViewConfig

/**
 * Vehicles-passed widget — running count of vehicles that have overtaken
 * the rider this ride, with the current lap count underneath.
 */
class PassCountGlanceDataType(
    radarExtension: VariaRadarExtension
) : GlanceDataType(radarExtension, "radar-passed") {

    @Composable
    override fun Content(input: RenderInput, config: ViewConfig) {
        val connected = input.state !is WidgetState.NotConnected &&
            input.state !is WidgetState.Connecting &&
            input.state !is WidgetState.ConnectionLost

        DataFieldContainer {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusBar(input.state, input.muted)

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LabelText(
                            text = radarExtension.getString(R.string.widget_passed_label),
                            fontSize = 12
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        ValueText(
                            text = if (connected) input.passCount.toString() else "--",
                            color = if (connected) GlanceColors.White else GlanceColors.Neutral,
                            fontSize = 36
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        LabelText(
                            text = radarExtension.getString(R.string.widget_lap_passed_label, input.lapPassCount),
                            fontSize = 12
                        )
                    }
                }
            }
        }
    }
}

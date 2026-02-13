package io.github.ykn.variaradarpro.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.ViewConfig

/**
 * Small widget — compact radar status.
 * Status bar at top + large colored value centered.
 */
class SmallWidgetGlanceDataType(
    radarExtension: VariaRadarExtension
) : GlanceDataType(radarExtension, "radar-small") {

    @Composable
    override fun Content(state: WidgetState, settings: PresetSettings, config: ViewConfig) {
        DataFieldContainer {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                StatusBar(state)

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ValueText(
                        text = getDisplayText(state),
                        color = GlanceColors.forState(state),
                        fontSize = 36
                    )
                }
            }
        }
    }

    private fun getDisplayText(state: WidgetState): String = when (state) {
        is WidgetState.NotConnected -> "--"
        is WidgetState.Connecting -> "--"
        is WidgetState.Clear -> "OK"
        is WidgetState.Threat -> state.vehicleCount.toString()
        is WidgetState.ConnectionLost -> "--"
    }
}

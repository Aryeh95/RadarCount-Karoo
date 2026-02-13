package io.github.ykn.variaradarpro.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.ViewConfig

/**
 * Small widget (1x1) - Minimal design.
 * Background color indicates status using traffic light system.
 */
class SmallWidgetGlanceDataType(
    radarExtension: VariaRadarExtension
) : GlanceDataType(radarExtension, "radar-small") {

    @Composable
    override fun Content(state: WidgetState, settings: PresetSettings, config: ViewConfig) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(WidgetColors.forState(state))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = getDisplayText(state),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    private fun getDisplayText(state: WidgetState): String {
        return when (state) {
            is WidgetState.NotConnected -> "-"
            is WidgetState.Connecting -> "..."
            is WidgetState.Clear -> "OK"
            is WidgetState.Threat -> state.vehicleCount.toString()
            is WidgetState.ConnectionLost -> "!"
        }
    }
}

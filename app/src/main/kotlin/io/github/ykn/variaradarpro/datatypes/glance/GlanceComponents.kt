package io.github.ykn.variaradarpro.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState

/**
 * Color definitions for Glance widgets.
 * Optimized for outdoor readability on Karoo display.
 */
object GlanceColors {
    val White = Color(0xFFFFFFFF)
    val Safe = Color(0xFF00C853)        // Green
    val Caution = Color(0xFFFF9100)     // Orange
    val Danger = Color(0xFFFF1744)      // Red
    val Label = Color(0xFFAAAAAA)       // Light gray for labels
    val Neutral = Color(0xFF555555)     // Medium gray for disconnected
    val Background = Color(0xFF000000)  // Black content area
    val Frame = Color(0xFF1A1A1A)       // Subtle border between cells

    fun forState(state: WidgetState): Color = when (state) {
        is WidgetState.NotConnected -> Neutral
        is WidgetState.Connecting -> Neutral
        is WidgetState.Clear -> Safe
        is WidgetState.Threat -> forThreatLevel(state.level)
        is WidgetState.ConnectionLost -> Neutral
    }

    fun forThreatLevel(level: ThreatLevel): Color = when (level) {
        ThreatLevel.CLEAR -> Safe
        ThreatLevel.APPROACHING -> Caution
        ThreatLevel.WARNING -> Caution
        ThreatLevel.CRITICAL -> Danger
    }
}

/**
 * Standard container for all data fields.
 * Frame color creates subtle border between adjacent cells.
 * Prevents background color from reaching widget corners.
 */
@Composable
fun DataFieldContainer(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceColors.Frame)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceColors.Background)
        ) {
            content()
        }
    }
}

/**
 * Colored status bar indicating threat level.
 * Placed at the top of every widget for immediate visual feedback.
 */
@Composable
fun StatusBar(
    state: WidgetState,
    height: Int = 5
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height.dp)
            .background(GlanceColors.forState(state))
    ) {}
}

/**
 * Large bold value text (vehicle count, status indicator).
 */
@Composable
fun ValueText(
    text: String,
    color: Color = GlanceColors.White,
    fontSize: Int = 18,
    modifier: GlanceModifier = GlanceModifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = ColorProvider(color),
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

/**
 * Small label text (status messages, labels).
 */
@Composable
fun LabelText(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: Int = 12,
    color: Color = GlanceColors.Label
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize.sp,
            color = ColorProvider(color),
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

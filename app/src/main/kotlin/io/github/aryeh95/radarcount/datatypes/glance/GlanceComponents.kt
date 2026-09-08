package io.github.aryeh95.radarcount.datatypes.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.aryeh95.radarcount.data.ThemeSetting
import io.github.aryeh95.radarcount.data.models.ThreatLevel
import io.github.aryeh95.radarcount.data.models.WidgetState

/**
 * Colours for the data fields. No background is drawn: the Karoo paints the
 * field cell in its own theme, and text follows the device's light/dark
 * mode unless overridden in settings.
 */
object GlanceColors {
    val Safe = Color(0xFF00A844)        // Green, readable on white and black
    val Caution = Color(0xFFF08A00)     // Orange
    val Danger = Color(0xFFE5173F)      // Red
    val Neutral = Color(0xFF8A8A8A)     // Grey for disconnected

    private val TextDay = Color(0xFF000000)
    private val TextNight = Color(0xFFFFFFFF)
    private val LabelDay = Color(0xFF555555)
    private val LabelNight = Color(0xFFBBBBBB)

    fun text(theme: ThemeSetting): ColorProvider = themed(theme, TextDay, TextNight)
    fun label(theme: ThemeSetting): ColorProvider = themed(theme, LabelDay, LabelNight)

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

    private fun themed(theme: ThemeSetting, day: Color, night: Color): ColorProvider = when (theme) {
        ThemeSetting.AUTO -> DayNightColorProvider(day = day, night = night)
        ThemeSetting.LIGHT -> ColorProvider(day)
        ThemeSetting.DARK -> ColorProvider(night)
    }
}

/**
 * Transparent container so the field matches the Karoo's own fields.
 */
@Composable
fun DataFieldContainer(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 2.dp), contentAlignment = Alignment.TopCenter) {
        content()
    }
}

/**
 * Large bold value text.
 */
@Composable
fun ValueText(
    text: String,
    color: ColorProvider,
    fontSize: Int,
    modifier: GlanceModifier = GlanceModifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

/**
 * Small label text.
 */
@Composable
fun LabelText(
    text: String,
    color: ColorProvider,
    fontSize: Int,
    modifier: GlanceModifier = GlanceModifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = fontSize.sp,
            color = color,
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

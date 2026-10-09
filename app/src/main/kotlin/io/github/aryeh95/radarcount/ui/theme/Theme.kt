package io.github.aryeh95.radarcount.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.aryeh95.radarcount.datatypes.render.FieldColors

/** Behind the cards: the neutral grey of the Karoo's and Barberfish's settings screens. */
val ScreenGrey = Color(0xFFF4F4F4)

/** Cards, the Status tab and menus: white, like the Karoo's own settings and picker screens. */
val CardWhite = Color.White

/** Inside an open field card, under its previews and settings. */
val CardInside = Color(0xFFE8EEF3)

/** The Karoo's floating back button, as Barberfish measured it. */
val BackButton = Color(0xFFA0B4BE)

val Ink = Color(0xFF1C1B1F)
val InkMuted = Color(0xFF636363)

/** The Karoo's dark slate, for buttons, switches and the field cards' icons. */
val KarooSlate = Color(0xFF214559)

/** The Status tab with no car behind: the Radar field's header green, so the app and the field agree. */
val ClearGreen = Color(FieldColors.RADAR_HEADER_GREEN)

/** The Status tab with a car behind. Darker than a signal orange so it still reads on white. */
val ApproachAmber = Color(0xFFE07000)

/** The Status tab at the radar's highest threat level. */
val DangerRed = Color(0xFFD62828)

// The same scheme other Karoo extensions use, so the screens look like the
// Karoo's own: Material 3 light with a dark slate primary. Every surface and
// container role is set to a neutral grey or white, so none of Material's
// default lavender shows through (screen, tab bar, menus, back button).
private val KarooColorScheme = lightColorScheme(
    primary = KarooSlate,
    secondary = InkMuted,
    tertiary = Color(0xFFFEF69A),
    error = DangerRed,
    primaryContainer = BackButton,
    onPrimaryContainer = Color.Black,
    secondaryContainer = Color(0xFFDDE3E7),
    onSecondaryContainer = Ink,
    background = ScreenGrey,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = CardWhite,
    surfaceDim = Color(0xFFE0E0E0),
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = Color(0xFFF7F7F7),
    surfaceContainerHighest = Color(0xFFEFEFEF),
    outlineVariant = Color(0xFFD6DDE3)
)

private fun style(sizeSp: Int, lineSp: Int, weight: FontWeight = FontWeight.Normal, trackingSp: Double, color: Color = Color.Unspecified) =
    TextStyle(fontSize = sizeSp.sp, lineHeight = lineSp.sp, fontWeight = weight, letterSpacing = trackingSp.sp, color = color)

/**
 * Only the styles the screens use are set; the rest stay Material's. Body
 * text is 16 sp so it reads at arm's length on a Karoo, and text buttons
 * (labelLarge) match it in bold. The body styles carry their colours, so
 * text drawn in them, the dropdowns' labels and menu items included, is
 * ink or muted ink without each caller saying so.
 */
private val KarooType = Typography(
    bodyLarge = style(16, 22, trackingSp = 0.1, color = Ink),
    bodySmall = style(12, 16, trackingSp = 0.4, color = InkMuted),
    labelLarge = style(16, 24, FontWeight.Bold, trackingSp = 0.1),
    labelMedium = style(14, 20, FontWeight.Medium, trackingSp = 0.5, color = InkMuted)
)

/** Light, like the Karoo's own screens. */
@Composable
fun RadarCountTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KarooColorScheme, typography = KarooType, content = content)
}

package io.github.aryeh95.radarcount.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

/** The Status tab with no car behind. */
val ClearGreen = Color(0xFF00C853)

/** The Status tab with a car behind, below the top threat level. */
val ApproachAmber = Color(0xFFFF9100)

/** The Status tab at the radar's highest threat level. */
val DangerRed = Color(0xFFFF1744)

/** The Status tab while no radar is live, and the version line under the settings. */
val IdleGrey = Color(0xFF666666)

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

private fun style(
    sizeSp: Int,
    lineSp: Int,
    weight: FontWeight = FontWeight.Normal,
    trackingSp: Double,
    color: Color = Color.Unspecified,
) = TextStyle(
    fontSize = sizeSp.sp,
    lineHeight = lineSp.sp,
    fontWeight = weight,
    letterSpacing = trackingSp.sp,
    color = color,
)

/**
 * Set only for the styles these screens draw with; Material fills in the
 * rest. Toggle and card titles, dropdown values and menu items are
 * bodyLarge, hints and captions bodySmall, text buttons labelLarge and
 * the Status tab's stat labels labelMedium. Colours ride along in the
 * styles, so the dropdowns, whose text this app does not colour itself,
 * come out ink and muted ink too.
 */
private val ScreenText = Typography(
    bodyLarge = style(16, 22, trackingSp = 0.15, color = Ink),
    bodySmall = style(12, 16, trackingSp = 0.4, color = InkMuted),
    labelLarge = style(16, 22, FontWeight.Bold, trackingSp = 0.1, color = Ink),
    labelMedium = style(14, 18, FontWeight.Medium, trackingSp = 0.5, color = InkMuted)
)

/** Light, like the Karoo's own screens. */
@Composable
fun RadarCountTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KarooColorScheme, typography = ScreenText, content = content)
}

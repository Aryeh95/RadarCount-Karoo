package io.github.aryeh95.mybiketraffic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Minimalist color palette for Karoo 3 cycling computer.
 *
 * Design principles:
 * - High contrast for outdoor visibility
 * - 5 core colors only
 * - Large touch targets (48dp minimum)
 * - Readable at a glance while cycling
 */

// ============================================
// CORE COLORS (5 colors only)
// ============================================

// Background - pure black for OLED efficiency and contrast
val Background = Color(0xFF000000)

// Surface - subtle elevation for cards
val Surface = Color(0xFF1A1A1A)

// Text - high contrast white
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFCCCCCC) // Better contrast than #B0B0B0

// Accent - warm orange for interactive elements
val Accent = Color(0xFFFF9500)


// ============================================
// STATUS COLORS (traffic light system)
// ============================================

// Clear/Safe - confident green
val StatusSafe = Color(0xFF00C853)

// Caution/Warning - bright orange (visible in sunlight)
val StatusCaution = Color(0xFFFF9100)

// Danger/Critical - urgent red
val StatusDanger = Color(0xFFFF1744)

// Info/Neutral - for connecting states
val StatusNeutral = Color(0xFF666666)


// ============================================
// MATERIAL THEME SCHEME
// ============================================

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    primaryContainer = Accent.copy(alpha = 0.2f),
    onPrimaryContainer = Accent,

    secondary = StatusSafe,
    onSecondary = Color.Black,

    background = Background,
    onBackground = TextPrimary,

    surface = Surface,
    onSurface = TextPrimary,

    surfaceVariant = Surface,
    onSurfaceVariant = TextSecondary,

    outline = TextSecondary.copy(alpha = 0.5f),

    error = StatusDanger,
    onError = Color.White
)


// ============================================
// TYPOGRAPHY - Large, readable fonts for cycling
// ============================================

private val KarooTypography = Typography(
    // Large titles - 28sp for main headers
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),

    // Section headers - 22sp
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),

    // Card titles - 20sp
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),

    // List item titles - 18sp
    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
        color = TextPrimary
    ),

    // Body text - 16sp (minimum for outdoor readability)
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = TextPrimary
    ),

    // Secondary body - 14sp
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = TextSecondary
    ),

    // Captions - 12sp (only for non-critical info)
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = TextSecondary
    ),

    // Button text - 16sp bold
    labelLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
        color = TextPrimary
    ),

    // Small labels - 14sp
    labelMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    )
)


// ============================================
// THEME COMPOSABLE
// ============================================

@Composable
fun MyBikeTrafficTheme(
    content: @Composable () -> Unit
) {
    // Always dark theme for cycling computer
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = KarooTypography,
        content = content
    )
}


// ============================================
// HELPER EXTENSIONS
// ============================================

/**
 * Get status color for threat level.
 * Uses traffic light system: green -> orange -> red
 */
object RadarColors {
    val safe = StatusSafe
    val caution = StatusCaution
    val danger = StatusDanger
    val neutral = StatusNeutral
    val accent = Accent
    val background = Background
    val surface = Surface
    val textPrimary = TextPrimary
    val textSecondary = TextSecondary
}

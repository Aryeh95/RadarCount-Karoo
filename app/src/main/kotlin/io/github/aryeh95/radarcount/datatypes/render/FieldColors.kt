package io.github.aryeh95.radarcount.datatypes.render

import android.content.Context
import android.content.res.Configuration
import io.github.aryeh95.radarcount.data.ThemeSetting

/**
 * Colours for the data fields, as ARGB for drawing. No background is
 * drawn: the Karoo paints the field cell in its own theme, and text follows
 * the Karoo's night mode unless forced light or dark in settings. The
 * colours are baked into the bitmap, so every frame resolves them afresh.
 */
object FieldColors {
    /** The radar icon in the Radar field's header: a green that reads on white and on black. */
    const val RADAR_HEADER_GREEN = 0xFF00A844.toInt()
    /** NO RADAR, in day and night mode alike. */
    const val NO_RADAR_GREY = 0xFF8A8A8A.toInt()
    /** Developer aid: translucent blue over the field's whole view. */
    const val DEBUG_TINT = 0x593366FF

    private const val TEXT_DAY = 0xFF000000.toInt()
    private const val TEXT_NIGHT = 0xFFFFFFFF.toInt()
    private const val LABEL_DAY = 0xFF555555.toInt()
    private const val LABEL_NIGHT = 0xFFBBBBBB.toInt()
    /**
     * A passed car's held speed, dimmer than the live text. Tuned in
     * sunlight on a Karoo 2: the caption grey (BB) read the same as a live
     * value, 7A could barely be seen; A0 sits between.
     */
    private const val HELD_DAY = 0xFF808080.toInt()
    private const val HELD_NIGHT = 0xFFA0A0A0.toInt()
    /** The Karoo's header icon green while a field has data, in light and dark mode, as measured on the device. */
    private const val ICON_DAY = 0xFF129A5E.toInt()
    private const val ICON_NIGHT = 0xFF31E09A.toInt()

    /** [icon] is the single fields' header icon while a radar is connected; without one it takes [text], as the Karoo's own icons do without data. [held] is a passed car's held speed. */
    data class Palette(val text: Int, val label: Int, val icon: Int, val held: Int)

    /** Whether [context] is in night mode: what AUTO follows. */
    fun systemNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun night(theme: ThemeSetting, systemNight: Boolean): Boolean = when (theme) {
        ThemeSetting.AUTO -> systemNight
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
    }

    fun palette(night: Boolean): Palette =
        if (night) Palette(TEXT_NIGHT, LABEL_NIGHT, ICON_NIGHT, HELD_NIGHT) else Palette(TEXT_DAY, LABEL_DAY, ICON_DAY, HELD_DAY)

    /** Text colours for [theme] as the field in [context] shows them. */
    fun palette(context: Context, theme: ThemeSetting): Palette = palette(night(theme, systemNight(context)))
}

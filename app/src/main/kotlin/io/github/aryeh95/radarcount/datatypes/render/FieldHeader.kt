package io.github.aryeh95.radarcount.datatypes.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import io.github.aryeh95.radarcount.datatypes.FieldFont
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.max

/** A single field's header drawn into a bitmap, and the label size it took, for the on-device test. */
class HeaderImage(val image: FieldImage, val label: String, val labelSp: Float, val karooSp: Float)

/**
 * The header the single fields draw themselves in place of the Karoo's
 * strip, which wraps a long name onto a second line at half width
 * ("VEHICLES / PER HOUR") and so takes room from the value. Drawn like the
 * Karoo's own header, as measured on the device (and as Barberfish draws
 * its fields' headers): the data type's icon in the header icon green,
 * the name in capitals in the Karoo's header font and size, both centred
 * in a band 22 dp tall. The name always stays on one line: where it does
 * not fit at the Karoo's size it is drawn only as much smaller as it needs.
 *
 * Drawn into the bitmap rather than set on a TextView: a TextView's line
 * box differs between the Karoo 2's Android 8 and the Karoo 3's Android 12,
 * and aligning, wrapping or sizing one through RemoteViews is partly
 * unavailable on Android 8, while measured glyphs land the same on both.
 */
object FieldHeader {

    /** The Karoo's header band, at least this tall. */
    private const val BAND_DP = 22f
    /** Between the tile edge and the icon or name: the field's 4 dp side padding (2 dp in narrow slots) and the header's own 1 dp. */
    private const val SIDE_DP = 4f
    private const val SIDE_NARROW_DP = 2f
    private const val INSET_DP = 1f
    /** Before the name, on its start side. */
    private const val LABEL_MARGIN_DP = 1f

    /**
     * The Karoo's header label size for a tile, in sp, by its grid span. In
     * a half-width tile of a five-row page the Karoo follows the rider's
     * Label Size setting, which also sets the value size it reports: 41 sp
     * at Large, 46 or 47 at Small.
     */
    fun karooSp(config: ViewConfig): Float {
        val (cols, rows) = config.gridSize
        return when {
            cols == 60 && rows >= 15 -> 19.2f
            cols == 60 && rows >= 12 -> 17.6f
            cols == 30 && rows >= 15 -> 17.6f
            cols == 30 -> if (config.textSize <= 43) 17.6f else 15.5f
            cols == 20 && rows >= 12 -> 12f
            cols == 15 && rows >= 12 -> 11f
            else -> 15.5f
        }
    }

    /** Height of the header band in px for [config], which the value area starts below. */
    fun heightPx(config: ViewConfig, density: Float, text: FieldText = FieldText()): Int {
        val line = text.lineBox(FieldFont.LABEL, karooSp(config) * density)
        return max((BAND_DP * density).toInt(), line.descent - line.ascent)
    }

    /**
     * Icon [iconRes] tinted [iconColor] and [label] in [color], across the
     * full width of a tile of [config]'s size, placed per its alignment as
     * the Karoo places them: right-aligned names with the icon at the left,
     * left-aligned names with it at the right, centred names centred in the
     * room right of the icon.
     */
    fun draw(context: Context, label: String, iconRes: Int, config: ViewConfig, density: Float, color: Int, iconColor: Int): HeaderImage {
        val text = FieldText()
        val upper = label.uppercase()
        val w = config.viewSize.first.coerceAtLeast(1)
        val h = heightPx(config, density, text)
        val karooSp = karooSp(config)
        // As the Karoo sizes its header: whole px, the icon as tall as the name's size in dp.
        val side = ((if (config.gridSize.first <= 20) SIDE_NARROW_DP else SIDE_DP) * density).toInt() + layoutPx(INSET_DP, density)
        val iconPx = (karooSp * density).toInt()
        val gapPx = (max(2, (karooSp * 0.2f).toInt()) * density).toInt()
        val marginPx = layoutPx(LABEL_MARGIN_DP, density)
        val room = (w - 2 * side - iconPx - gapPx - marginPx).toFloat()

        // The Karoo's size, or just small enough for one line.
        var sizePx = karooSp * density
        val full = text.width(upper, FieldFont.LABEL, sizePx)
        if (room > 0 && full > room) {
            sizePx *= room / full
            while (sizePx > 1f && text.width(upper, FieldFont.LABEL, sizePx) > room) sizePx -= 0.01f * density
        }

        val (iconLeft, x, align) = when (config.alignment) {
            ViewConfig.Alignment.RIGHT -> Triple(side, (w - side).toFloat(), Paint.Align.RIGHT)
            ViewConfig.Alignment.LEFT -> Triple(w - side - iconPx, (side + marginPx).toFloat(), Paint.Align.LEFT)
            ViewConfig.Alignment.CENTER -> Triple(side, (side + iconPx + gapPx + marginPx + (w - side)) / 2f, Paint.Align.CENTER)
        }
        // Both centred in the band, the name by its line box as a TextView centres it.
        val line = text.lineBox(FieldFont.LABEL, sizePx)
        val baseline = (h - (line.descent - line.ascent)) / 2f - line.ascent
        val iconTop = (h - iconPx) / 2

        val bitmap = FieldBitmaps.newBitmap(w, h)
        val canvas = Canvas(bitmap)
        val icon = checkNotNull(context.getDrawable(iconRes)).mutate()
        icon.setTint(iconColor)
        icon.setBounds(iconLeft, iconTop, iconLeft + iconPx, iconTop + iconPx)
        icon.draw(canvas)
        val name = text.draw(canvas, upper, FieldFont.LABEL, sizePx, x, baseline, align, color)
        return HeaderImage(FieldImage(bitmap, listOf(Rect(icon.bounds), name)), upper, sizePx / density, karooSp)
    }

    /** [dp] as Android sizes it in a layout: rounded to whole px. */
    private fun layoutPx(dp: Float, density: Float): Int = (dp * density + 0.5f).toInt()
}

package io.github.aryeh95.radarcount.datatypes.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import io.github.aryeh95.radarcount.R
import io.github.aryeh95.radarcount.datatypes.ComboLayout
import io.github.aryeh95.radarcount.datatypes.FieldFont
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A field drawn into a bitmap, with the rectangle every glyph and icon covers so a test can check none is cut off. */
class FieldImage(val bitmap: Bitmap, val glyphs: List<Rect>) {
    /** Glyphs that fall outside the bitmap. Empty by construction; the on-device test checks it. */
    val clipped: List<Rect>
        get() = glyphs.filterNot { Rect(0, 0, bitmap.width, bitmap.height).contains(it) }
}

/** The Radar field: its own header strip, if shown, and the cells under it. */
class ComboImage(val header: FieldImage?, val body: FieldImage)

/**
 * Draws the fields onto a Canvas with exact glyph metrics, so where the
 * digits land does not depend on how a TextView or Glance distributes
 * line-box overflow, which differs between the Karoo 2's Android 8 and
 * the Karoo 3's Android 12.
 */
object FieldBitmaps {

    /** Header strip contents, in the style of the Karoo's own. */
    private const val HEADER_TEXT = "RADAR"
    private const val HEADER_SP = 13f
    private const val HEADER_ICON_DP = 13f
    private const val HEADER_ICON_GAP_DP = 4f

    /** Single values keep this much clear of the tile's sides, like the Karoo's own fields: the padding in field_value_*.xml. */
    const val VALUE_SIDE_DP = 5f
    /** Between the value's baseline and the top of the tag under it, as a fraction of the drawn value size. */
    private const val TAG_GAP = 0.35f
    /** Spare pixel round a cropped bitmap so anti-aliased edges are not cut. */
    private const val EDGE_PX = 1f

    /**
     * The whole Radar field on a tile of [config]'s size: the optional
     * RADAR header strip, and under it [plan] laid out by
     * [ComboLayout.arrange] with the real fonts. Two images, so the layout
     * can keep the header at the top and centre the cells in whatever is
     * left if the host's view is taller than the tile it reported.
     * Transparent where nothing is drawn.
     */
    fun combo(context: Context, plan: ComboLayout.Plan, config: ViewConfig, density: Float, palette: FieldColors.Palette, header: Boolean): ComboImage {
        val w = config.viewSize.first.coerceAtLeast(1)
        val h = config.viewSize.second.coerceAtLeast(1)
        val text = FieldText()
        // Whole px, so the strip and the cells under it add up to the tile exactly.
        val headerPx = if (header) layoutPx(ComboLayout.HEADER_DP, density) else 0
        val bodyH = (h - headerPx).coerceAtLeast(1)
        val a = ComboLayout.arrange(
            plan, w, bodyH, config.textSize, density, wideGrid = config.gridSize.first >= 60,
            headerDp = 0f, measurer = text
        )
        val bitmap = newBitmap(w, bodyH)
        val canvas = Canvas(bitmap)
        val glyphs = mutableListOf<Rect>()
        for (t in a.texts) {
            // Captions in the label grey; a held pass speed in the darker held grey.
            val color = when {
                t.font == FieldFont.CAPTION -> palette.label
                t.dim -> palette.held
                else -> palette.text
            }
            glyphs += text.draw(canvas, t.text, t.font, t.sizePx, t.centerX, t.baseline, Paint.Align.CENTER, color)
        }
        val strip = if (header) {
            val b = newBitmap(w, headerPx)
            FieldImage(b, drawHeader(context, Canvas(b), text, w, headerPx.toFloat(), density, palette.text))
        } else {
            null
        }
        return ComboImage(strip, FieldImage(bitmap, glyphs))
    }

    /** Icon and RADAR centred in a strip [headerPx] tall. */
    private fun drawHeader(context: Context, canvas: Canvas, text: FieldText, width: Int, headerPx: Float, density: Float, color: Int): List<Rect> {
        val iconPx = (HEADER_ICON_DP * density).roundToInt()
        val gapPx = HEADER_ICON_GAP_DP * density
        val sizePx = HEADER_SP * density
        val left = ((width - (iconPx + gapPx + text.width(HEADER_TEXT, FieldFont.HEADER, sizePx))) / 2).roundToInt()
        val iconTop = ((headerPx - iconPx) / 2).roundToInt()
        val icon = checkNotNull(context.getDrawable(R.drawable.ic_radar)).mutate()
        icon.setTint(FieldColors.RADAR_HEADER_GREEN)
        icon.setBounds(left, iconTop, left + iconPx, iconTop + iconPx)
        icon.draw(canvas)
        val baseline = (headerPx + text.bandHeight(FieldFont.HEADER) * sizePx) / 2
        val label = text.draw(canvas, HEADER_TEXT, FieldFont.HEADER, sizePx, left + iconPx + gapPx, baseline, Paint.Align.LEFT, color)
        return listOf(Rect(icon.bounds), label)
    }

    /**
     * One value, and optionally a [tag] line under it, as the single fields
     * (and the Radar field's NO RADAR) show it: monospace at 97% of the
     * Karoo's numeric size, shrunk to fit the tile's width less 5 dp each
     * side and, if [roomH] is given, that many px of height, the tag at 40%
     * of that in the caption font, both aligned per [ViewConfig.alignment].
     * The bitmap is cropped to the text and the view centres it vertically.
     * Its extent is measured from [layoutSample] too (the field's widest-ink
     * value, e.g. "88mph"), so the digits sit at the same height whatever
     * the string ("--", "45mph"), and a descender (the p of mph) only takes
     * the room it needs below the digits instead of the same again above.
     */
    fun value(
        value: String, tag: String?, color: Int, tagColor: Int, config: ViewConfig, density: Float, roomH: Int = 0,
        layoutSample: String? = null
    ): FieldImage {
        val text = FieldText()
        val align = when (config.alignment) {
            ViewConfig.Alignment.LEFT -> Paint.Align.LEFT
            ViewConfig.Alignment.CENTER -> Paint.Align.CENTER
            ViewConfig.Alignment.RIGHT -> Paint.Align.RIGHT
        }
        fun lines(sp: Int): List<Line> {
            val valueLine = Line(value, FieldFont.VALUE, sp * ComboLayout.VALUE_SCALE * density)
            val tagPx = (sp * ComboLayout.CAPTION_RATIO).toInt().coerceIn(9, 16) * density
            return if (tag != null) listOf(valueLine, Line(tag, FieldFont.CAPTION, tagPx)) else listOf(valueLine)
        }
        fun widthAt(sp: Int): Float = text.span(lines(sp), align).let { it.second - it.first }

        /**
         * Where the lines go at [sp], in px from the value's baseline: the
         * digit band, the tag under it, and the ink of the value and of
         * [layoutSample] beyond them, top and bottom each as far as needed.
         */
        class Fit(sp: Int) {
            val lines = lines(sp)
            val valuePx = lines[0].sizePx
            val tagPx = lines.getOrNull(1)?.sizePx ?: 0f
            val tagBaseline = TAG_GAP * valuePx + text.bandHeight(FieldFont.CAPTION) * tagPx
            /** The extent's top (negative, above the baseline) and bottom. */
            val top: Float
            val height: Int
            init {
                var lo = -text.bandHeight(FieldFont.VALUE) * valuePx
                var hi = if (tag != null) tagBaseline else 0f
                for (sample in listOfNotNull(value, layoutSample)) {
                    val ink = text.ink(sample, FieldFont.VALUE, valuePx)
                    lo = minOf(lo, ink.top)
                    hi = maxOf(hi, ink.bottom)
                }
                if (tag != null) {
                    val tagInk = text.ink(tag, FieldFont.CAPTION, tagPx)
                    lo = minOf(lo, tagBaseline + tagInk.top)
                    hi = maxOf(hi, tagBaseline + tagInk.bottom)
                }
                top = lo
                height = ceil(hi - lo + 2 * EDGE_PX).toInt()
            }
        }

        // The width the layout really gives the image (its dp padding rounds
        // to whole px) less the spare edge, so a fitted value is shown 1:1.
        val roomW = config.viewSize.first - 2 * layoutPx(VALUE_SIDE_DP, density) - 2 * EDGE_PX
        var sp = config.textSize.coerceAtLeast(1)
        if (roomW > 0) {
            val full = widthAt(sp)
            if (full > roomW) sp = (sp * roomW / full).toInt().coerceAtLeast(1)
            while (sp > 1 && widthAt(sp) > roomW) sp--
        }
        var fit = Fit(sp)
        if (roomH > 0) {
            while (sp > 1 && fit.height > roomH) fit = Fit(--sp)
        }

        // x from the alignment anchor both lines share.
        val (left, right) = text.span(fit.lines, align)
        val w = ceil(right - left + 2 * EDGE_PX).toInt()
        val h = fit.height
        val x = EDGE_PX - left
        val baseline = EDGE_PX - fit.top

        val bitmap = newBitmap(w, h)
        val canvas = Canvas(bitmap)
        val glyphs = mutableListOf(text.draw(canvas, value, FieldFont.VALUE, fit.valuePx, x, baseline, align, color))
        if (tag != null) glyphs += text.draw(canvas, tag, FieldFont.CAPTION, fit.tagPx, x, baseline + fit.tagBaseline, align, tagColor)
        return FieldImage(bitmap, glyphs)
    }

    private class Line(val text: String, val font: FieldFont, val sizePx: Float)

    /**
     * Leftmost and rightmost px of [lines]' advances and glyphs, from the
     * anchor [align] draws them at; a glyph that overhangs its advance
     * counts, so nothing is cut at the bitmap's edge.
     */
    private fun FieldText.span(lines: List<Line>, align: Paint.Align): Pair<Float, Float> {
        var left = 0f
        var right = 0f
        for (l in lines) {
            val advance = width(l.text, l.font, l.sizePx)
            val ink = ink(l.text, l.font, l.sizePx)
            val start = when (align) {
                Paint.Align.LEFT -> 0f
                Paint.Align.CENTER -> -advance / 2
                Paint.Align.RIGHT -> -advance
            }
            left = minOf(left, start + minOf(0f, ink.left))
            right = maxOf(right, start + maxOf(advance, ink.right))
        }
        return left to right
    }

    /** [dp] as Android sizes it in a layout: rounded to whole px. */
    internal fun layoutPx(dp: Float, density: Float): Int = (dp * density + 0.5f).toInt()

    /** Transparent, and drawn 1:1 in px by the host: without DENSITY_NONE it would be rescaled by density. */
    internal fun newBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
}

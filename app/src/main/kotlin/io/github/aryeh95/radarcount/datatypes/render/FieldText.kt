package io.github.aryeh95.radarcount.datatypes.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import io.github.aryeh95.radarcount.datatypes.FieldFont
import io.github.aryeh95.radarcount.datatypes.Ink
import io.github.aryeh95.radarcount.datatypes.TextMeasurer

/**
 * The fields' text, measured and drawn with the same Paint so a string
 * lands exactly where the layout measured it. The fonts are the system's
 * monospace (values), sans (captions), bold sans (RADAR header) and the
 * Karoo's condensed header font (labels), the same families on the Karoo 2
 * and the Karoo 3. Not thread-safe: one per render.
 */
class FieldText : TextMeasurer {

    private val paints = FieldFont.entries.associateWith { font ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = when (font) {
                FieldFont.VALUE -> Typeface.MONOSPACE
                FieldFont.CAPTION -> Typeface.DEFAULT
                FieldFont.HEADER -> Typeface.DEFAULT_BOLD
                FieldFont.LABEL -> LABEL_TYPEFACE
            }
        }
    }
    private val bounds = Rect()

    /** Height of "0" for values and "H" otherwise, measured once at a large size. */
    private val bands = FieldFont.entries.associateWith { font ->
        val probe = if (font == FieldFont.VALUE) "0" else "H"
        paint(font, PROBE_PX).getTextBounds(probe, 0, 1, bounds)
        -bounds.top / PROBE_PX
    }

    private fun paint(font: FieldFont, sizePx: Float): Paint = paints.getValue(font).apply { textSize = sizePx }

    override fun width(text: String, font: FieldFont, sizePx: Float): Float = paint(font, sizePx).measureText(text)

    override fun ink(text: String, font: FieldFont, sizePx: Float): Ink {
        if (text.isEmpty()) return Ink(0f, 0f, 0f, 0f)
        paint(font, sizePx).getTextBounds(text, 0, text.length, bounds)
        return Ink(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
    }

    override fun bandHeight(font: FieldFont): Float = bands.getValue(font)

    /** The line box a TextView without font padding gives [font] at [sizePx]: whole px, ascent negative. */
    fun lineBox(font: FieldFont, sizePx: Float): Paint.FontMetricsInt = paint(font, sizePx).fontMetricsInt

    /**
     * Draws [text] with its baseline at [baseline], anchored at [x] per
     * [align], and returns the rectangle its glyphs cover on the canvas.
     */
    fun draw(canvas: Canvas, text: String, font: FieldFont, sizePx: Float, x: Float, baseline: Float, align: Paint.Align, color: Int): Rect {
        val p = paint(font, sizePx)
        p.color = color
        p.textAlign = Paint.Align.LEFT
        val left = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - p.measureText(text) / 2
            Paint.Align.RIGHT -> x - p.measureText(text)
        }
        canvas.drawText(text, left, baseline, p)
        val ink = ink(text, font, sizePx)
        return Rect(
            kotlin.math.floor(left + ink.left).toInt(),
            kotlin.math.floor(baseline + ink.top).toInt(),
            kotlin.math.ceil(left + ink.right).toInt(),
            kotlin.math.ceil(baseline + ink.bottom).toInt()
        )
    }

    private companion object {
        const val PROBE_PX = 200f
        /**
         * The font the Karoo sets its own field headers in, a system font
         * on the Karoo. Where it is missing (a phone running the render
         * test) this falls back to the system sans.
         */
        val LABEL_TYPEFACE: Typeface = Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL)
    }
}

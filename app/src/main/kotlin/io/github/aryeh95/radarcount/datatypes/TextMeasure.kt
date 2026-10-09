package io.github.aryeh95.radarcount.datatypes

/** The fonts the data fields draw with. */
enum class FieldFont {
    /** Values and digits: monospace, like the Karoo's numeric fields. */
    VALUE,
    /** Captions above the digits and the tag under the speed. */
    CAPTION,
    /** The RADAR header, bold. */
    HEADER,
    /** The single fields' header label: the Karoo's own condensed header font. */
    LABEL
}

/** Where a string's glyphs draw, in px from its start point on the baseline; [top] is negative (above). */
data class Ink(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Text measurement for laying out the fields: the real fonts on the device
 * (render.FieldText), or [EstimatedText] where there are none (JVM tests).
 * Layout works from glyphs, not line boxes, so what fits here fits on
 * screen on every Android version.
 */
interface TextMeasurer {
    /** Advance width of [text] at [sizePx]. */
    fun width(text: String, font: FieldFont, sizePx: Float): Float

    /** Where the glyphs of [text] actually draw at [sizePx]. */
    fun ink(text: String, font: FieldFont, sizePx: Float): Ink

    /**
     * Height of a digit (or a capital) as a fraction of the font size. Lines
     * are placed on this band, not on their own ink, so "--" sits where
     * digits would and a descender does not move the digits.
     */
    fun bandHeight(font: FieldFont): Float
}

/** Monospace estimate of the device fonts: 0.6 em per character, digits 0.72 em tall. */
object EstimatedText : TextMeasurer {
    private const val EM_PER_CHAR = 0.6f
    private const val BAND = 0.72f
    private const val DESCENT = 0.21f

    override fun width(text: String, font: FieldFont, sizePx: Float): Float = text.length * EM_PER_CHAR * sizePx

    override fun ink(text: String, font: FieldFont, sizePx: Float): Ink =
        Ink(0f, -BAND * sizePx, width(text, font, sizePx), if (text.any { it in "gjpqy" }) DESCENT * sizePx else 0f)

    override fun bandHeight(font: FieldFont): Float = BAND
}

package io.github.aryeh95.radarcount.datatypes

import io.github.aryeh95.radarcount.data.ComboActiveSetting
import io.github.aryeh95.radarcount.data.ComboIdleSetting
import io.github.aryeh95.radarcount.data.Settings

/**
 * Decides what the Radar combo field shows and where. Shared by the field
 * and the live preview on the settings screen, which draw the same bitmap.
 *
 * The field has two states: idle (nothing on the radar) and active (a
 * vehicle is being tracked, held briefly after it vanishes so the layout
 * does not flicker). Each state has its own setting: the count alone,
 * speed and distance alone, or all three cells. While a passed car's speed
 * is held (see [PassHold]) the field stays active and the speed cell shows
 * that speed dimmed, captioned PASSED whatever the caption setting.
 */
object ComboLayout {

    /** Caption texts, resolved from resources by the caller. */
    data class Labels(
        val count: String,
        val vehicles: String,
        val speedRel: String,
        val speedAbs: String,
        val dist: String,
        /** Speed unit as a caption, e.g. MPH or KPH. */
        val speedUnit: String,
        /** Distance unit as a caption, e.g. FT or M. */
        val distUnit: String,
        /** The caption of a held pass speed. */
        val passed: String = "PASSED"
    )

    /**
     * One value cell: a caption above the value, as the Karoo's own fields
     * draw them. A [dim] value is drawn in the caption colour: a passed
     * car's held speed, not a live one.
     */
    data class Cell(val caption: String?, val value: String, val dim: Boolean = false)

    /** Font sizes (sp) and gap (dp) for drawing a [Plan] in a field of a given size. */
    data class Sizes(val valueSp: Int, val captionSp: Int, val badgeSp: Int, val gapDp: Int)

    data class Plan(
        val cells: List<Cell>,
        /** Count shown small in the corner while speed and distance fill the field, or null. */
        val badge: String?
    )

    fun plan(
        active: Boolean,
        count: Int,
        /** Speed digits without unit, or null while unknown. */
        speed: Int?,
        /** Distance digits without unit, or null while there is no target. */
        distance: Int?,
        showsAbsolute: Boolean,
        settings: Settings,
        labels: Labels,
        /** [speed] is a passed car's held speed, not a live one. */
        passed: Boolean = false
    ): Plan {
        val countText = count.toString()
        val speedDigits = speed?.toString() ?: "--"
        val distDigits = distance?.toString() ?: "--"
        val unitsInCaptions = settings.comboUnitsInCaptions
        val speedValue = if (unitsInCaptions || speed == null) speedDigits else speedDigits + labels.speedUnit.lowercase()
        val distValue = if (unitsInCaptions || distance == null) distDigits else distDigits + labels.distUnit.lowercase()
        val speedCaption = if (showsAbsolute) labels.speedAbs else labels.speedRel

        // A held pass speed: dimmed, and captioned PASSED (PASSED KPH with
        // units as captions) even where captions are off, since grey alone
        // may not read in sunlight.
        val passedCaption = if (unitsInCaptions) "${labels.passed} ${labels.speedUnit}" else labels.passed
        val all = Plan(
            cells = listOf(
                Cell(labels.count, countText),
                if (passed) Cell(passedCaption, speedValue, dim = true)
                else Cell(if (unitsInCaptions) shortSpeedCaption(showsAbsolute, labels) else speedCaption, speedValue),
                Cell(if (unitsInCaptions) labels.distUnit else labels.dist, distValue)
            ),
            badge = null
        )
        // Captions off: no line above the digits, so they take the full
        // height. Units then have nowhere to go unless they were glued on.
        val cap = settings.comboCaptions
        return when {
            !active && settings.comboIdle == ComboIdleSetting.COUNT ->
                Plan(listOf(Cell(if (cap) labels.vehicles else null, countText)), badge = null)
            active && settings.comboActive == ComboActiveSetting.SPEED_DISTANCE ->
                Plan(
                    cells = when {
                        passed -> listOf(
                            Cell(passedCaption, speedValue, dim = true),
                            Cell(if (!cap) null else if (unitsInCaptions) labels.distUnit else labels.dist, distValue)
                        )
                        !cap -> listOf(Cell(null, speedValue), Cell(null, distValue))
                        unitsInCaptions -> listOf(Cell(labels.speedUnit, speedValue), Cell(labels.distUnit, distValue))
                        else -> listOf(Cell(speedCaption, speedValue), Cell(labels.dist, distValue))
                    },
                    badge = if (settings.comboBadge) countText else null
                )
            else -> all
        }
    }

    /** Caption glyphs are drawn at this fraction of the value size. */
    const val CAPTION_RATIO = 0.4f
    /** The count badge beside speed and distance, as a fraction of the value size. */
    const val BADGE_RATIO = 0.5f
    /** Value glyphs are drawn at this fraction of their nominal size, like the Karoo's own numeric fields. */
    const val VALUE_SCALE = 0.97f
    /**
     * Height of the header strip the field draws itself (icon and RADAR),
     * in the style of the Karoo's own. The Karoo's strip is turned off so
     * the whole tile is ours and nothing is hidden under it.
     */
    const val HEADER_DP = 20f
    /** Run to the tile edge like the Karoo's own fields: 2 dp each side. */
    private const val SIDE_DP = 2f
    /** Clear space kept above and below the glyphs inside the area under the header. */
    private const val MARGIN_DP = 2f
    /** Between the badge and the first cell. */
    private const val BADGE_GAP_DP = 4f
    /** Between a caption's baseline and the top of the digits under it, as a fraction of the drawn value size. */
    private const val CAPTION_GAP = 0.25f

    /**
     * One string placed on the tile: centred on [centerX], baseline at
     * [baseline], both px from the tile's top left; [dim] for a held pass
     * speed's digits.
     */
    data class Placed(val text: String, val font: FieldFont, val sizePx: Float, val centerX: Float, val baseline: Float, val dim: Boolean = false)

    /**
     * A plan laid out on a tile: the sizes it was fitted at and where every
     * string goes. [fits] is false only when even the smallest size spills
     * out of the tile.
     */
    data class Arrangement(val sizes: Sizes, val texts: List<Placed>, val headerPx: Float, val fits: Boolean)

    /** Font sizes for drawing [plan] in a tile; see [arrange]. */
    fun sizes(
        plan: Plan, widthPx: Int, heightPx: Int, textSizeSp: Int, density: Float, wideGrid: Boolean,
        headerDp: Float = HEADER_DP, measurer: TextMeasurer = EstimatedText
    ): Sizes = arrange(plan, widthPx, heightPx, textSizeSp, density, wideGrid, headerDp, measurer).sizes

    /**
     * Lays out [plan] on a tile [widthPx] x [heightPx] whose standard
     * numeric font is [textSizeSp]: the value font takes a share of the
     * standard size that shrinks with the number of cells, then shrinks
     * further until the glyphs, as [measurer] measures them, fit the width
     * and the height below the header. The block is centred in that area by
     * its glyphs, so it fits by construction instead of relying on the host
     * to split any overflow evenly. Shared by the field and the settings
     * preview, which draw the same bitmap.
     */
    fun arrange(
        plan: Plan, widthPx: Int, heightPx: Int, textSizeSp: Int, density: Float, wideGrid: Boolean,
        headerDp: Float = HEADER_DP, measurer: TextMeasurer = EstimatedText
    ): Arrangement {
        val n = plan.cells.size
        val gapDp = when {
            n == 1 -> 0
            wideGrid -> 10
            else -> 4
        }
        val factor = when (n) { 1 -> 0.95f; 2 -> 0.85f; else -> 0.6f }
        val headerPx = headerDp * density
        val roomW = if (widthPx <= 0) Float.MAX_VALUE else widthPx - 2 * SIDE_DP * density
        val roomH = if (heightPx <= 0) Float.MAX_VALUE else heightPx - headerPx - 2 * MARGIN_DP * density
        // Largest size whose glyphs fit, down to whatever fits: a clipped
        // digit is worse than a small one.
        var sizes = sizesFor(1, gapDp)
        var block = block(plan, sizes, density, measurer)
        var fits = false
        for (sp in (textSizeSp * factor).toInt().coerceAtLeast(1) downTo 1) {
            val s = sizesFor(sp, gapDp)
            val b = block(plan, s, density, measurer)
            if (b.width <= roomW && b.bottom - b.top <= roomH) {
                sizes = s
                block = b
                fits = true
                break
            }
        }
        val left = if (widthPx <= 0) 0f else (widthPx - block.width) / 2
        val bodyTop = headerPx
        val bodyHeight = if (heightPx <= 0) block.bottom - block.top else heightPx - headerPx
        val baseline = bodyTop + (bodyHeight - (block.bottom - block.top)) / 2 - block.top
        val texts = block.texts.map { it.copy(centerX = it.centerX + left, baseline = it.baseline + baseline) }
        return Arrangement(sizes, texts, headerPx, fits)
    }

    private fun sizesFor(valueSp: Int, gapDp: Int): Sizes {
        val captionSp = (valueSp * CAPTION_RATIO).toInt().coerceIn(9, 16)
        val badgeSp = (valueSp * BADGE_RATIO).toInt().coerceAtLeast(10)
        return Sizes(valueSp, captionSp, badgeSp, gapDp)
    }

    /** The cells as one block, x from its left edge and y from the digit baseline; [top]..[bottom] is what must fit. */
    private class Block(val texts: List<Placed>, val width: Float, val top: Float, val bottom: Float)

    private fun block(plan: Plan, s: Sizes, density: Float, m: TextMeasurer): Block {
        val valuePx = s.valueSp * VALUE_SCALE * density
        val captionPx = s.captionSp * density
        val badgePx = s.badgeSp * VALUE_SCALE * density
        val digitTop = -m.bandHeight(FieldFont.VALUE) * valuePx
        val captionBaseline = digitTop - CAPTION_GAP * valuePx
        val texts = mutableListOf<Placed>()
        var x = 0f
        if (plan.badge != null) {
            // Sits on the digit row, not the caption row.
            val w = m.width(plan.badge, FieldFont.VALUE, badgePx)
            texts += Placed(plan.badge, FieldFont.VALUE, badgePx, x + w / 2, 0f)
            x += w + BADGE_GAP_DP * density
        }
        plan.cells.forEachIndexed { i, c ->
            if (i > 0) x += s.gapDp * density
            val w = maxOf(m.width(c.value, FieldFont.VALUE, valuePx), c.caption?.let { m.width(it, FieldFont.CAPTION, captionPx) } ?: 0f)
            if (c.caption != null) texts += Placed(c.caption, FieldFont.CAPTION, captionPx, x + w / 2, captionBaseline)
            texts += Placed(c.value, FieldFont.VALUE, valuePx, x + w / 2, 0f, c.dim)
            x += w
        }
        // The band the digits and capitals occupy, so "--" counts as tall as
        // digits, plus any ink beyond it (the p of a glued "mph").
        var top = if (plan.cells.any { it.caption != null }) captionBaseline - m.bandHeight(FieldFont.CAPTION) * captionPx else digitTop
        var bottom = 0f
        for (t in texts) {
            val ink = m.ink(t.text, t.font, t.sizePx)
            top = minOf(top, t.baseline + ink.top)
            bottom = maxOf(bottom, t.baseline + ink.bottom)
        }
        return Block(texts, x, top, bottom)
    }

    /** "ABS MPH" / "REL KPH": the speed mode and its unit in one short caption. */
    private fun shortSpeedCaption(showsAbsolute: Boolean, labels: Labels): String =
        (if (showsAbsolute) labels.speedAbs else labels.speedRel).substringBefore(' ') + " " + labels.speedUnit
}

package io.github.aryeh95.radarcount.datatypes

import io.github.aryeh95.radarcount.data.ComboActiveSetting
import io.github.aryeh95.radarcount.data.ComboIdleSetting
import io.github.aryeh95.radarcount.data.Settings

/**
 * Decides what the Radar combo field shows. Shared by the Glance field and
 * the live preview on the settings screen so both draw the same thing.
 *
 * The field has two states: idle (nothing on the radar) and active (a
 * vehicle is being tracked, held briefly after it vanishes so the layout
 * does not flicker). Each state has its own setting: the count alone,
 * speed and distance alone, or all three cells.
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
        val distUnit: String
    )

    /** One value cell: a caption above the value, as the Karoo's own fields draw them. */
    data class Cell(val caption: String?, val value: String)

    /** Font sizes (sp) and gap (dp) for drawing a [Plan] in a field of a given width. */
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
        labels: Labels
    ): Plan {
        val countText = count.toString()
        val speedDigits = speed?.toString() ?: "--"
        val distDigits = distance?.toString() ?: "--"
        val unitsInCaptions = settings.comboUnitsInCaptions
        val speedValue = if (unitsInCaptions || speed == null) speedDigits else speedDigits + labels.speedUnit.lowercase()
        val distValue = if (unitsInCaptions || distance == null) distDigits else distDigits + labels.distUnit.lowercase()
        val speedCaption = if (showsAbsolute) labels.speedAbs else labels.speedRel

        val all = Plan(
            cells = listOf(
                Cell(labels.count, countText),
                Cell(if (unitsInCaptions) shortSpeedCaption(showsAbsolute, labels) else speedCaption, speedValue),
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
    /** Width of one monospace digit as a fraction of the font size. Measured on the Karoo 3 at 0.6; 0.47 ellipsised the last cell. */
    private const val EM_PER_CHAR = 0.6f
    /**
     * Height of the header strip the field draws itself (icon and RADAR),
     * in the style of the Karoo's own. The Karoo's strip is turned off so
     * the whole tile is ours and nothing is hidden under it.
     */
    const val HEADER_DP = 20f

    /**
     * Sizes for drawing [plan] in a field [widthPx] wide whose standard
     * numeric font is [textSizeSp]. The value font takes a share of the
     * standard size that shrinks with the number of cells, then shrinks
     * further until every cell fits the width. Shared by the field and the
     * settings preview so they match exactly.
     */
    fun sizes(plan: Plan, widthPx: Int, heightPx: Int, textSizeSp: Int, density: Float, wideGrid: Boolean, headerDp: Float = HEADER_DP): Sizes {
        val n = plan.cells.size
        val gapDp = when {
            n == 1 -> 0
            wideGrid -> 10
            else -> 4
        }
        var chars = plan.cells.sumOf { c ->
            maxOf(c.value.length.toFloat(), (c.caption?.length ?: 0) * CAPTION_RATIO).toDouble()
        }.toFloat()
        // Run to the tile edge like the Karoo's own fields: 2 dp each side.
        var paddingDp = 4 + gapDp * (n - 1)
        if (plan.badge != null) {
            chars += plan.badge.length * BADGE_RATIO
            paddingDp += 4
        }
        val factor = when (n) { 1 -> 0.95f; 2 -> 0.85f; else -> 0.6f }
        val fitW = if (widthPx <= 0) Int.MAX_VALUE else ((widthPx - paddingDp * density) / (EM_PER_CHAR * chars * density)).toInt()
        // Height: the digit line plus, when captioned, the caption line,
        // each with the line padding Glance text carries. Measured on the
        // Karoo 3 half-width field (45 dp below the strip): with a caption
        // 26 sp fits and 28 sp clips the bottom of the digits, so the
        // captioned block is budgeted at 1.6 em and digits alone at 1.3.
        val captioned = plan.cells.any { it.caption != null }
        // Below our own header the area is ours and centred, so only the
        // glyphs need to fit, not the line boxes around them: digits are
        // about 0.75 em tall, a caption line about 0.3 em more, and the
        // line-box padding that overflows is empty space.
        val linesEm = 1.05f + (if (captioned) 0.3f else 0f)
        val fitH = if (heightPx <= 0) Int.MAX_VALUE else ((heightPx / density - headerDp - 2) / linesEm).toInt()
        val valueSp = minOf((textSizeSp * factor).toInt(), fitW, fitH).coerceAtLeast(12)
        val captionSp = (valueSp * CAPTION_RATIO).toInt().coerceIn(9, 16)
        val badgeSp = (valueSp * BADGE_RATIO).toInt().coerceAtLeast(10)
        return Sizes(valueSp, captionSp, badgeSp, gapDp)
    }

    /** "ABS MPH" / "REL KPH": the speed mode and its unit in one short caption. */
    private fun shortSpeedCaption(showsAbsolute: Boolean, labels: Labels): String =
        (if (showsAbsolute) labels.speedAbs else labels.speedRel).substringBefore(' ') + " " + labels.speedUnit
}

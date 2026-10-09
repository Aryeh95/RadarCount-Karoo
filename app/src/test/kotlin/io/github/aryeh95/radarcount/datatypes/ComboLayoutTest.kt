package io.github.aryeh95.radarcount.datatypes

import com.google.common.truth.Truth.assertThat
import io.github.aryeh95.radarcount.data.ComboActiveSetting
import io.github.aryeh95.radarcount.data.ComboIdleSetting
import io.github.aryeh95.radarcount.data.Settings
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ComboLayout")
class ComboLayoutTest {

    private val labels = ComboLayout.Labels(
        count = "COUNT", vehicles = "VEHICLES", speedRel = "REL SPEED", speedAbs = "ABS SPEED",
        dist = "DIST", speedUnit = "MPH", distUnit = "FT"
    )

    @Test
    @DisplayName("idle with count-only shows one captioned count")
    fun idleCountOnly() {
        val p = ComboLayout.plan(active = false, count = 12, speed = null, distance = null, showsAbsolute = false, settings = Settings(), labels = labels)
        assertThat(p.cells).containsExactly(ComboLayout.Cell("VEHICLES", "12"))
        assertThat(p.badge).isNull()
    }

    @Test
    @DisplayName("active with speed and distance puts units as captions and the count in the badge")
    fun activeSpeedDistance() {
        val p = ComboLayout.plan(active = true, count = 12, speed = 34, distance = 148, showsAbsolute = true, settings = Settings(), labels = labels)
        assertThat(p.cells).containsExactly(
            ComboLayout.Cell("MPH", "34"),
            ComboLayout.Cell("FT", "148")
        ).inOrder()
        assertThat(p.badge).isEqualTo("12")
    }

    @Test
    @DisplayName("unknown speed shows a dash without a unit glued to it")
    fun unknownSpeed() {
        val p = ComboLayout.plan(true, 12, null, 28, false, Settings(comboUnitsInCaptions = false), labels)
        assertThat(p.cells[0].value).isEqualTo("--")
        assertThat(p.cells[1].value).isEqualTo("28ft")
    }

    @Test
    @DisplayName("two big values in a half-width field come out much larger than three captioned cells")
    fun sizesTwoCellsBeatThree() {
        val two = ComboLayout.plan(true, 12, 34, 148, true, Settings(), labels)
        val three = ComboLayout.plan(true, 12, 34, 148, true, Settings(comboActive = ComboActiveSetting.ALL, comboUnitsInCaptions = false), labels)
        val a = ComboLayout.sizes(two, widthPx = 238, heightPx = 126, textSizeSp = 46, density = 1.875f, wideGrid = false)
        val b = ComboLayout.sizes(three, widthPx = 238, heightPx = 126, textSizeSp = 46, density = 1.875f, wideGrid = false)
        assertThat(a.valueSp).isAtLeast(28)
        assertThat(a.valueSp).isGreaterThan(b.valueSp + 4)
    }

    @Test
    @DisplayName("a short field caps the digits so caption and digits fit its height")
    fun sizesRespectHeight() {
        val two = ComboLayout.plan(true, 12, 34, 148, true, Settings(), labels)
        // The Karoo 3 half-width tile: 238x126 px at density 1.875, text 46 sp.
        val s = ComboLayout.sizes(two, widthPx = 238, heightPx = 126, textSizeSp = 46, density = 1.875f, wideGrid = false)
        // Width-bound here: 238 px less 2 dp each side over the badge, two cells and their gaps.
        assertThat(s.valueSp).isAtMost(34)
        assertThat(s.valueSp).isAtLeast(28)
        // On the half-width tile the width is the limit once the header goes, so test the growth on the full-width tile.
        val wide = ComboLayout.sizes(two, widthPx = 478, heightPx = 160, textSizeSp = 69, density = 1.875f, wideGrid = true)
        val wideNoHeader = ComboLayout.sizes(two, widthPx = 478, heightPx = 160, textSizeSp = 69, density = 1.875f, wideGrid = true, headerDp = 0f)
        assertThat(wideNoHeader.valueSp).isGreaterThan(wide.valueSp)
    }

    @Test
    @DisplayName("every layout fits inside the Karoo 2 and Karoo 3 half-width tiles, below the header")
    fun arrangementsFitTheTile() {
        val variants = listOf(
            Settings(),
            Settings(comboCaptions = false),
            Settings(comboHeader = false),
            Settings(comboUnitsInCaptions = false),
            Settings(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL),
            Settings(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL, comboUnitsInCaptions = false)
        )
        // Karoo 2 and Karoo 3 half width, and the full-width tile.
        val tiles = listOf(Triple(238, 148, 50), Triple(238, 126, 46), Triple(478, 160, 69))
        for (s in variants) for (active in listOf(false, true)) for ((w, h, text) in tiles) {
            val p = ComboLayout.plan(active, 48, if (active) 45 else null, if (active) 571 else null, false, s, labels)
            val headerDp = if (s.comboHeader) ComboLayout.HEADER_DP else 0f
            val a = ComboLayout.arrange(p, w, h, text, 1.875f, wideGrid = w > 300, headerDp = headerDp)
            assertThat(a.fits).isTrue()
            for (t in a.texts) {
                val ink = EstimatedText.ink(t.text, t.font, t.sizePx)
                val half = EstimatedText.width(t.text, t.font, t.sizePx) / 2
                assertThat(t.centerX - half).isAtLeast(0f)
                assertThat(t.centerX + half).isAtMost(w.toFloat())
                assertThat(t.baseline + ink.top).isAtLeast(a.headerPx)
                assertThat(t.baseline + ink.bottom).isAtMost(h.toFloat())
            }
        }
    }

    /**
     * Ink unlike [EstimatedText]'s, closer to a real font: dashes at mid
     * height, ascenders above the digit band, descenders, and glyphs a little
     * wider than their advance.
     */
    private object InkyText : TextMeasurer {
        override fun width(text: String, font: FieldFont, sizePx: Float): Float = text.length * 0.55f * sizePx

        override fun ink(text: String, font: FieldFont, sizePx: Float): Ink {
            val w = width(text, font, sizePx)
            if (text.all { it == '-' }) return Ink(0.05f * sizePx, -0.4f * sizePx, w - 0.05f * sizePx, -0.3f * sizePx)
            val top = if (text.any { it in "bdfhklt" }) -0.76f * sizePx else -0.7f * sizePx
            val bottom = if (text.any { it in "gjpqy" }) 0.2f * sizePx else 0f
            return Ink(-0.02f * sizePx, top, w + 0.02f * sizePx, bottom)
        }

        override fun bandHeight(font: FieldFont): Float = 0.7f
    }

    @Test
    @DisplayName("an unknown speed's dashes sit on the digits' baseline, and no glyph leaves the tile")
    fun bandPlacementWithRealInk() {
        val tiles = listOf(Triple(238, 148, 50), Triple(238, 126, 46), Triple(478, 160, 69))
        for ((w, h, text) in tiles) {
            for (s in listOf(Settings(), Settings(comboCaptions = false))) {
                val known = ComboLayout.arrange(ComboLayout.plan(true, 48, 45, 571, false, s, labels), w, h, text, 1.875f, w > 300, measurer = InkyText)
                val unknown = ComboLayout.arrange(ComboLayout.plan(true, 48, null, 571, false, s, labels), w, h, text, 1.875f, w > 300, measurer = InkyText)
                assertThat(unknown.sizes).isEqualTo(known.sizes)
                assertThat(unknown.texts.single { it.text == "--" }.baseline).isEqualTo(known.texts.single { it.text == "45" }.baseline)
            }
            val variants = listOf(
                Settings(),
                Settings(comboCaptions = false, comboUnitsInCaptions = false),
                Settings(comboHeader = false, comboUnitsInCaptions = false),
                Settings(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL, comboUnitsInCaptions = false)
            )
            for (s in variants) for (active in listOf(false, true)) {
                val p = ComboLayout.plan(active, 48, if (active) 45 else null, if (active) 571 else null, false, s, labels)
                val headerDp = if (s.comboHeader) ComboLayout.HEADER_DP else 0f
                val a = ComboLayout.arrange(p, w, h, text, 1.875f, wideGrid = w > 300, headerDp = headerDp, measurer = InkyText)
                assertThat(a.fits).isTrue()
                for (t in a.texts) {
                    val ink = InkyText.ink(t.text, t.font, t.sizePx)
                    val start = t.centerX - InkyText.width(t.text, t.font, t.sizePx) / 2
                    assertThat(start + ink.left).isAtLeast(0f)
                    assertThat(start + ink.right).isAtMost(w.toFloat())
                    assertThat(t.baseline + ink.top).isAtLeast(a.headerPx)
                    assertThat(t.baseline + ink.bottom).isAtMost(h.toFloat())
                }
            }
        }
    }

    @Test
    @DisplayName("a glued unit that descends lifts the digits to make room instead of shrinking them")
    fun descenderLiftsDigits() {
        val s = Settings(comboUnitsInCaptions = false, comboBadge = false)
        val known = ComboLayout.arrange(ComboLayout.plan(true, 48, 45, 571, false, s, labels.copy(speedUnit = "KMH")), 478, 160, 69, 1.875f, true)
        val glued = ComboLayout.arrange(ComboLayout.plan(true, 48, 45, 571, false, s, labels), 478, 160, 69, 1.875f, true)
        // "45kmh" and "45mph" are the same width; only the p descends.
        assertThat(glued.sizes).isEqualTo(known.sizes)
        val kph = known.texts.last { it.font == FieldFont.VALUE }.baseline
        val mph = glued.texts.last { it.font == FieldFont.VALUE }.baseline
        assertThat(mph).isLessThan(kph)
    }

    @Test
    @DisplayName("all-three layout with units in captions shortens the speed caption to mode plus unit")
    fun allThreeUnitsInCaptions() {
        val s = Settings(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL)
        val p = ComboLayout.plan(true, 12, 34, 148, showsAbsolute = false, settings = s, labels = labels)
        assertThat(p.cells.map { it.caption }).containsExactly("COUNT", "REL MPH", "FT").inOrder()
        assertThat(p.cells.map { it.value }).containsExactly("12", "34", "148").inOrder()
        assertThat(p.badge).isNull()
    }

    @Test
    @DisplayName("captions off drops the caption line and lets the digits grow")
    fun captionsOff() {
        val s = Settings(comboCaptions = false)
        val idle = ComboLayout.plan(false, 12, null, null, false, s, labels)
        assertThat(idle.cells).containsExactly(ComboLayout.Cell(null, "12"))
        val active = ComboLayout.plan(true, 12, 34, 148, false, s, labels)
        assertThat(active.cells.map { it.caption }).containsExactly(null, null)
        val withCap = ComboLayout.sizes(ComboLayout.plan(true, 12, 34, 148, false, Settings(), labels), 478, 160, 69, 1.875f, true)
        val noCap = ComboLayout.sizes(active, 478, 160, 69, 1.875f, true)
        assertThat(noCap.valueSp).isGreaterThan(withCap.valueSp)
    }

    @Test
    @DisplayName("badge can be turned off; units-after-digits keeps the old captions")
    fun noBadgeOldUnits() {
        val s = Settings(comboBadge = false, comboUnitsInCaptions = false)
        val p = ComboLayout.plan(true, 12, 34, 148, showsAbsolute = true, settings = s, labels = labels)
        assertThat(p.badge).isNull()
        assertThat(p.cells).containsExactly(
            ComboLayout.Cell("ABS SPEED", "34mph"),
            ComboLayout.Cell("DIST", "148ft")
        ).inOrder()
    }

    @Test
    @DisplayName("a held pass speed is dimmed and captioned PASSED with its unit; distance and badge stay live")
    fun passedSpeedDistance() {
        val p = ComboLayout.plan(true, 12, 34, 148, false, Settings(), labels, passed = true)
        assertThat(p.cells).containsExactly(
            ComboLayout.Cell("PASSED MPH", "34", dim = true),
            ComboLayout.Cell("FT", "148")
        ).inOrder()
        assertThat(p.badge).isEqualTo("12")
    }

    @Test
    @DisplayName("a held pass speed with units glued on is captioned PASSED")
    fun passedGlued() {
        val p = ComboLayout.plan(true, 12, 34, 148, true, Settings(comboUnitsInCaptions = false), labels, passed = true)
        assertThat(p.cells).containsExactly(
            ComboLayout.Cell("PASSED", "34mph", dim = true),
            ComboLayout.Cell("DIST", "148ft")
        ).inOrder()
    }

    @Test
    @DisplayName("captions off still caption a held pass speed PASSED, and only that cell")
    fun passedCaptionsOff() {
        val p = ComboLayout.plan(true, 12, 34, null, false, Settings(comboCaptions = false), labels, passed = true)
        assertThat(p.cells).containsExactly(
            ComboLayout.Cell("PASSED MPH", "34", dim = true),
            ComboLayout.Cell(null, "--")
        ).inOrder()
        val glued = ComboLayout.plan(true, 12, 34, null, false, Settings(comboCaptions = false, comboUnitsInCaptions = false), labels, passed = true)
        assertThat(glued.cells[0]).isEqualTo(ComboLayout.Cell("PASSED", "34mph", dim = true))
    }

    @Test
    @DisplayName("all three with a held pass speed: count and distance live, the distance never held")
    fun passedAll() {
        val s = Settings(comboIdle = ComboIdleSetting.ALL, comboActive = ComboActiveSetting.ALL)
        val clear = ComboLayout.plan(true, 13, 34, null, false, s, labels, passed = true)
        assertThat(clear.cells).containsExactly(
            ComboLayout.Cell("COUNT", "13"),
            ComboLayout.Cell("PASSED MPH", "34", dim = true),
            ComboLayout.Cell("FT", "--")
        ).inOrder()
        val nextCar = ComboLayout.plan(true, 13, 34, 197, false, s, labels, passed = true)
        assertThat(nextCar.cells[2]).isEqualTo(ComboLayout.Cell("FT", "197"))
        assertThat(nextCar.cells.count { it.dim }).isEqualTo(1)
    }

    /** The plan as it was before the pass hold, to check passed = false leaves every layout unchanged. */
    private fun planBeforePassHold(active: Boolean, count: Int, speed: Int?, distance: Int?, showsAbsolute: Boolean, settings: Settings, labels: ComboLayout.Labels): ComboLayout.Plan {
        val countText = count.toString()
        val speedDigits = speed?.toString() ?: "--"
        val distDigits = distance?.toString() ?: "--"
        val unitsInCaptions = settings.comboUnitsInCaptions
        val speedValue = if (unitsInCaptions || speed == null) speedDigits else speedDigits + labels.speedUnit.lowercase()
        val distValue = if (unitsInCaptions || distance == null) distDigits else distDigits + labels.distUnit.lowercase()
        val speedCaption = if (showsAbsolute) labels.speedAbs else labels.speedRel
        val shortSpeed = (if (showsAbsolute) labels.speedAbs else labels.speedRel).substringBefore(' ') + " " + labels.speedUnit
        val all = ComboLayout.Plan(
            cells = listOf(
                ComboLayout.Cell(labels.count, countText),
                ComboLayout.Cell(if (unitsInCaptions) shortSpeed else speedCaption, speedValue),
                ComboLayout.Cell(if (unitsInCaptions) labels.distUnit else labels.dist, distValue)
            ),
            badge = null
        )
        val cap = settings.comboCaptions
        return when {
            !active && settings.comboIdle == ComboIdleSetting.COUNT ->
                ComboLayout.Plan(listOf(ComboLayout.Cell(if (cap) labels.vehicles else null, countText)), badge = null)
            active && settings.comboActive == ComboActiveSetting.SPEED_DISTANCE ->
                ComboLayout.Plan(
                    cells = when {
                        !cap -> listOf(ComboLayout.Cell(null, speedValue), ComboLayout.Cell(null, distValue))
                        unitsInCaptions -> listOf(ComboLayout.Cell(labels.speedUnit, speedValue), ComboLayout.Cell(labels.distUnit, distValue))
                        else -> listOf(ComboLayout.Cell(speedCaption, speedValue), ComboLayout.Cell(labels.dist, distValue))
                    },
                    badge = if (settings.comboBadge) countText else null
                )
            else -> all
        }
    }

    private fun allSettings(): List<Settings> {
        val out = mutableListOf<Settings>()
        for (idle in ComboIdleSetting.entries) for (act in ComboActiveSetting.entries) for (badge in listOf(true, false))
            for (units in listOf(true, false)) for (cap in listOf(true, false)) for (header in listOf(true, false)) {
                out += Settings(comboIdle = idle, comboActive = act, comboBadge = badge, comboUnitsInCaptions = units, comboCaptions = cap, comboHeader = header)
            }
        return out
    }

    @Test
    @DisplayName("without a held pass every layout is exactly what it was before the pass hold")
    fun notPassedUnchanged() {
        for (s in allSettings()) for (active in listOf(false, true)) for (speed in listOf(null, 34, 144))
            for (distance in listOf(null, 148)) for (abs in listOf(false, true)) {
                val before = planBeforePassHold(active, 48, speed, distance, abs, s, labels)
                assertThat(ComboLayout.plan(active, 48, speed, distance, abs, s, labels, passed = false)).isEqualTo(before)
                assertThat(ComboLayout.plan(active, 48, speed, distance, abs, s, labels)).isEqualTo(before)
            }
    }

    @Test
    @DisplayName("held pass layouts with 3-digit speeds fit every Karoo 2 and Karoo 3 tile, with estimated and real-like ink")
    fun passedArrangementsFit() {
        // Karoo 2 and Karoo 3 half width, the Karoo 2 five-row half width, and the full-width tile.
        val tiles = listOf(Triple(238, 148, 50), Triple(238, 126, 46), Triple(238, 126, 41), Triple(478, 160, 69))
        // PASSED KPH and the wider PASSED MPH (the imperial default).
        val units = listOf(Pair("KPH", 144), Pair("MPH", 123))
        for (s in allSettings()) for ((w, h, text) in tiles) for (distance in listOf(null, 571)) for (m in listOf(EstimatedText, InkyText))
            for ((unit, speed) in units) {
                val p = ComboLayout.plan(true, 148, speed, distance, false, s, labels.copy(speedUnit = unit), passed = true)
                assertThat(p.cells.any { it.dim && it.caption!!.startsWith("PASSED") }).isTrue()
                val headerDp = if (s.comboHeader) ComboLayout.HEADER_DP else 0f
                val a = ComboLayout.arrange(p, w, h, text, 1.875f, wideGrid = w > 300, headerDp = headerDp, measurer = m)
                assertThat(a.fits).isTrue()
                assertThat(a.texts.filter { it.dim }.map { it.text }).containsExactly(if (s.comboUnitsInCaptions) "$speed" else "$speed${unit.lowercase()}")
                for (t in a.texts) {
                    val ink = m.ink(t.text, t.font, t.sizePx)
                    val start = t.centerX - m.width(t.text, t.font, t.sizePx) / 2
                    assertThat(start + ink.left).isAtLeast(0f)
                    assertThat(start + ink.right).isAtMost(w.toFloat())
                    assertThat(t.baseline + ink.top).isAtLeast(a.headerPx)
                    assertThat(t.baseline + ink.bottom).isAtMost(h.toFloat())
                }
            }
    }
}

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
        // 67 dp minus our 20 dp header and 2 slack, over 1.35 em of glyphs
        assertThat(s.valueSp).isAtMost(34)
        assertThat(s.valueSp).isAtLeast(28)
        // On the half-width tile the width is the limit once the header goes, so test the growth on the full-width tile.
        val wide = ComboLayout.sizes(two, widthPx = 478, heightPx = 160, textSizeSp = 69, density = 1.875f, wideGrid = true)
        val wideNoHeader = ComboLayout.sizes(two, widthPx = 478, heightPx = 160, textSizeSp = 69, density = 1.875f, wideGrid = true, headerDp = 0f)
        assertThat(wideNoHeader.valueSp).isGreaterThan(wide.valueSp)
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
}

package io.github.aryeh95.radarcount.datatypes

import com.google.common.truth.Truth.assertThat
import io.github.aryeh95.radarcount.data.PassHoldSetting
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.engine.FitRecordWriter
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.github.aryeh95.radarcount.engine.TargetTracker
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PassHold")
class PassHoldTest {

    private val at = 1_000_000L
    private val pass = TargetTracker.Pass(seq = 3, atMs = at, closingMps = 20.0, riderMps = 7.0, cars = 1)

    @Test
    @DisplayName("off holds nothing")
    fun offGivesNull() {
        assertThat(PassHold.held(pass, PassHoldSetting.OFF, liveKnown = false, nowMs = at)).isNull()
    }

    @Test
    @DisplayName("5 s holds from the decision up to, not including, 5 s after it")
    fun holdsForItsLength() {
        assertThat(PassHold.held(pass, PassHoldSetting.S5, false, at)).isEqualTo(pass)
        assertThat(PassHold.held(pass, PassHoldSetting.S5, false, at + 4_999)).isEqualTo(pass)
        assertThat(PassHold.held(pass, PassHoldSetting.S5, false, at + 5_000)).isNull()
        assertThat(PassHold.held(pass, PassHoldSetting.S3, false, at + 3_000)).isNull()
        assertThat(PassHold.held(pass, PassHoldSetting.S10, false, at + 9_999)).isEqualTo(pass)
    }

    @Test
    @DisplayName("a clock that went back before the decision counts as expired")
    fun clockBackwardsExpires() {
        assertThat(PassHold.held(pass, PassHoldSetting.S10, false, at - 1)).isNull()
    }

    @Test
    @DisplayName("a live car with a measured speed always wins")
    fun liveKnownWins() {
        assertThat(PassHold.held(pass, PassHoldSetting.S10, liveKnown = true, nowMs = at + 1_000)).isNull()
    }

    @Test
    @DisplayName("a pass whose speed was never measured is not held")
    fun unknownSpeedNotHeld() {
        assertThat(PassHold.held(pass.copy(closingMps = null), PassHoldSetting.S10, false, at)).isNull()
        assertThat(PassHold.held(null, PassHoldSetting.S10, false, at)).isNull()
    }

    @Test
    @DisplayName("relative ignores the rider; absolute adds the rider's speed at the pass")
    fun shownSpeed() {
        // 20 m/s = 72 km/h; 7 m/s = 25.2 km/h.
        assertThat(PassHold.shownSpeed(pass, imperial = false, absolute = false)).isEqualTo(72)
        assertThat(PassHold.shownSpeed(pass, imperial = false, absolute = true)).isEqualTo(72 + 25)
        // A known 0 closing speed still adds the rider's speed, as the live value does.
        assertThat(PassHold.shownSpeed(pass.copy(closingMps = 0.0), imperial = false, absolute = true)).isEqualTo(25)
        assertThat(PassHold.shownSpeed(pass.copy(closingMps = 0.0), imperial = false, absolute = false)).isEqualTo(0)
    }

    @Test
    @DisplayName("imperial rounds each part as the live value does")
    fun imperialRounding() {
        val p = pass.copy(closingMps = 13.4, riderMps = 6.9)
        val rel = FitRecordWriter.toUserSpeedUnits(13.4, true)
        val rider = FitRecordWriter.toUserSpeedUnits(6.9, true)
        assertThat(PassHold.shownSpeed(p, imperial = true, absolute = false)).isEqualTo(rel)
        assertThat(PassHold.shownSpeed(p, imperial = true, absolute = true)).isEqualTo(rel + rider)
        assertThat(rel).isEqualTo(30)
        assertThat(rider).isEqualTo(15)
    }

    private val clear = FieldDataType.PREVIEW_INPUT.copy(state = RadarStatus.Live.CLEAR, closingSpeedMps = null, lastPass = pass)

    @Test
    @DisplayName("the Vehicle Speed field's held pass, part of its redraw key, ends with the hold")
    fun speedKeyChangesAtExpiry() {
        val input = clear.copy(settings = Settings(speedPassHold = PassHoldSetting.S5))
        assertThat(ApproachSpeedDataType.held(input, at + 4_000)?.seq).isEqualTo(3)
        assertThat(ApproachSpeedDataType.held(input, at + 5_000)?.seq).isNull()
        // Its own setting only: the Radar field's is off.
        assertThat(ComboDataType.held(input, at + 4_000)).isNull()
    }

    @Test
    @DisplayName("the Radar field's held pass, part of its redraw key, ends with the hold")
    fun comboKeyChangesAtExpiry() {
        val input = clear.copy(settings = Settings(comboPassHold = PassHoldSetting.S10))
        assertThat(ComboDataType.held(input, at + 9_000)?.seq).isEqualTo(3)
        assertThat(ComboDataType.held(input, at + 10_000)?.seq).isNull()
        assertThat(ApproachSpeedDataType.held(input, at + 9_000)).isNull()
    }

    @Test
    @DisplayName("a held pass stays while the next car has no measured speed, and ends when it has one")
    fun nextCarWithoutSpeedKeepsHold() {
        val s = Settings(speedPassHold = PassHoldSetting.S10, comboPassHold = PassHoldSetting.S10)
        val next = clear.copy(state = RadarStatus.Live(level = 1, vehicles = 1, nearestM = 60), settings = s)
        assertThat(ApproachSpeedDataType.held(next, at + 1_000)).isEqualTo(pass)
        assertThat(ComboDataType.held(next, at + 1_000)).isEqualTo(pass)
        val measured = next.copy(closingSpeedMps = 9.0)
        assertThat(ApproachSpeedDataType.held(measured, at + 1_000)).isNull()
        assertThat(ComboDataType.held(measured, at + 1_000)).isNull()
    }
}

package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("VehiclePassCounter")
class VehiclePassCounterTest {

    private lateinit var counter: VehiclePassCounter

    @BeforeEach
    fun setUp() {
        counter = VehiclePassCounter(closeThresholdM = 20, closingThresholdM = 60)
    }

    @Test
    @DisplayName("starts at zero")
    fun startsAtZero() {
        assertThat(counter.total).isEqualTo(0)
        assertThat(counter.lap).isEqualTo(0)
    }

    @Test
    @DisplayName("counts a slow pass that comes within 20 m")
    fun slowPass() {
        counter.update(1, 90)
        counter.update(1, 40)
        counter.update(1, 12)
        assertThat(counter.update(0, 0)).isEqualTo(1)
        assertThat(counter.total).isEqualTo(1)
        assertThat(counter.lap).isEqualTo(1)
    }

    @Test
    @DisplayName("counts a fast pass that jumps from 45 m to gone while closing")
    fun fastPass() {
        counter.update(1, 120)
        counter.update(1, 85)
        counter.update(1, 45)
        assertThat(counter.update(0, 0)).isEqualTo(1)
    }

    @Test
    @DisplayName("does not count a vehicle that vanishes far away even if closing")
    fun farVehicleNotCounted() {
        counter.update(1, 140)
        counter.update(1, 110)
        assertThat(counter.update(0, 0)).isEqualTo(0)
        assertThat(counter.total).isEqualTo(0)
    }

    @Test
    @DisplayName("does not count a vehicle that was receding when it vanished")
    fun recedingNotCounted() {
        counter.update(1, 30)
        counter.update(1, 45)
        counter.update(1, 55)
        assertThat(counter.update(0, 0)).isEqualTo(0)
    }

    @Test
    @DisplayName("counts a vehicle that came close then drifted back before vanishing")
    fun cameCloseThenReceded() {
        counter.update(1, 15)
        counter.update(1, 25)
        counter.update(1, 40)
        assertThat(counter.update(0, 0)).isEqualTo(1)
    }

    @Test
    @DisplayName("counts several vehicles disappearing at once")
    fun multipleAtOnce() {
        counter.update(3, 40)
        counter.update(3, 15)
        assertThat(counter.update(1, 50)).isEqualTo(2)
        assertThat(counter.total).isEqualTo(2)
    }

    @Test
    @DisplayName("a single sample then gone is not counted (no closing evidence)")
    fun singleSampleNotCounted() {
        counter.update(1, 50)
        assertThat(counter.update(0, 0)).isEqualTo(0)
    }

    @Test
    @DisplayName("nothing counted while count is rising or flat")
    fun risingOrFlat() {
        counter.update(1, 10)
        counter.update(2, 8)
        counter.update(2, 5)
        assertThat(counter.total).isEqualTo(0)
    }

    @Test
    @DisplayName("resetLap clears lap but keeps total")
    fun resetLap() {
        counter.update(1, 30)
        counter.update(1, 10)
        counter.update(0, 0)
        counter.resetLap()
        assertThat(counter.lap).isEqualTo(0)
        assertThat(counter.total).isEqualTo(1)
        counter.update(1, 30)
        counter.update(1, 10)
        counter.update(0, 0)
        assertThat(counter.lap).isEqualTo(1)
        assertThat(counter.total).isEqualTo(2)
    }

    @Test
    @DisplayName("clearTracking prevents counting vehicles in view before a disconnect")
    fun clearTracking() {
        counter.update(2, 30)
        counter.update(2, 10)
        counter.clearTracking()
        assertThat(counter.update(0, 0)).isEqualTo(0)
        assertThat(counter.total).isEqualTo(0)
    }

    @Test
    @DisplayName("reset clears everything")
    fun reset() {
        counter.update(1, 30)
        counter.update(1, 10)
        counter.update(0, 0)
        counter.reset()
        assertThat(counter.total).isEqualTo(0)
        assertThat(counter.lap).isEqualTo(0)
    }

    @Test
    @DisplayName("threat with unknown range (0 m) never arms the counter")
    fun zeroRangeDoesNotArm() {
        counter.update(1, 0)
        counter.update(1, 0)
        assertThat(counter.update(0, 0)).isEqualTo(0)
    }
}

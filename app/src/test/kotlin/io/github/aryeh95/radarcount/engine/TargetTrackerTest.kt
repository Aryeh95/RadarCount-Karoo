package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TargetTracker")
class TargetTrackerTest {

    private lateinit var tracker: TargetTracker
    private var now = 0L

    @BeforeEach
    fun setUp() {
        tracker = TargetTracker()
        now = 100_000L
    }

    /** Feed one packet per second; returns passes counted. */
    private fun feed(vararg ranges: Int, dtMs: Long = 1000L): Int {
        now += dtMs
        return tracker.update(ranges.toList(), now)
    }

    /** Feed an empty packet until the lost timeout has elapsed. */
    private fun gone(): Int {
        var passed = 0
        repeat(3) { passed += feed() }
        return passed
    }

    @Test
    @DisplayName("clean pass 84 m down to 3 m then gone counts once")
    fun cleanPass() {
        for (r in listOf(84, 68, 59, 46, 34, 25, 12, 3)) assertThat(feed(r)).isEqualTo(0)
        assertThat(gone()).isEqualTo(1)
        assertThat(tracker.activeCount).isEqualTo(0)
    }

    @Test
    @DisplayName("a threat-without-ranges packet before the drop does not lose the pass")
    fun noRangePacketBeforeDrop() {
        for (r in listOf(46, 34, 25, 12)) feed(r)
        feed() // Karoo sends threat but no target ranges
        feed(3, dtMs = 200) // burst packet with the range again
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("fast pass lost between 45 m and gone still counts when closing")
    fun fastPass() {
        for (r in listOf(120, 85, 45)) feed(r)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("vehicle that vanishes far away while closing is not counted")
    fun farVehicle() {
        for (r in listOf(140, 110)) feed(r)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("vehicle that was receding when it vanished is not counted")
    fun receding() {
        for (r in listOf(30, 45, 55)) feed(r)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("target-count jitter around one car counts one vehicle")
    fun jitter() {
        // one car at ~30 m briefly splits into 3 targets, then passes
        feed(34)
        feed(31, 31, 28)
        feed(31, 28, 28)
        feed(28)
        feed(25)
        feed(21)
        feed(15)
        feed(6)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("two cars in a line both count")
    fun twoCars() {
        var passed = 0
        passed += feed(60, 90)
        passed += feed(45, 75)
        passed += feed(30, 60)
        passed += feed(15, 45)
        passed += feed(3, 30)
        passed += feed(15)      // first car gone, second still closing
        passed += feed(3)
        passed += gone()
        assertThat(passed).isEqualTo(2)
    }

    @Test
    @DisplayName("single-sample blip is ignored")
    fun blip() {
        feed(15)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("burst packets a few ms apart do not produce absurd speeds")
    fun burstSpeed() {
        feed(34)
        feed(31, dtMs = 1000)
        feed(31, dtMs = 5)
        feed(28, dtMs = 5)
        // 6 m over ~1 s, but history spans only 1.01 s -> below min span until next
        feed(25, dtMs = 1000)
        val v = tracker.nearestClosingSpeedMps()
        assertThat(v).isAtLeast(2.0)
        assertThat(v).isAtMost(6.0)
    }

    @Test
    @DisplayName("closing speed of a steady approach is about right")
    fun steadySpeed() {
        for (r in listOf(84, 72, 60, 48, 36)) feed(r) // 12 m/s
        assertThat(tracker.nearestClosingSpeedMps()).isWithin(1.0).of(12.0)
    }

    @Test
    @DisplayName("closing speed is zero with less than 1 s of history")
    fun speedNeedsHistory() {
        feed(84)
        assertThat(tracker.nearestClosingSpeedMps()).isEqualTo(0.0)
    }

    @Test
    @DisplayName("clear drops tracks without counting")
    fun clear() {
        for (r in listOf(30, 15, 5)) feed(r)
        tracker.clear()
        assertThat(gone()).isEqualTo(0)
        assertThat(tracker.activeCount).isEqualTo(0)
    }

    @Test
    @DisplayName("nearest range follows the closest track")
    fun nearest() {
        feed(60, 90)
        assertThat(tracker.nearestRangeM()).isEqualTo(60)
        feed(45, 75)
        assertThat(tracker.nearestRangeM()).isEqualTo(45)
    }
}

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
    private fun feed(vararg ranges: Int, dtMs: Long = 1000L, threat: Int = 0): Int {
        now += dtMs
        return tracker.update(ranges.toList(), now, threat)
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
    @DisplayName("a threat-without-ranges packet before the drop counts the car exactly once")
    fun noRangePacketBeforeDrop() {
        for (r in listOf(46, 34, 25, 12)) feed(r)
        var passed = feed() // Karoo sends threat but no target ranges
        passed += feed(3, dtMs = 200) // burst packet with the range again
        passed += gone()
        assertThat(passed).isEqualTo(1)
    }

    @Test
    @DisplayName("a close car is counted on the first packet it is missing from")
    fun closeCarCountsImmediately() {
        for (r in listOf(46, 34, 25, 12, 6)) feed(r)
        assertThat(feed()).isEqualTo(1)
        assertThat(tracker.activeCount).isEqualTo(0)
        assertThat(tracker.nearestRangeM()).isEqualTo(0)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("a burst packet a few ms after the car vanished does not count it early")
    fun burstDoesNotCountEarly() {
        for (r in listOf(46, 34, 25, 12)) feed(r)
        assertThat(feed(dtMs = 5)).isEqualTo(0)
        assertThat(feed(9, dtMs = 5)).isEqualTo(0)
        assertThat(feed(3)).isEqualTo(0)
        assertThat(feed()).isEqualTo(1)
    }

    @Test
    @DisplayName("a counted car that reappears alongside after a dropout is not counted twice")
    fun ghostAbsorbsDropout() {
        for (r in listOf(46, 34, 25, 12, 6)) feed(r)
        var passed = feed()      // dropout: counted now
        passed += feed(3)        // radar sees it again, right where it vanished
        passed += feed(3)
        passed += gone()
        assertThat(passed).isEqualTo(1)
    }

    @Test
    @DisplayName("a new car appearing behind a counted one starts its own track")
    fun newCarAfterGhost() {
        for (r in listOf(46, 34, 25, 12, 6)) feed(r)
        var passed = feed()      // first car counted
        passed += feed(40)       // second car, well behind the ghost
        passed += feed(25)
        passed += feed(12)
        passed += feed(5)
        passed += gone()
        assertThat(passed).isEqualTo(2)
    }

    @Test
    @DisplayName("a far car still waits the full lost timeout before counting")
    fun farCarStillWaits() {
        for (r in listOf(120, 85, 45)) feed(r)
        assertThat(feed()).isEqualTo(0)
        assertThat(feed()).isEqualTo(1)
    }

    @Test
    @DisplayName("closing speed freezes at the approach value inside 10 m")
    fun speedFreezesClose() {
        for (r in listOf(84, 72, 60, 48, 36, 24, 12)) feed(r) // 12 m/s
        val approach = tracker.nearestClosingSpeedMps()
        feed(3)   // radar quantisation: 12 -> 3 would read as 9 m/s
        feed(3)   // and 3 -> 3 as a stall
        assertThat(tracker.nearestClosingSpeedMps()).isEqualTo(approach)
        assertThat(approach).isWithin(1.0).of(12.0)
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
    @DisplayName("closing speed is unknown with less than 1 s of history")
    fun speedNeedsHistory() {
        feed(84)
        assertThat(tracker.nearestClosingSpeedMps()).isNull()
    }

    @Test
    @DisplayName("closing speed is unknown when nothing is tracked")
    fun speedUnknownWithNoTarget() {
        assertThat(tracker.nearestClosingSpeedMps()).isNull()
    }

    @Test
    @DisplayName("a car holding station reads a real zero, not unknown")
    fun holdingStationReadsZero() {
        for (r in listOf(30, 30, 30, 30)) feed(r, threat = 1)
        assertThat(tracker.nearestClosingSpeedMps()).isEqualTo(0.0)
    }

    @Test
    @DisplayName("a receding car reads a real zero, not unknown")
    fun recedingReadsZero() {
        for (r in listOf(30, 40, 50, 60)) feed(r, threat = 1)
        assertThat(tracker.nearestClosingSpeedMps()).isEqualTo(0.0)
    }

    @Test
    @DisplayName("the estimate becomes known on the second sample a second later")
    fun estimateKnownAfterOneSecond() {
        feed(84)
        assertThat(tracker.nearestClosingSpeedMps()).isNull()
        feed(72)
        assertThat(tracker.nearestClosingSpeedMps()).isNotNull()
    }

    @Test
    @DisplayName("a car that follows at the rider's speed and then drops off is not a pass")
    fun followerNotCounted() {
        // From a ride: 31 m down to 12 m over 12 s at ~1 m/s, threat level 1 throughout, then gone
        for (r in listOf(31, 28, 28, 25, 21, 18, 18, 15, 15, 12, 12, 12)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("a follower that gets to 6 m and drops off is still not a pass")
    fun closeFollowerNotCounted() {
        for (r in listOf(21, 18, 15, 15, 15, 12, 9, 6, 9, 6, 6)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("a slow car the radar flagged as approaching fast counts")
    fun slowButFlagged() {
        for (r in listOf(21, 18, 15, 15, 12, 12, 9)) feed(r, threat = 2)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a slow car seen alongside at 3 m counts")
    fun slowAlongside() {
        for (r in listOf(12, 12, 9, 6, 6, 3)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(1)
    }

    /** Rider heading sample at the current time. */
    private fun heading(deg: Double) = tracker.updateHeading(deg, now)

    @Test
    @DisplayName("a car that vanishes as the rider turns off the road is not a pass")
    fun vanishesAtTurn() {
        // From a ride: car closes 15 m to 3 m at 2 m/s while the rider brakes and turns right
        heading(90.0)
        for ((i, r) in listOf(15, 15, 12, 9, 6, 3).withIndex()) {
            feed(r, threat = 1)
            heading(90.0 + i * 5)   // gentle drift while braking
        }
        heading(150.0); feed(); heading(175.0); feed(); heading(180.0)
        assertThat(tracker.turnedSince(now - 3000)).isTrue()
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("a brief fast target right after a turn is a car crossing the cone, not a pass")
    fun crossingAfterTurn() {
        heading(90.0); feed()
        heading(135.0); feed(); heading(180.0); feed()   // the turn
        feed(); feed(); feed()
        feed(21, threat = 2); feed(3, threat = 2)         // two samples, 18 m/s closing
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("a car on the new road that passes as the rider completes a turn counts")
    fun passWhileMergingOntoBusyRoad() {
        heading(90.0); feed()
        heading(120.0); feed()
        heading(150.0); feed(84, threat = 2)     // car on the new road appears mid-turn
        heading(175.0); feed(59, threat = 2)     // turn detected here
        heading(180.0); feed(34, threat = 2)
        heading(180.0); feed(12, threat = 2)
        heading(180.0); feed(3, threat = 2)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a fast car first seen at normal range right after a turn counts even if brief")
    fun fastPassRightAfterTurn() {
        heading(90.0); feed(); heading(180.0); feed()
        feed(); feed()
        feed(96, threat = 2); feed(46, threat = 2); feed(6, threat = 2)   // three samples from 96 m
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a real pass on a straight road still counts with heading fed")
    fun straightRoadPassWithHeading() {
        for (r in listOf(84, 68, 59, 46, 34, 25, 12, 3)) { feed(r, threat = 2); heading(90.0) }
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a pass well after a turn counts")
    fun passAfterTurn() {
        heading(90.0); feed(); heading(180.0); feed()
        repeat(15) { feed(); heading(180.0) }
        for (r in listOf(84, 68, 59, 46, 34, 25, 12, 3)) { feed(r, threat = 2); heading(180.0) }
        assertThat(gone()).isEqualTo(1)
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

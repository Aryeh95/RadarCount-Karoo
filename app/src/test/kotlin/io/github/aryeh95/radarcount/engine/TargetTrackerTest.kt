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
    @DisplayName("a car that vanishes at 12 m waits the full lost timeout before it is decided")
    fun farCarStillWaits() {
        for (r in listOf(120, 85, 45, 12)) feed(r)
        assertThat(feed()).isEqualTo(0)
        assertThat(tracker.rejectedNotPass).isEqualTo(0)
        assertThat(feed()).isEqualTo(0)
        assertThat(tracker.rejectedNotPass).isEqualTo(1)
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
    @DisplayName("a fast car lost at 37 m as the rider turns off is not a pass")
    fun fastCarLostFarOutNotCounted() {
        // From a side-by-side ride: closed from 93 m to 37 m in twelve seconds
        // and vanished with two other targets when the rider swung 34 degrees
        // off the road. It never came alongside; 0.2.16 counted it.
        for (r in listOf(93, 90, 87, 81, 78, 78, 68, 65, 59, 56, 50, 43, 37, 37)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(0)
        assertThat(tracker.rejectedNotPass).isEqualTo(1)
    }

    @Test
    @DisplayName("a car that stops in the queue at 15 m behind a stopped truck is not a pass")
    fun queuedCarBehindTruckNotCounted() {
        // From a side-by-side ride: truck held 12 m while the rider slowed to a
        // give-way; the car behind it closed 28, 21, 15 and stopped. Neither
        // passed. 0.2.16 counted the car through the 20 m threshold.
        for (r in listOf(28, 21, 15)) feed(12, r, threat = 2)
        var passed = feed(12)           // car stopped: Doppler-invisible
        passed += feed(12)
        for (r in listOf(15, 21)) passed += feed(r)   // rider turns off, truck recedes
        passed += gone()
        assertThat(passed).isEqualTo(0)
    }

    @Test
    @DisplayName("a car that follows at 28 to 37 m for eight seconds and turns off is not a pass")
    fun followerThatTurnsOffNotCounted() {
        // From a side-by-side ride: closed from 71 m to 28 m, hung there, gone.
        for (r in listOf(71, 71, 62, 59, 53, 46, 43, 37, 28, 31, 28, 28, 31, 34, 37, 34)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("a long vehicle whose reading flickers 3, 6, 3 m counts once")
    fun longVehicleFlickerCountsOnce() {
        // From a side-by-side ride: a semi at 6 m for four seconds counted twice
        // in 0.2.16 because a reading one bin farther back was refused by the
        // ghost and started a new track.
        for (r in listOf(31, 28, 21, 18, 12, 9, 6)) feed(r, threat = 2)
        var passed = feed(dtMs = 400)          // body gap
        passed += feed(dtMs = 400)             // counted here (missing > 700 ms)
        assertThat(passed).isEqualTo(1)
        passed += feed(3, dtMs = 300)          // reflection back, nearer
        passed += feed(dtMs = 300)
        passed += feed(6, dtMs = 200)          // one bin farther back, 500 ms after last echo
        passed += feed(6, dtMs = 300)
        passed += feed(3, dtMs = 300)
        passed += gone()
        assertThat(passed).isEqualTo(1)
    }

    @Test
    @DisplayName("a car first seen one bin behind a ghost after a longer gap is the next car")
    fun tailgaterAfterGapCounted() {
        // The flicker allowance must not swallow a queued car that pulls out
        // right behind a counted one a second later.
        for (r in listOf(18, 12, 6, 3)) feed(r, threat = 2)
        var passed = feed()                    // first car counted, ghost at 3 m
        assertThat(passed).isEqualTo(1)
        passed += feed(6, dtMs = 1000)         // 2 s after last echo: new car
        passed += feed(3)
        passed += gone()
        assertThat(passed).isEqualTo(2)
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
    @DisplayName("a slow car that works its way down to 6 m and drops off has passed")
    fun slowCarToAlongsideCounted() {
        // From a ride, and the reason this expectation was inverted: the rider
        // confirmed a car that sat at 12-6 m at his own speed for several
        // seconds and then overtook. On a Doppler radar a pass at low relative
        // speed carries no threat flag and no closing speed; the one thing it
        // cannot fake is getting alongside.
        for (r in listOf(21, 18, 15, 15, 15, 12, 9, 6, 9, 6, 6)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a car that settles at 9 m and drops off counts, as it does on mybiketraffic.com")
    fun nineMetreVanishCounted() {
        // From a ride: closes steadily to 9 m, holds it, then goes quiet. It is
        // either a wide pass leaving the beam or a follower matching speed; the
        // radar cannot tell, and the site counts a run ending under ten metres,
        // so the device does too. Accepted as the false-positive side of the
        // trade.
        for (r in listOf(25, 25, 21, 18, 15, 15, 12, 12, 9, 9)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a car that appears alongside for a single sample has passed")
    fun singleSampleAlongsideCounted() {
        // From a rush-hour ride: the lead car of a queue sits at the rider's
        // speed, invisible to Doppler, pulls out, and is seen once at 3 m
        // before leaving the beam. The car behind it was counted; this one
        // was not.
        feed(3, threat = 2)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a car whose first echo is just outside alongside but gets inside on one sample counts")
    fun briefCarReachingAlongsideCounted() {
        // From a ride: first echo in the 12 m bin, the next in the 6 m bin a
        // fraction of a second later, then out of the beam. One sample by the
        // 250 ms rule. 0.2.15 waived the sample minimum only on the first
        // range and missed it; the Garmin counted it.
        feed(12, threat = 2)
        feed(6, dtMs = 200L, threat = 2)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a counted car's ghost does not swallow the next car in the queue")
    fun ghostDoesNotTakeFollowingCar() {
        // From the same ride: a car counted at 3 m, then a target at 9 m for
        // two seconds, then gone. The 9 m readings belong to the next car,
        // not to a dropout of the first.
        for (r in listOf(18, 12, 6, 3)) feed(r, threat = 2)
        var passed = feed()                      // first car gone: counted, ghost lingers
        assertThat(passed).isEqualTo(1)
        for (r in listOf(9, 9)) passed += feed(r, threat = 1)
        passed += gone()
        assertThat(passed).isEqualTo(2)
    }

    @Test
    @DisplayName("a car that holds 12 m for four seconds and drops off is not a pass")
    fun twelveMetreFollowerNotCounted() {
        // From a ride: after this track vanished the radar saw nothing at all
        // for eleven seconds, then a car closed from 25 m and was counted.
        // Counting the plateau as well would count the same traffic twice.
        for (r in listOf(46, 40, 37, 31, 28, 25, 21, 18, 18, 12, 12, 12, 12)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(0)
    }

    @Test
    @DisplayName("a slow car the radar flagged as approaching fast counts once it reaches 9 m")
    fun slowButFlagged() {
        for (r in listOf(21, 18, 15, 15, 12, 12, 9)) feed(r, threat = 2)
        assertThat(gone()).isEqualTo(1)
    }

    @Test
    @DisplayName("a flagged car that only reaches 12 m before vanishing does not count")
    fun flaggedButNotAlongsideNotCounted() {
        for (r in listOf(21, 18, 15, 15, 12, 12)) feed(r, threat = 2)
        assertThat(gone()).isEqualTo(0)
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
    @DisplayName("a turn is detected when headings arrive at irregular intervals")
    fun turnDetectedWithIrregularHeadings() {
        // The device feeds headings about once a second but never on an exact
        // boundary. 0.2.15 judged the window full only when the oldest kept
        // sample was exactly the window's age, which never happened on the
        // road, so no turn was ever detected and the turn vetoes were dead.
        var t = 0L
        repeat(8) { t += 1_100L; tracker.updateHeading(90.0, t) }
        t += 1_100L; tracker.updateHeading(135.0, t)
        t += 1_100L; tracker.updateHeading(180.0, t)
        assertThat(tracker.turnedSince(0L)).isTrue()
    }

    @Test
    @DisplayName("a brief fast target right after a turn is a car crossing the cone, not a pass")
    fun crossingAfterTurn() {
        repeat(7) { heading(90.0); feed() }               // heading window full: steady road before the turn
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

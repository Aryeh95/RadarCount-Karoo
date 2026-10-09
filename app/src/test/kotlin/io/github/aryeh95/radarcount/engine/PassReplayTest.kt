package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import io.github.aryeh95.radarcount.data.PassHoldSetting
import io.github.aryeh95.radarcount.datatypes.PassHold
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Replays ride sequences through [TargetTracker] and applies the fields'
 * hold rule ([PassHold.held], 10 s) to [TargetTracker.lastPass] after every
 * packet, to check what a rider would see after each pass.
 */
@DisplayName("Pass replay")
class PassReplayTest {

    private lateinit var tracker: TargetTracker
    private var now = 0L

    /** What a field would show after one packet. */
    private data class Frame(val liveKnown: Boolean, val held: TargetTracker.Pass?)
    private val frames = ArrayList<Frame>()

    @BeforeEach
    fun setUp() {
        tracker = TargetTracker()
        now = 100_000L
        frames.clear()
    }

    /** Feed one packet and record what a field would show; returns passes counted. */
    private fun feed(vararg ranges: Int, dtMs: Long = 1000L, threat: Int = 0): Int {
        now += dtMs
        val passed = tracker.update(ranges.toList(), now, threat)
        // The field's live value: a target on the radar (Threat) with a measured speed.
        val liveKnown = (ranges.isNotEmpty() || threat > 0) && tracker.nearestClosingSpeedMps() != null
        if (ranges.isNotEmpty() && tracker.nearestClosingSpeedMps() != null) {
            assertThat(tracker.lastPass).isNull()
        }
        frames.add(Frame(liveKnown, PassHold.held(tracker.lastPass, PassHoldSetting.S10, liveKnown, now)))
        return passed
    }

    private fun gone(): Int {
        var passed = 0
        repeat(3) { passed += feed() }
        return passed
    }

    private val shown: TargetTracker.Pass? get() = frames.last().held

    private fun assertNeverHeldWithLiveSpeed() {
        assertThat(frames.none { it.liveKnown && it.held != null }).isTrue()
    }

    companion object {
        private val HOLD_MS = PassHoldSetting.S10.seconds * 1000L
    }

    @Test
    @DisplayName("tailgater after a gap: A is held until the follower passes with no measured speed")
    fun tailgaterAfterGap() {
        for (r in listOf(18, 12, 6, 3)) feed(r, threat = 2)
        assertThat(feed()).isEqualTo(1)                 // A decided
        val a = shown!!
        assertThat(a.closingMps!!).isWithin(1.0).of(6.0)
        feed(6)                                         // follower, no speed yet
        assertThat(shown).isEqualTo(a)
        feed(3)
        assertThat(shown).isEqualTo(a)
        assertThat(gone()).isEqualTo(1)                 // follower decided after 1 s of history
        assertThat(tracker.lastPass!!.seq).isEqualTo(2)
        assertThat(tracker.lastPass!!.closingMps).isNull()
        assertThat(shown).isNull()                      // A's speed is not left on screen
        assertNeverHeldWithLiveSpeed()
    }

    @Test
    @DisplayName("tailgater seen long enough to measure: its speed ends A's hold and it is held after")
    fun tailgaterWithSpeed() {
        for (r in listOf(18, 12, 6, 3)) feed(r, threat = 2)
        assertThat(feed()).isEqualTo(1)
        val a = shown!!
        feed(21, threat = 1)                            // follower, no speed yet
        assertThat(shown).isEqualTo(a)
        feed(18, threat = 1)
        assertThat(shown).isEqualTo(a)
        feed(15, threat = 1)                            // 2 s of history: live speed known
        assertThat(frames.last().liveKnown).isTrue()
        assertThat(shown).isNull()
        assertThat(tracker.lastPass).isNull()
        for (r in listOf(12, 6, 3)) feed(r, threat = 2)
        assertThat(feed()).isEqualTo(1)                 // follower decided
        val b = shown!!
        assertThat(b.seq).isEqualTo(2)
        assertThat(b.closingMps!!).isWithin(1.0).of(3.0)
        assertNeverHeldWithLiveSpeed()
    }

    @Test
    @DisplayName("ghost does not take the following car: A held until the follower's unknown pass")
    fun ghostDoesNotTakeFollowingCar() {
        for (r in listOf(18, 12, 6, 3)) feed(r, threat = 2)
        assertThat(feed()).isEqualTo(1)
        val a = shown!!
        for (r in listOf(9, 9)) {
            feed(r, threat = 1)
            assertThat(shown).isEqualTo(a)
        }
        assertThat(gone()).isEqualTo(1)
        assertThat(tracker.lastPass!!.closingMps).isNull()
        assertThat(shown).isNull()
        assertNeverHeldWithLiveSpeed()
    }

    @Test
    @DisplayName("following car measured before it passes ends the hold and is held itself")
    fun followingCarMeasured() {
        for (r in listOf(18, 12, 6, 3)) feed(r, threat = 2)
        assertThat(feed()).isEqualTo(1)
        val a = shown!!
        feed(25, threat = 1)
        feed(21, threat = 1)
        assertThat(shown).isEqualTo(a)
        feed(18, threat = 1)
        assertThat(shown).isNull()
        for (r in listOf(15, 12, 9)) feed(r, threat = 1)
        assertThat(gone()).isEqualTo(1)
        assertThat(frames.last().held).isNotNull()
        assertThat(shown!!.seq).isEqualTo(2)
        assertNeverHeldWithLiveSpeed()
    }

    @Test
    @DisplayName("two cars in a line: A is never held over B's live speed, B is held at the end")
    fun twoCars() {
        feed(60, 90)
        feed(45, 75)
        feed(30, 60)
        feed(15, 45)
        feed(3, 30)
        assertThat(feed(15)).isEqualTo(1)
        assertThat(shown).isNull()
        feed(3)
        assertThat(feed()).isEqualTo(1)
        val b = shown!!
        assertThat(b.seq).isEqualTo(2)
        assertThat(b.closingMps!!).isWithin(1.0).of(15.0)
        assertThat(frames.dropLast(1).all { it.held == null }).isTrue()
        assertNeverHeldWithLiveSpeed()
    }

    @Test
    @DisplayName("a platoon of three: the last car is held")
    fun platoonOfThree() {
        feed(60, 90, 120)
        feed(45, 75, 105)
        feed(30, 60, 90)
        feed(15, 45, 75)
        feed(3, 30, 60)
        assertThat(feed(15, 45)).isEqualTo(1)
        feed(3, 30)
        assertThat(feed(15)).isEqualTo(1)
        feed(3)
        assertThat(feed()).isEqualTo(1)
        val c = shown!!
        assertThat(c.seq).isEqualTo(3)
        assertThat(c.closingMps!!).isWithin(1.0).of(15.0)
        assertThat(frames.dropLast(1).all { it.held == null }).isTrue()
        assertNeverHeldWithLiveSpeed()
    }

    @Test
    @DisplayName("the hold ends once its time is up")
    fun holdExpires() {
        for (r in listOf(84, 72, 60, 48, 36, 24, 12, 3)) feed(r)
        assertThat(feed()).isEqualTo(1)
        val at = tracker.lastPass!!.atMs
        repeat(9) { feed() }
        assertThat(now - at).isEqualTo(HOLD_MS - 1000)
        assertThat(shown).isNotNull()
        feed()
        assertThat(shown).isNull()
        assertThat(tracker.lastPass).isNotNull()        // the engine keeps it; the field times it out
    }
}

package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import io.github.aryeh95.radarcount.engine.ClosingSpeedTracker.Companion.calculateClosingSpeed
import io.github.aryeh95.radarcount.engine.ClosingSpeedTracker.Companion.isTargetChange
import io.github.aryeh95.radarcount.engine.ClosingSpeedTracker.Sample
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("ClosingSpeedTracker")
class ClosingSpeedTrackerTest {

    private lateinit var tracker: ClosingSpeedTracker

    @BeforeEach
    fun setUp() {
        tracker = ClosingSpeedTracker()
    }

    @Nested
    @DisplayName("calculateClosingSpeed")
    inner class CalculateClosingSpeed {

        @Test
        @DisplayName("returns null for empty list")
        fun emptyListReturnsNull() {
            assertThat(calculateClosingSpeed(emptyList<Sample>())).isNull()
        }

        @Test
        @DisplayName("returns null for single sample")
        fun singleSample() {
            assertThat(calculateClosingSpeed(listOf(Sample(100, 1000)))).isNull()
        }

        @ParameterizedTest(name = "{0}m→{1}m over {2}s = {3} m/s")
        @CsvSource(
            "100, 90, 1, 10.0",
            "100, 80, 2, 10.0",
            "100, 100, 1, 0.0",
            "100, 110, 1, -10.0",
            "50, 40, 1, 10.0",
            "80, 60, 2, 10.0",
        )
        @DisplayName("calculates correct closing speed")
        fun correctSpeed(startM: Int, endM: Int, durationS: Int, expectedMs: Double) {
            val samples = listOf(
                Sample(startM, 0),
                Sample(endM, durationS * 1000L)
            )
            assertThat(calculateClosingSpeed(samples)).isWithin(0.01).of(expectedMs)
        }

        @Test
        @DisplayName("three samples: uses first and last")
        fun threeSamples() {
            val samples = listOf(
                Sample(100, 0),
                Sample(95, 1000),
                Sample(80, 2000)
            )
            // (100 - 80) / 2.0 = 10.0
            assertThat(calculateClosingSpeed(samples)).isWithin(0.01).of(10.0)
        }

        @Test
        @DisplayName("returns null for zero time delta")
        fun zeroTimeDelta() {
            val samples = listOf(Sample(100, 1000), Sample(90, 1000))
            assertThat(calculateClosingSpeed(samples)).isNull()
        }
    }

    @Nested
    @DisplayName("isTargetChange")
    inner class IsTargetChange {

        @ParameterizedTest(name = "{0}m → {1}m = {2}")
        @CsvSource(
            "50, 49, false",
            "50, 80, false",
            "50, 81, true",
            "50, 19, true",
            "50, 20, false",
            "100, 60, true",
            "100, 131, true",
            "100, 130, false",
            "50, 0, true",
        )
        @DisplayName("detects target changes correctly")
        fun detectsTargetChange(oldM: Int, newM: Int, expected: Boolean) {
            assertThat(isTargetChange(oldM, newM)).isEqualTo(expected)
        }
    }

    @Nested
    @DisplayName("isFastApproach")
    inner class IsFastApproach {

        @Test
        @DisplayName("100→90→80 at 1s intervals is fast approach")
        fun fastApproach() {
            tracker.addSample(100, 0)
            tracker.addSample(90, 1000)
            tracker.addSample(80, 2000)
            assertThat(tracker.isFastApproach()).isTrue()
        }

        @Test
        @DisplayName("100→95→90 at 1s intervals is not fast approach")
        fun notFastApproach() {
            tracker.addSample(100, 0)
            tracker.addSample(95, 1000)
            tracker.addSample(90, 2000)
            assertThat(tracker.isFastApproach()).isFalse()
        }

        @Test
        @DisplayName("single sample returns false")
        fun singleSample() {
            tracker.addSample(100, 0)
            assertThat(tracker.isFastApproach()).isFalse()
        }

        @Test
        @DisplayName("no samples returns false")
        fun noSamples() {
            assertThat(tracker.isFastApproach()).isFalse()
        }
    }

    @Nested
    @DisplayName("isHoldingOrReceding")
    inner class IsHoldingOrReceding {

        @Test
        @DisplayName("50→49→49 is holding")
        fun holding() {
            tracker.addSample(50, 0)
            tracker.addSample(49, 1000)
            tracker.addSample(49, 2000)
            assertThat(tracker.isHoldingOrReceding()).isTrue()
        }

        @Test
        @DisplayName("50→51→53 (receding) is holding/receding")
        fun receding() {
            tracker.addSample(50, 0)
            tracker.addSample(51, 1000)
            tracker.addSample(53, 2000)
            assertThat(tracker.isHoldingOrReceding()).isTrue()
        }

        @Test
        @DisplayName("100→90→80 (fast closing) is not holding/receding")
        fun fastClosing() {
            tracker.addSample(100, 0)
            tracker.addSample(90, 1000)
            tracker.addSample(80, 2000)
            assertThat(tracker.isHoldingOrReceding()).isFalse()
        }

        @Test
        @DisplayName("single sample returns false")
        fun singleSample() {
            tracker.addSample(50, 0)
            assertThat(tracker.isHoldingOrReceding()).isFalse()
        }
    }

    @Nested
    @DisplayName("target change resets tracker")
    inner class TargetChangeReset {

        @Test
        @DisplayName("large distance jump resets buffer")
        fun largeJumpResets() {
            tracker.addSample(50, 0)
            tracker.addSample(49, 1000)
            tracker.addSample(48, 2000)
            // Target change: 48 → 120 (diff = 72 > 30)
            tracker.addSample(120, 3000)
            // Only 1 sample after reset — insufficient data
            assertThat(tracker.isFastApproach()).isFalse()
            assertThat(tracker.isHoldingOrReceding()).isFalse()
        }

        @Test
        @DisplayName("needs 2 more samples after target change")
        fun needsMoreSamplesAfterReset() {
            tracker.addSample(50, 0)
            tracker.addSample(49, 1000)
            // Target change
            tracker.addSample(120, 2000)
            // One more sample — now have 2
            tracker.addSample(110, 3000)
            assertThat(tracker.isFastApproach()).isTrue()
        }

        @Test
        @DisplayName("zero distance treated as target change")
        fun zeroDistanceResetsTracker() {
            tracker.addSample(50, 0)
            tracker.addSample(40, 1000)
            tracker.addSample(0, 2000)
            assertThat(tracker.isFastApproach()).isFalse()
            assertThat(tracker.isHoldingOrReceding()).isFalse()
        }
    }

    @Nested
    @DisplayName("buffer size limit")
    inner class BufferSizeLimit {

        @Test
        @DisplayName("only keeps last 3 samples")
        fun keepsLastThree() {
            tracker.addSample(100, 0)
            tracker.addSample(95, 1000)
            tracker.addSample(90, 2000)
            tracker.addSample(85, 3000)
            // Buffer is now [95, 90, 85], speed = (95-85)/2 = 5 m/s — not fast
            assertThat(tracker.isFastApproach()).isFalse()
        }
    }

    @Nested
    @DisplayName("reset")
    inner class Reset {

        @Test
        @DisplayName("reset clears all state")
        fun resetClearsState() {
            tracker.addSample(100, 0)
            tracker.addSample(90, 1000)
            tracker.reset()
            assertThat(tracker.isFastApproach()).isFalse()
            assertThat(tracker.isHoldingOrReceding()).isFalse()
        }
    }
}

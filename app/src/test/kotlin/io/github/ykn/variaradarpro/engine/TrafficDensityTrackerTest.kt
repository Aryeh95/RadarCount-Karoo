package io.github.ykn.variaradarpro.engine

import com.google.common.truth.Truth.assertThat
import io.github.ykn.variaradarpro.engine.TrafficDensityTracker.Companion.calculateAverage
import io.github.ykn.variaradarpro.engine.TrafficDensityTracker.Companion.isDense
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("TrafficDensityTracker")
class TrafficDensityTrackerTest {

    private lateinit var tracker: TrafficDensityTracker

    @BeforeEach
    fun setUp() {
        tracker = TrafficDensityTracker()
    }

    @Nested
    @DisplayName("calculateAverage")
    inner class CalculateAverage {

        @Test
        @DisplayName("empty collection returns 0.0")
        fun emptyCollection() {
            assertThat(calculateAverage(emptyList())).isEqualTo(0.0)
        }

        @Test
        @DisplayName("single element")
        fun singleElement() {
            assertThat(calculateAverage(listOf(3))).isWithin(0.01).of(3.0)
        }

        @Test
        @DisplayName("multiple elements")
        fun multipleElements() {
            assertThat(calculateAverage(listOf(1, 2, 3, 4))).isWithin(0.01).of(2.5)
        }

        @Test
        @DisplayName("all zeros")
        fun allZeros() {
            assertThat(calculateAverage(listOf(0, 0, 0))).isEqualTo(0.0)
        }
    }

    @Nested
    @DisplayName("isDense")
    inner class IsDense {

        @ParameterizedTest(name = "avg={0}, count={1} → {2}")
        @CsvSource(
            "2.0, 10, true",
            "3.0, 15, true",
            "1.9, 10, false",
            "2.0, 9, false",
            "5.0, 5, false",
            "0.0, 30, false",
        )
        @DisplayName("returns correct density classification")
        fun correctClassification(average: Double, count: Int, expected: Boolean) {
            assertThat(isDense(average, count)).isEqualTo(expected)
        }
    }

    @Nested
    @DisplayName("less than MIN_SAMPLES")
    inner class LessThanMinSamples {

        @Test
        @DisplayName("fewer than 10 samples is never dense even with high counts")
        fun fewerThanMinSamples() {
            repeat(9) { tracker.addSample(5) }
            assertThat(tracker.isDenseTraffic()).isFalse()
        }

        @Test
        @DisplayName("exactly 10 samples with high average is dense")
        fun exactlyMinSamples() {
            repeat(10) { tracker.addSample(3) }
            assertThat(tracker.isDenseTraffic()).isTrue()
        }
    }

    @Nested
    @DisplayName("dense traffic detection")
    inner class DenseTrafficDetection {

        @Test
        @DisplayName("10+ samples with avg >= 2.0 is dense")
        fun denseTraffic() {
            repeat(10) { tracker.addSample(2) }
            assertThat(tracker.isDenseTraffic()).isTrue()
        }

        @Test
        @DisplayName("10+ samples with avg < 2.0 is not dense")
        fun notDenseTraffic() {
            repeat(10) { tracker.addSample(1) }
            assertThat(tracker.isDenseTraffic()).isFalse()
        }
    }

    @Nested
    @DisplayName("circular buffer")
    inner class CircularBuffer {

        @Test
        @DisplayName("31 samples drops oldest, average reflects last 30")
        fun dropsOldest() {
            // Add 1 high sample, then 30 low samples
            tracker.addSample(100)
            repeat(30) { tracker.addSample(0) }
            // The high sample should have been dropped; avg of 30 zeros = 0.0
            assertThat(tracker.isDenseTraffic()).isFalse()
        }

        @Test
        @DisplayName("buffer correctly rolls over high-count period")
        fun rollsOverHighCount() {
            // Fill with dense traffic
            repeat(30) { tracker.addSample(3) }
            assertThat(tracker.isDenseTraffic()).isTrue()
            // Feed zeros to push out dense samples
            repeat(30) { tracker.addSample(0) }
            assertThat(tracker.isDenseTraffic()).isFalse()
        }
    }

    @Nested
    @DisplayName("zero-vehicle samples reduce average")
    inner class ZeroVehicleSamples {

        @Test
        @DisplayName("mixing zeros brings average below threshold")
        fun zerosReduceAverage() {
            // 5 samples of 3 vehicles, 5 samples of 0
            repeat(5) { tracker.addSample(3) }
            repeat(5) { tracker.addSample(0) }
            // Average = 15/10 = 1.5, below 2.0
            assertThat(tracker.isDenseTraffic()).isFalse()
        }
    }

    @Nested
    @DisplayName("reset")
    inner class Reset {

        @Test
        @DisplayName("reset clears everything, not dense after reset")
        fun resetClearsState() {
            repeat(15) { tracker.addSample(5) }
            assertThat(tracker.isDenseTraffic()).isTrue()

            tracker.reset()

            assertThat(tracker.isDenseTraffic()).isFalse()
        }

        @Test
        @DisplayName("can rebuild state after reset")
        fun canRebuildAfterReset() {
            repeat(15) { tracker.addSample(5) }
            tracker.reset()
            repeat(10) { tracker.addSample(3) }
            assertThat(tracker.isDenseTraffic()).isTrue()
        }
    }
}

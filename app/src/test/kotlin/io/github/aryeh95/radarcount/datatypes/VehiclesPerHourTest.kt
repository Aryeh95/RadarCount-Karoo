package io.github.aryeh95.radarcount.datatypes

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("VehiclesPerHour")
class VehiclesPerHourTest {

    @Test
    @DisplayName("under two minutes of ride time shows nothing")
    fun tooEarlyToRate() {
        assertThat(VehiclesPerHourDataType.format(passCount = 5, rideTimeMs = 119_999L)).isEqualTo("--")
    }

    @Test
    @DisplayName("two minutes is enough to start showing a rate")
    fun twoMinutesIsEnough() {
        assertThat(VehiclesPerHourDataType.format(passCount = 0, rideTimeMs = 120_000L)).isEqualTo("0.0")
    }

    @Test
    @DisplayName("small rates keep one decimal")
    fun smallRateKeepsDecimal() {
        assertThat(VehiclesPerHourDataType.format(passCount = 1, rideTimeMs = 2_400_000L)).isEqualTo("1.5")
    }

    @Test
    @DisplayName("ten per hour is already a whole number")
    fun tenIsWhole() {
        assertThat(VehiclesPerHourDataType.format(passCount = 5, rideTimeMs = 1_800_000L)).isEqualTo("10")
    }

    @Test
    @DisplayName("a full hour of ride time shows the raw count")
    fun fullHourShowsCount() {
        assertThat(VehiclesPerHourDataType.format(passCount = 12, rideTimeMs = 3_600_000L)).isEqualTo("12")
    }
}

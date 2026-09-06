package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("Units")
class UnitsTest {

    @ParameterizedTest(name = "{0}m → {1}ft")
    @CsvSource("0,0", "1,3", "20,66", "45,148", "100,328")
    fun metersToFeetRounds(meters: Int, feet: Int) {
        assertThat(Units.metersToFeet(meters)).isEqualTo(feet)
    }

    @ParameterizedTest(name = "{0} km/h → {1} mph")
    @CsvSource("3,2", "5,3", "8,5", "30,19")
    fun kmhToMphRounds(kmh: Int, mph: Int) {
        assertThat(Units.kmhToMph(kmh)).isEqualTo(mph)
    }

    @Test
    fun formatDistance() {
        assertThat(Units.formatDistance(45, useImperial = false)).isEqualTo("45m")
        assertThat(Units.formatDistance(45, useImperial = true)).isEqualTo("148ft")
    }

    @Test
    fun formatSpeed() {
        assertThat(Units.formatSpeed(5, useImperial = false)).isEqualTo("5 km/h")
        assertThat(Units.formatSpeed(5, useImperial = true)).isEqualTo("3 mph")
    }
}

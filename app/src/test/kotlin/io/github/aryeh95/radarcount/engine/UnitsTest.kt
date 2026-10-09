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

    @Test
    fun distanceLabel() {
        assertThat(Units.distanceLabel(45, imperial = false)).isEqualTo("45m")
        assertThat(Units.distanceLabel(45, imperial = true)).isEqualTo("148ft")
    }
}

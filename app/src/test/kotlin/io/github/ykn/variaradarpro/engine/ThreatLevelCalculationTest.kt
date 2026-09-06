package io.github.ykn.variaradarpro.engine

import com.google.common.truth.Truth.assertThat
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("Threat Level Calculation")
class ThreatLevelCalculationTest {

    @Nested
    @DisplayName("mapThreatLevel")
    inner class MapThreatLevel {

        @ParameterizedTest(name = "karoo level {0} → {1}")
        @CsvSource(
            "0, CLEAR",
            "1, APPROACHING",
            "2, WARNING",
            "3, CRITICAL",
        )
        @DisplayName("valid Karoo levels")
        fun validKarooLevels(karooLevel: Int, expected: ThreatLevel) {
            assertThat(RadarParser.mapThreatLevel(karooLevel)).isEqualTo(expected)
        }

        @ParameterizedTest(name = "karoo level {0} → CLEAR")
        @CsvSource(
            "-1",
            "-100",
            "4",
            "99",
        )
        @DisplayName("out-of-range values default to CLEAR")
        fun outOfRangeDefaultsToClear(karooLevel: Int) {
            assertThat(RadarParser.mapThreatLevel(karooLevel)).isEqualTo(ThreatLevel.CLEAR)
        }
    }
}

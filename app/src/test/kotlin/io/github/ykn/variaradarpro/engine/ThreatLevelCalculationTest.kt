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
    @DisplayName("calculateThreatLevel")
    inner class CalculateThreatLevel {

        @ParameterizedTest(name = "distance {0}m → {1}")
        @CsvSource(
            // Critical: distance <= 20
            "5, CRITICAL",
            "10, CRITICAL",
            "20, CRITICAL",
            // Warning: 20 < distance <= 50
            "21, WARNING",
            "35, WARNING",
            "50, WARNING",
            // Approaching: 50 < distance <= 100
            "51, APPROACHING",
            "75, APPROACHING",
            "100, APPROACHING",
            // Clear: distance > 100
            "101, CLEAR",
            "200, CLEAR",
        )
        @DisplayName("default thresholds (100/50/20)")
        fun defaultThresholds(distanceM: Int, expected: ThreatLevel) {
            val result = RadarEngine.calculateThreatLevel(
                distanceM = distanceM,
                approachingThreshold = 100,
                warningThreshold = 50,
                criticalThreshold = 20
            )
            assertThat(result).isEqualTo(expected)
        }

        @ParameterizedTest(name = "distance {0}m → {1}")
        @CsvSource(
            "0, CRITICAL",
            "-1, CRITICAL",
            "-100, CRITICAL",
        )
        @DisplayName("zero and negative distances → CRITICAL")
        fun zeroAndNegativeDistances(distanceM: Int, expected: ThreatLevel) {
            val result = RadarEngine.calculateThreatLevel(
                distanceM = distanceM,
                approachingThreshold = 100,
                warningThreshold = 50,
                criticalThreshold = 20
            )
            assertThat(result).isEqualTo(expected)
        }

        @ParameterizedTest(name = "thresholds ({1}/{2}/{3}), distance {0}m → {4}")
        @CsvSource(
            "150, 200, 70, 30, APPROACHING",
            "25, 200, 70, 30, CRITICAL",
            "50, 200, 70, 30, WARNING",
            "250, 200, 70, 30, CLEAR",
        )
        @DisplayName("custom thresholds")
        fun customThresholds(
            distanceM: Int,
            approaching: Int,
            warning: Int,
            critical: Int,
            expected: ThreatLevel
        ) {
            val result = RadarEngine.calculateThreatLevel(
                distanceM = distanceM,
                approachingThreshold = approaching,
                warningThreshold = warning,
                criticalThreshold = critical
            )
            assertThat(result).isEqualTo(expected)
        }
    }

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
            assertThat(RadarEngine.mapThreatLevel(karooLevel)).isEqualTo(expected)
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
            assertThat(RadarEngine.mapThreatLevel(karooLevel)).isEqualTo(ThreatLevel.CLEAR)
        }
    }
}

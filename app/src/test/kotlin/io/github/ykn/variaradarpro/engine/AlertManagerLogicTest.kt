package io.github.ykn.variaradarpro.engine

import com.google.common.truth.Truth.assertThat
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("AlertManager Logic")
class AlertManagerLogicTest {

    @Nested
    @DisplayName("applyNightModeOverrides")
    inner class ApplyNightModeOverrides {

        @Test
        @DisplayName("multiplies all three distances by 1.33")
        fun multipliesDistances() {
            val settings = PresetSettings(
                approachingDistanceM = 100,
                warningDistanceM = 50,
                criticalDistanceM = 20
            )

            val result = AlertManager.applyNightModeOverrides(settings)

            assertThat(result.approachingDistanceM).isEqualTo(133)
            assertThat(result.warningDistanceM).isEqualTo(66)
            assertThat(result.criticalDistanceM).isEqualTo(26)
        }

        @Test
        @DisplayName("does not modify original settings (immutable copy)")
        fun doesNotModifyOriginal() {
            val original = PresetSettings(
                approachingDistanceM = 100,
                warningDistanceM = 50,
                criticalDistanceM = 20
            )

            AlertManager.applyNightModeOverrides(original)

            assertThat(original.approachingDistanceM).isEqualTo(100)
            assertThat(original.warningDistanceM).isEqualTo(50)
            assertThat(original.criticalDistanceM).isEqualTo(20)
        }

        @Test
        @DisplayName("preserves non-distance fields")
        fun preservesNonDistanceFields() {
            val settings = PresetSettings(
                approachingDistanceM = 100,
                warningDistanceM = 50,
                criticalDistanceM = 20,
                alertCooldownMs = 8000L,
                speedGateKmh = 5
            )

            val result = AlertManager.applyNightModeOverrides(settings)

            assertThat(result.alertCooldownMs).isEqualTo(8000L)
            assertThat(result.speedGateKmh).isEqualTo(5)
        }
    }

    @Nested
    @DisplayName("getAutoDismissMs")
    inner class GetAutoDismissMs {

        @ParameterizedTest(name = "{0} → {1}ms")
        @CsvSource(
            "CRITICAL, 5000",
            "WARNING, 4000",
            "APPROACHING, 2000",
            "CLEAR, 1500",
        )
        @DisplayName("returns correct auto-dismiss duration")
        fun correctDuration(level: ThreatLevel, expectedMs: Long) {
            assertThat(AlertManager.getAutoDismissMs(level)).isEqualTo(expectedMs)
        }
    }

    @Nested
    @DisplayName("NIGHT_MODE_DISTANCE_MULTIPLIER")
    inner class NightModeMultiplier {

        @Test
        @DisplayName("multiplier is 1.33")
        fun multiplierValue() {
            assertThat(AlertManager.NIGHT_MODE_DISTANCE_MULTIPLIER).isEqualTo(1.33f)
        }
    }
}

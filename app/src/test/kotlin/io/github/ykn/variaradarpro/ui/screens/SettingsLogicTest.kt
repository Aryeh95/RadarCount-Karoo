package io.github.ykn.variaradarpro.ui.screens

import com.google.common.truth.Truth.assertThat
import io.github.ykn.variaradarpro.data.models.BuiltInSoundSet
import io.github.ykn.variaradarpro.data.models.ScreenWakePolicy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("SettingsLogic")
class SettingsLogicTest {

    @Nested
    @DisplayName("nextApproaching")
    inner class NextApproaching {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource("100,125", "125,150", "150,175", "175,200", "200,100")
        @DisplayName("cycles through values")
        fun cycles(current: Int, expected: Int) {
            assertThat(SettingsLogic.nextApproaching(current)).isEqualTo(expected)
        }

        @Test
        @DisplayName("invalid value cycles to first")
        fun invalidCyclesToFirst() {
            assertThat(SettingsLogic.nextApproaching(999)).isEqualTo(100)
        }
    }

    @Nested
    @DisplayName("nextWarning")
    inner class NextWarning {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource("30,40", "40,50", "50,60", "60,70", "70,30")
        @DisplayName("cycles through values")
        fun cycles(current: Int, expected: Int) {
            assertThat(SettingsLogic.nextWarning(current)).isEqualTo(expected)
        }

        @Test
        @DisplayName("invalid value cycles to first")
        fun invalidCyclesToFirst() {
            assertThat(SettingsLogic.nextWarning(999)).isEqualTo(30)
        }
    }

    @Nested
    @DisplayName("nextCritical")
    inner class NextCritical {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource("10,15", "15,20", "20,25", "25,30", "30,10")
        @DisplayName("cycles through values")
        fun cycles(current: Int, expected: Int) {
            assertThat(SettingsLogic.nextCritical(current)).isEqualTo(expected)
        }

        @Test
        @DisplayName("invalid value cycles to first")
        fun invalidCyclesToFirst() {
            assertThat(SettingsLogic.nextCritical(999)).isEqualTo(10)
        }
    }

    @Nested
    @DisplayName("nextCooldown")
    inner class NextCooldown {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource("3000,5000", "5000,8000", "8000,12000", "12000,3000")
        @DisplayName("cycles through values")
        fun cycles(current: Long, expected: Long) {
            assertThat(SettingsLogic.nextCooldown(current)).isEqualTo(expected)
        }

        @Test
        @DisplayName("invalid value cycles to first")
        fun invalidCyclesToFirst() {
            assertThat(SettingsLogic.nextCooldown(99999L)).isEqualTo(3000L)
        }
    }

    @Nested
    @DisplayName("nextSpeedGate")
    inner class NextSpeedGate {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource("0,3", "3,5", "5,8", "8,0")
        @DisplayName("cycles through values")
        fun cycles(current: Int, expected: Int) {
            assertThat(SettingsLogic.nextSpeedGate(current)).isEqualTo(expected)
        }

        @Test
        @DisplayName("invalid value cycles to first")
        fun invalidCyclesToFirst() {
            assertThat(SettingsLogic.nextSpeedGate(999)).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("nextSound")
    inner class NextSound {

        @Test
        @DisplayName("CLASSIC → SUBTLE → URGENT → BIKE_BELL → CLASSIC")
        fun cyclesThroughAll() {
            assertThat(SettingsLogic.nextSound(BuiltInSoundSet.CLASSIC)).isEqualTo(BuiltInSoundSet.SUBTLE)
            assertThat(SettingsLogic.nextSound(BuiltInSoundSet.SUBTLE)).isEqualTo(BuiltInSoundSet.URGENT)
            assertThat(SettingsLogic.nextSound(BuiltInSoundSet.URGENT)).isEqualTo(BuiltInSoundSet.BIKE_BELL)
            assertThat(SettingsLogic.nextSound(BuiltInSoundSet.BIKE_BELL)).isEqualTo(BuiltInSoundSet.CLASSIC)
        }
    }

    @Nested
    @DisplayName("nextScreenWake")
    inner class NextScreenWake {

        @Test
        @DisplayName("NEVER → CRITICAL_ONLY → ALWAYS → NEVER")
        fun cyclesThroughAll() {
            assertThat(SettingsLogic.nextScreenWake(ScreenWakePolicy.NEVER)).isEqualTo(ScreenWakePolicy.CRITICAL_ONLY)
            assertThat(SettingsLogic.nextScreenWake(ScreenWakePolicy.CRITICAL_ONLY)).isEqualTo(ScreenWakePolicy.ALWAYS)
            assertThat(SettingsLogic.nextScreenWake(ScreenWakePolicy.ALWAYS)).isEqualTo(ScreenWakePolicy.NEVER)
        }
    }

    @Nested
    @DisplayName("formatDistance")
    inner class FormatDistance {

        @ParameterizedTest(name = "{0}m metric → {1}")
        @CsvSource("100,100m", "50,50m", "20,20m")
        @DisplayName("metric format")
        fun metricFormat(meters: Int, expected: String) {
            assertThat(SettingsLogic.formatDistance(meters, useImperial = false)).isEqualTo(expected)
        }

        @ParameterizedTest(name = "{0}m imperial → {1}")
        @CsvSource("100,328ft", "50,164ft", "20,65ft")
        @DisplayName("imperial format")
        fun imperialFormat(meters: Int, expected: String) {
            assertThat(SettingsLogic.formatDistance(meters, useImperial = true)).isEqualTo(expected)
        }
    }

    @Nested
    @DisplayName("formatCooldown")
    inner class FormatCooldown {

        @ParameterizedTest(name = "{0}ms → {1}")
        @CsvSource("3000,3s", "5000,5s", "8000,8s", "12000,12s")
        @DisplayName("formats milliseconds as seconds")
        fun formatsCorrectly(ms: Long, expected: String) {
            assertThat(SettingsLogic.formatCooldown(ms)).isEqualTo(expected)
        }
    }

    @Nested
    @DisplayName("formatSpeedGate")
    inner class FormatSpeedGate {

        @Test
        @DisplayName("zero returns Off")
        fun zeroReturnsOff() {
            assertThat(SettingsLogic.formatSpeedGate(0, useImperial = false)).isEqualTo("Off")
            assertThat(SettingsLogic.formatSpeedGate(0, useImperial = true)).isEqualTo("Off")
        }

        @ParameterizedTest(name = "{0} km/h metric → {1}")
        @CsvSource("3,3 km/h", "5,5 km/h", "8,8 km/h")
        @DisplayName("metric format")
        fun metricFormat(kmh: Int, expected: String) {
            assertThat(SettingsLogic.formatSpeedGate(kmh, useImperial = false)).isEqualTo(expected)
        }

        @ParameterizedTest(name = "{0} km/h imperial → {1}")
        @CsvSource("5,3 mph", "8,4 mph")
        @DisplayName("imperial format")
        fun imperialFormat(kmh: Int, expected: String) {
            assertThat(SettingsLogic.formatSpeedGate(kmh, useImperial = true)).isEqualTo(expected)
        }
    }
}

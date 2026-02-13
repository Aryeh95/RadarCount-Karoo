package io.github.ykn.variaradarpro.data.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Default Settings")
class DefaultSettingsTest {

    @Nested
    @DisplayName("PresetSettings defaults")
    inner class PresetSettingsDefaults {

        private val defaults = PresetSettings()

        @Test
        @DisplayName("approaching distance is 100m")
        fun approachingDistance() {
            assertThat(defaults.approachingDistanceM).isEqualTo(100)
        }

        @Test
        @DisplayName("warning distance is 50m")
        fun warningDistance() {
            assertThat(defaults.warningDistanceM).isEqualTo(50)
        }

        @Test
        @DisplayName("critical distance is 20m")
        fun criticalDistance() {
            assertThat(defaults.criticalDistanceM).isEqualTo(20)
        }

        @Test
        @DisplayName("alert cooldown is 5000ms")
        fun alertCooldown() {
            assertThat(defaults.alertCooldownMs).isEqualTo(5000L)
        }

        @Test
        @DisplayName("speed gate is disabled (0)")
        fun speedGateDisabled() {
            assertThat(defaults.speedGateKmh).isEqualTo(0)
        }

        @Test
        @DisplayName("sound is enabled")
        fun soundEnabled() {
            assertThat(defaults.soundEnabled).isTrue()
        }

        @Test
        @DisplayName("sound set is CLASSIC")
        fun soundSet() {
            assertThat(defaults.soundSet).isEqualTo(BuiltInSoundSet.CLASSIC)
        }

        @Test
        @DisplayName("screen wake policy is CRITICAL_ONLY")
        fun screenWakePolicy() {
            assertThat(defaults.screenWakePolicy).isEqualTo(ScreenWakePolicy.CRITICAL_ONLY)
        }

        @Test
        @DisplayName("clear chime is enabled")
        fun clearChimeEnabled() {
            assertThat(defaults.clearChimeEnabled).isTrue()
        }
    }

    @Nested
    @DisplayName("AlertSettings defaults")
    inner class AlertSettingsDefaults {

        private val defaults = AlertSettings()

        @Test
        @DisplayName("all channels enabled by default")
        fun allChannelsEnabled() {
            assertThat(defaults.globalEnabled).isTrue()
            assertThat(defaults.visualAlert).isTrue()
            assertThat(defaults.soundAlert).isTrue()
        }
    }

    @Nested
    @DisplayName("ThreatLevel ordering")
    inner class ThreatLevelOrdering {

        @Test
        @DisplayName("ordinal ordering: CLEAR < APPROACHING < WARNING < CRITICAL")
        fun ordinalOrdering() {
            assertThat(ThreatLevel.CLEAR.ordinal).isLessThan(ThreatLevel.APPROACHING.ordinal)
            assertThat(ThreatLevel.APPROACHING.ordinal).isLessThan(ThreatLevel.WARNING.ordinal)
            assertThat(ThreatLevel.WARNING.ordinal).isLessThan(ThreatLevel.CRITICAL.ordinal)
        }

        @Test
        @DisplayName("exactly 4 threat levels")
        fun exactlyFourLevels() {
            assertThat(ThreatLevel.entries).hasSize(4)
        }
    }

    @Nested
    @DisplayName("WidgetState sealed class")
    inner class WidgetStateConstruction {

        @Test
        @DisplayName("NotConnected is singleton")
        fun notConnected() {
            assertThat(WidgetState.NotConnected).isInstanceOf(WidgetState::class.java)
        }

        @Test
        @DisplayName("Connecting is singleton")
        fun connecting() {
            assertThat(WidgetState.Connecting).isInstanceOf(WidgetState::class.java)
        }

        @Test
        @DisplayName("Clear is singleton")
        fun clear() {
            assertThat(WidgetState.Clear).isInstanceOf(WidgetState::class.java)
        }

        @Test
        @DisplayName("ConnectionLost is singleton")
        fun connectionLost() {
            assertThat(WidgetState.ConnectionLost).isInstanceOf(WidgetState::class.java)
        }

        @Test
        @DisplayName("Threat holds correct data")
        fun threatHoldsData() {
            val threat = WidgetState.Threat(
                level = ThreatLevel.WARNING,
                vehicleCount = 3,
                nearestDistanceM = 42
            )

            assertThat(threat.level).isEqualTo(ThreatLevel.WARNING)
            assertThat(threat.vehicleCount).isEqualTo(3)
            assertThat(threat.nearestDistanceM).isEqualTo(42)
            assertThat(threat).isInstanceOf(WidgetState::class.java)
        }
    }
}

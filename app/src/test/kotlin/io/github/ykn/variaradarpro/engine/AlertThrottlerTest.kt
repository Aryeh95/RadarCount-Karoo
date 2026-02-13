package io.github.ykn.variaradarpro.engine

import com.google.common.truth.Truth.assertThat
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AlertThrottler")
class AlertThrottlerTest {

    private lateinit var throttler: AlertThrottler

    @BeforeEach
    fun setUp() {
        throttler = AlertThrottler()
    }

    @Nested
    @DisplayName("shouldAlert")
    inner class ShouldAlert {

        @Test
        @DisplayName("CLEAR always returns false")
        fun clearAlwaysReturnsFalse() {
            assertThat(throttler.shouldAlert(ThreatLevel.CLEAR, 5000L)).isFalse()
        }

        @Test
        @DisplayName("first alert always fires")
        fun firstAlertAlwaysFires() {
            assertThat(throttler.shouldAlert(ThreatLevel.APPROACHING, 5000L)).isTrue()
        }

        @Test
        @DisplayName("second alert within cooldown is blocked")
        fun secondAlertWithinCooldownIsBlocked() {
            assertThat(throttler.shouldAlert(ThreatLevel.APPROACHING, 5000L)).isTrue()
            assertThat(throttler.shouldAlert(ThreatLevel.APPROACHING, 5000L)).isFalse()
        }

        @Test
        @DisplayName("alert after cooldown expires fires")
        fun alertAfterCooldownExpiresFires() {
            assertThat(throttler.shouldAlert(ThreatLevel.WARNING, 1L)).isTrue()
            Thread.sleep(5)
            assertThat(throttler.shouldAlert(ThreatLevel.WARNING, 1L)).isTrue()
        }

        @Test
        @DisplayName("escalation APPROACHING → WARNING bypasses cooldown")
        fun escalationApproachingToWarningBypassesCooldown() {
            assertThat(throttler.shouldAlert(ThreatLevel.APPROACHING, 60_000L)).isTrue()
            assertThat(throttler.shouldAlert(ThreatLevel.WARNING, 60_000L)).isTrue()
        }

        @Test
        @DisplayName("escalation WARNING → CRITICAL bypasses cooldown")
        fun escalationWarningToCriticalBypassesCooldown() {
            assertThat(throttler.shouldAlert(ThreatLevel.WARNING, 60_000L)).isTrue()
            assertThat(throttler.shouldAlert(ThreatLevel.CRITICAL, 60_000L)).isTrue()
        }

        @Test
        @DisplayName("same level during cooldown is blocked")
        fun sameLevelDuringCooldownIsBlocked() {
            assertThat(throttler.shouldAlert(ThreatLevel.CRITICAL, 60_000L)).isTrue()
            assertThat(throttler.shouldAlert(ThreatLevel.CRITICAL, 60_000L)).isFalse()
        }

        @Test
        @DisplayName("de-escalation respects cooldown")
        fun deEscalationRespectsCooldown() {
            // First APPROACHING fires
            assertThat(throttler.shouldAlert(ThreatLevel.APPROACHING, 60_000L)).isTrue()
            // Escalation to CRITICAL fires (bypasses cooldown)
            assertThat(throttler.shouldAlert(ThreatLevel.CRITICAL, 60_000L)).isTrue()
            // De-escalation back to APPROACHING — not an escalation, APPROACHING still in cooldown
            assertThat(throttler.shouldAlert(ThreatLevel.APPROACHING, 60_000L)).isFalse()
        }
    }

    @Nested
    @DisplayName("reset")
    inner class Reset {

        @Test
        @DisplayName("reset clears all state allowing new alerts")
        fun resetClearsAllState() {
            throttler.shouldAlert(ThreatLevel.WARNING, 60_000L)
            throttler.reset()
            // After reset, same level should fire again
            assertThat(throttler.shouldAlert(ThreatLevel.WARNING, 60_000L)).isTrue()
        }
    }

    @Nested
    @DisplayName("isInCooldown")
    inner class IsInCooldown {

        @Test
        @DisplayName("CLEAR is never in cooldown")
        fun clearNeverInCooldown() {
            assertThat(throttler.isInCooldown(ThreatLevel.CLEAR, 5000L)).isFalse()
        }

        @Test
        @DisplayName("reflects cooldown state after alert")
        fun reflectsCooldownAfterAlert() {
            throttler.shouldAlert(ThreatLevel.WARNING, 60_000L)
            assertThat(throttler.isInCooldown(ThreatLevel.WARNING, 60_000L)).isTrue()
        }

        @Test
        @DisplayName("not in cooldown before any alert")
        fun notInCooldownBeforeAnyAlert() {
            assertThat(throttler.isInCooldown(ThreatLevel.WARNING, 5000L)).isFalse()
        }
    }

    @Nested
    @DisplayName("recordAlert")
    inner class RecordAlert {

        @Test
        @DisplayName("recordAlert updates timestamp and blocks subsequent same-level")
        fun recordAlertUpdatesTimestamp() {
            throttler.recordAlert(ThreatLevel.APPROACHING)
            assertThat(throttler.isInCooldown(ThreatLevel.APPROACHING, 60_000L)).isTrue()
        }

        @Test
        @DisplayName("recordAlert for CLEAR is ignored")
        fun recordAlertClearIgnored() {
            throttler.recordAlert(ThreatLevel.CLEAR)
            assertThat(throttler.isInCooldown(ThreatLevel.CLEAR, 60_000L)).isFalse()
        }
    }
}

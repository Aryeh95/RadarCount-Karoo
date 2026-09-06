package io.github.ykn.variaradarpro.engine

import io.github.ykn.variaradarpro.data.models.ThreatLevel

/**
 * Alert throttling with per-level cooldowns.
 *
 * Escalation (higher threat level) always bypasses cooldown.
 * Called from a single coroutine in [AlertManager]; not thread-safe.
 */
class AlertThrottler {

    // Per-level last alert timestamps
    private val lastAlertTimes = mutableMapOf(
        ThreatLevel.APPROACHING to 0L,
        ThreatLevel.WARNING to 0L,
        ThreatLevel.CRITICAL to 0L
    )

    // Last alerted level for escalation detection
    private var lastAlertedLevel: ThreatLevel? = null

    /**
     * Check if an alert should be fired for the given threat level.
     *
     * @param level Current threat level
     * @param cooldownMs Cooldown period in milliseconds
     * @return true if alert should fire, false if throttled
     */
    fun shouldAlert(level: ThreatLevel, cooldownMs: Long): Boolean {
        if (level == ThreatLevel.CLEAR) return false

        val now = System.currentTimeMillis()
        val lastTime = lastAlertTimes[level] ?: return false

        // Escalation always fires immediately
        val previousLevel = lastAlertedLevel
        val isEscalation = previousLevel != null && level > previousLevel

        if (!isEscalation && now - lastTime < cooldownMs) {
            return false
        }

        lastAlertTimes[level] = now
        lastAlertedLevel = level
        return true
    }

    /**
     * Reset all throttle states.
     */
    fun reset() {
        for (key in lastAlertTimes.keys) {
            lastAlertTimes[key] = 0L
        }
        lastAlertedLevel = null
    }
}

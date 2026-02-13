package io.github.ykn.variaradarpro.engine

import io.github.ykn.variaradarpro.data.models.ThreatLevel
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe alert throttling with per-level cooldowns.
 *
 * Escalation (higher threat level) always bypasses cooldown.
 */
class AlertThrottler {

    // Per-level last alert timestamps
    private val lastAlertTimes = mapOf(
        ThreatLevel.APPROACHING to AtomicLong(0L),
        ThreatLevel.WARNING to AtomicLong(0L),
        ThreatLevel.CRITICAL to AtomicLong(0L)
    )

    // Last alerted level for escalation detection
    private val lastAlertedLevel = AtomicReference<ThreatLevel?>(null)

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

        // Check for escalation — always allow higher threat levels immediately
        val previousLevel = lastAlertedLevel.get()
        val isEscalation = previousLevel != null && level.ordinal > previousLevel.ordinal

        if (!isEscalation) {
            // Not an escalation — check cooldown
            val elapsed = now - lastTime.get()
            if (elapsed < cooldownMs) {
                return false
            }
        }

        // Try to update atomically
        val previous = lastTime.get()
        if (lastTime.compareAndSet(previous, now)) {
            lastAlertedLevel.set(level)
            return true
        }

        // CAS failed — another thread won, skip this alert
        return false
    }

    /**
     * Record that an alert was fired externally.
     */
    fun recordAlert(level: ThreatLevel) {
        if (level == ThreatLevel.CLEAR) return

        val now = System.currentTimeMillis()
        lastAlertTimes[level]?.set(now)
        lastAlertedLevel.set(level)
    }

    /**
     * Reset all throttle states.
     */
    fun reset() {
        lastAlertTimes.values.forEach { it.set(0L) }
        lastAlertedLevel.set(null)
    }

    /**
     * Check if currently in cooldown for a specific level.
     */
    fun isInCooldown(level: ThreatLevel, cooldownMs: Long): Boolean {
        if (level == ThreatLevel.CLEAR) return false
        val lastTime = lastAlertTimes[level]?.get() ?: return false
        return (System.currentTimeMillis() - lastTime) < cooldownMs
    }
}

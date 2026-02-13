package io.github.ykn.variaradarpro.data.models

/**
 * Threat level from radar data.
 * Ordered by severity (CLEAR < APPROACHING < WARNING < CRITICAL).
 */
enum class ThreatLevel {
    /** No vehicles detected */
    CLEAR,
    /** Vehicle approaching (> warningDistance) */
    APPROACHING,
    /** Vehicle close (< warningDistance, > criticalDistance) */
    WARNING,
    /** Vehicle very close (< criticalDistance) */
    CRITICAL
}

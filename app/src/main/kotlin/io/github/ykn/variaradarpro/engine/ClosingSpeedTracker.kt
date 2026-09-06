package io.github.ykn.variaradarpro.engine

import kotlin.math.abs

/**
 * Tracks closing speed from sequential nearest-distance samples at ~1 Hz.
 *
 * Uses a rolling buffer of the last [BUFFER_SIZE] samples to compute smoothed
 * closing speed. Detects target changes (vehicle passed, new vehicle appeared)
 * and resets automatically.
 */
class ClosingSpeedTracker {

    data class Sample(val distanceM: Int, val timestampMs: Long)

    companion object {
        const val BUFFER_SIZE = 3
        const val TARGET_CHANGE_THRESHOLD_M = 30
        const val FAST_APPROACH_MS = 10.0   // ~36 km/h differential
        const val HOLDING_THRESHOLD_MS = 2.0 // ~7 km/h differential

        /**
         * Calculate closing speed in m/s from a list of samples.
         * Positive = closing (getting nearer), negative = receding.
         * Returns null if fewer than 2 samples or zero time span.
         */
        fun calculateClosingSpeed(samples: List<Sample>): Double? {
            if (samples.size < 2) return null
            val first = samples.first()
            val last = samples.last()
            val timeDeltaMs = last.timestampMs - first.timestampMs
            if (timeDeltaMs <= 0) return null
            return (first.distanceM - last.distanceM).toDouble() / (timeDeltaMs / 1000.0)
        }

        /**
         * Detect if distance jump indicates a different vehicle target.
         */
        fun isTargetChange(oldDistanceM: Int, newDistanceM: Int): Boolean {
            if (newDistanceM <= 0) return true
            return abs(newDistanceM - oldDistanceM) > TARGET_CHANGE_THRESHOLD_M
        }
    }

    private val buffer = ArrayDeque<Sample>(BUFFER_SIZE)

    fun addSample(distanceM: Int, timestampMs: Long = System.currentTimeMillis()) {
        if (distanceM <= 0) {
            reset()
            return
        }

        if (buffer.isNotEmpty() && isTargetChange(buffer.last().distanceM, distanceM)) {
            reset()
        }

        buffer.addLast(Sample(distanceM, timestampMs))
        if (buffer.size > BUFFER_SIZE) {
            buffer.removeFirst()
        }
    }

    /**
     * Current smoothed closing speed in m/s (positive = closing), or null
     * if fewer than 2 samples are buffered.
     */
    fun closingSpeedMps(): Double? = calculateClosingSpeed(buffer.toList())

    /**
     * True if the vehicle is closing at >= [FAST_APPROACH_MS] m/s.
     * Requires at least 2 samples.
     */
    fun isFastApproach(): Boolean {
        val speed = calculateClosingSpeed(buffer.toList()) ?: return false
        return speed >= FAST_APPROACH_MS
    }

    /**
     * True if the vehicle is holding distance or receding (closing speed < [HOLDING_THRESHOLD_MS]).
     * Requires at least 2 samples.
     */
    fun isHoldingOrReceding(): Boolean {
        val speed = calculateClosingSpeed(buffer.toList()) ?: return false
        return speed < HOLDING_THRESHOLD_MS
    }

    fun reset() {
        buffer.clear()
    }
}

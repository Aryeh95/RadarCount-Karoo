package io.github.aryeh95.radarcount.engine

/**
 * Counts vehicles that have passed the rider.
 *
 * Uses the same rule as the Garmin MyBikeTraffic data field: when the
 * number of tracked targets drops, the vehicles that disappeared are counted
 * as passes, but only if the nearest target had come within
 * [countThresholdM] on the previous sample. A target that vanishes while
 * still far away (turned off, stopped, out of range) is not a pass.
 *
 * Not thread-safe: call [update] from a single thread.
 */
class VehiclePassCounter(
    private val countThresholdM: Int = DEFAULT_COUNT_THRESHOLD_M
) {

    companion object {
        /**
         * The Karoo radar stream is sampled at roughly 1 Hz, so a vehicle
         * closing at 30 m/s can jump from ~30 m to gone between samples.
         * 20 m is forgiving enough to catch fast passes while still
         * rejecting targets that drop out far behind the rider.
         */
        const val DEFAULT_COUNT_THRESHOLD_M = 20
    }

    var total: Int = 0
        private set

    var lap: Int = 0
        private set

    private var lastTrackedCount = 0
    private var crossedThreshold = false

    /**
     * Feed one radar sample.
     *
     * @param trackedCount number of targets currently tracked
     * @param nearestDistanceM distance to the nearest target in metres, 0 if none
     * @return number of vehicles counted as passed on this sample
     */
    fun update(trackedCount: Int, nearestDistanceM: Int): Int {
        var passed = 0
        if (trackedCount < lastTrackedCount && crossedThreshold) {
            passed = lastTrackedCount - trackedCount
            total += passed
            lap += passed
        }

        crossedThreshold = trackedCount > 0 && nearestDistanceM in 1..countThresholdM
        lastTrackedCount = trackedCount
        return passed
    }

    /**
     * Forget the current tracking state without counting anything.
     * Call when the radar disconnects so a reconnect does not count
     * vehicles that were in view before the drop.
     */
    fun clearTracking() {
        lastTrackedCount = 0
        crossedThreshold = false
    }

    fun resetLap() {
        lap = 0
    }

    /** Reset all counts and tracking state, e.g. at the start of a ride. */
    fun reset() {
        total = 0
        lap = 0
        clearTracking()
    }
}

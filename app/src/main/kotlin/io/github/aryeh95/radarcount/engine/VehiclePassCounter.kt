package io.github.aryeh95.radarcount.engine

/**
 * Counts vehicles that have passed the rider.
 *
 * Based on the rule used by the Garmin MyBikeTraffic data field: when the
 * number of tracked targets drops, the vehicles that disappeared are counted
 * as passes, provided the nearest target looked like it was actually
 * overtaking rather than turning off or dropping out of range far behind.
 *
 * The Karoo delivers roughly one radar sample per second, so a vehicle
 * closing at 25 m/s can go from 40 m to gone between two samples. The
 * counter therefore arms when either:
 *  - the nearest target has been within [closeThresholdM] at any point
 *    while tracked, or
 *  - the nearest target was closing (range decreasing) and was last seen
 *    within [closingThresholdM].
 *
 * Not thread-safe: call [update] from a single thread.
 */
class VehiclePassCounter(
    private val closeThresholdM: Int = DEFAULT_CLOSE_THRESHOLD_M,
    private val closingThresholdM: Int = DEFAULT_CLOSING_THRESHOLD_M
) {

    companion object {
        const val DEFAULT_CLOSE_THRESHOLD_M = 20
        const val DEFAULT_CLOSING_THRESHOLD_M = 60
    }

    var total: Int = 0
        private set

    var lap: Int = 0
        private set

    private var lastTrackedCount = 0
    private var lastNearestM = 0
    private var cameClose = false
    private var wasClosing = false

    /**
     * Feed one radar sample.
     *
     * @param trackedCount number of targets currently tracked
     * @param nearestDistanceM distance to the nearest target in metres, 0 if unknown
     * @return number of vehicles counted as passed on this sample
     */
    fun update(trackedCount: Int, nearestDistanceM: Int): Int {
        var passed = 0
        if (trackedCount < lastTrackedCount && isArmed()) {
            passed = lastTrackedCount - trackedCount
            total += passed
            lap += passed
        }

        if (trackedCount == 0) {
            cameClose = false
            wasClosing = false
            lastNearestM = 0
        } else if (nearestDistanceM > 0) {
            if (lastTrackedCount == 0 || lastNearestM <= 0) {
                // New nearest target: start fresh
                cameClose = false
                wasClosing = false
            } else {
                wasClosing = nearestDistanceM < lastNearestM
            }
            if (nearestDistanceM <= closeThresholdM) cameClose = true
            lastNearestM = nearestDistanceM
        }
        // nearestDistanceM == 0 with targets tracked: range unknown, keep state

        lastTrackedCount = trackedCount
        return passed
    }

    private fun isArmed(): Boolean {
        if (cameClose) return true
        return wasClosing && lastNearestM in 1..closingThresholdM
    }

    /**
     * Forget the current tracking state without counting anything.
     * Call when the radar disconnects so a reconnect does not count
     * vehicles that were in view before the drop.
     */
    fun clearTracking() {
        lastTrackedCount = 0
        lastNearestM = 0
        cameClose = false
        wasClosing = false
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

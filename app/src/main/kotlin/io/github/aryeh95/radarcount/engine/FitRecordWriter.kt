package io.github.aryeh95.radarcount.engine

import kotlin.math.roundToInt

/**
 * Turns one-per-second radar samples into the MyBikeTraffic developer field
 * values for a FIT record message. Pure logic, no Karoo SDK, so the record
 * shapes mybiketraffic.com depends on can be unit tested.
 *
 * The site splits cars by looking at `radar_ranges`: a car is a run of
 * consecutive non-zero values that ends below [passCloseM] and is followed
 * by a 0. The Karoo SDK only lets us write one value per field (the
 * nearest target), so the writer has to make sure every counted pass
 * produces exactly one such run:
 *
 * - A car counted while its run is still open and already inside
 *   [passCloseM] gets a single 0 on the record where the count arrives,
 *   even if a new car is already the nearest target. The live value
 *   resumes on the next record.
 * - A car counted after its run already closed under [passCloseM] needs
 *   nothing: the site has it. Each closed run can stand in for one car
 *   only, and a marker's own 3 m record never does.
 * - Any other counted pass (a car that dropped off the radar farther out,
 *   or a second car counted in the same second as the first) gets the
 *   Garmin marker: one record at [passRangeM] then one at 0, once per car.
 */
class FitRecordWriter(
    private val passCloseM: Double = 10.0,
    private val passRangeM: Double = 3.0,
    private val maxSpeed: Double = 254.0
) {
    companion object {
        /** Sentinels used by the Garmin MyBikeTraffic field when the radar is off. */
        const val RANGE_RADAR_OFF = -1.0
        const val SPEED_RADAR_OFF = 255.0

        /** m/s to the rider's speed unit, rounded, never negative. */
        fun toUserSpeedUnits(metersPerSecond: Double, imperial: Boolean): Int {
            val v = if (imperial) metersPerSecond * 2.23694 else metersPerSecond * 3.6
            return v.roundToInt().coerceAtLeast(0)
        }
    }

    /** What the engine knows at the moment a record is written. */
    data class Sample(
        val connected: Boolean,
        val vehicleCount: Int,
        val nearestM: Int,
        val passTotal: Int,
        val closingMps: Double,
        val riderMps: Double,
        val imperial: Boolean
    ) {
        val tracked: Boolean get() = vehicleCount > 0
    }

    /** MyBikeTraffic record fields 0, 1, 5 and 6. */
    data class Record(
        val rangeM: Double,
        val speedMps: Double,
        val passingSpeed: Int,
        val passingSpeedAbs: Int
    )

    private var lastTotal = -1
    /** Markers still to write, two records each (3 m then 0). */
    private var markersPending = 0
    private var markerLeft = 0
    private var forceZero = false
    /** Final non-zero live range written, and whether a 0 has been written since. */
    private var lastRunEndRange = -1.0
    private var runOpen = false
    /** A live run closed under [passCloseM] and no count has claimed it yet. */
    private var closedRunUnclaimed = false
    /** The open live run has already been claimed by a count (its closing 0 is being forced). */
    private var openRunClaimed = false
    private var lastSpeedMps = 0.0
    private var lastPassingSpeed = 0
    private var lastPassingSpeedAbs = 0

    fun next(s: Sample): Record {
        if (s.passTotal > lastTotal && lastTotal >= 0) {
            var newPasses = s.passTotal - lastTotal
            val runInsideClose = lastRunEndRange > 0.0 && lastRunEndRange < passCloseM
            if (runOpen && runInsideClose && !openRunClaimed) {
                forceZero = true
                openRunClaimed = true
                newPasses--
            } else if (closedRunUnclaimed) {
                closedRunUnclaimed = false // the site already has this car
                newPasses--
            }
            markersPending += newPasses
        }
        lastTotal = s.passTotal

        if (!s.connected) {
            lastRunEndRange = -1.0
            runOpen = false
            closedRunUnclaimed = false
            openRunClaimed = false
            forceZero = false
            markerLeft = 0
            markersPending = 0
            return Record(RANGE_RADAR_OFF, SPEED_RADAR_OFF, 0, 0)
        }
        if (markerLeft == 0 && markersPending > 0 && !forceZero) {
            markersPending--
            markerLeft = 2
        }

        val passingSpeed = if (s.tracked) toUserSpeedUnits(s.closingMps, s.imperial) else 0
        val passingSpeedAbs = if (passingSpeed > 0) passingSpeed + toUserSpeedUnits(s.riderMps, s.imperial) else 0

        var live = false
        val record = when {
            forceZero -> {
                forceZero = false
                Record(0.0, 0.0, 0, 0)
            }
            markerLeft == 2 -> {
                markerLeft = 1
                Record(passRangeM, lastSpeedMps.coerceIn(0.0, maxSpeed), lastPassingSpeed, lastPassingSpeedAbs)
            }
            markerLeft == 1 -> {
                markerLeft = 0
                Record(0.0, 0.0, 0, 0)
            }
            s.tracked -> {
                live = true
                lastSpeedMps = s.closingMps
                lastPassingSpeed = passingSpeed
                lastPassingSpeedAbs = passingSpeedAbs
                Record(s.nearestM.toDouble(), s.closingMps.coerceIn(0.0, maxSpeed), passingSpeed, passingSpeedAbs)
            }
            else -> Record(0.0, 0.0, 0, 0)
        }

        if (live) {
            lastRunEndRange = record.rangeM
            runOpen = true
            closedRunUnclaimed = false
            openRunClaimed = false
        } else if (runOpen) {
            // A 0 (or a marker) just closed the live run. It can stand in for
            // one car, unless a count already claimed it while it was open.
            closedRunUnclaimed = !openRunClaimed && lastRunEndRange > 0.0 && lastRunEndRange < passCloseM &&
                markerLeft == 0 && record.rangeM == 0.0
            runOpen = false
            openRunClaimed = false
        }
        return record
    }
}

package io.github.aryeh95.radarcount.engine

import kotlin.math.abs

/**
 * Tracks individual radar targets across packets and decides when one has
 * passed the rider.
 *
 * Why not just watch the nearest range: the Karoo delivers packets in
 * bursts, sometimes without any ranges at all ("threat, no targets"), and
 * the target count jitters (one car can show up as 1, 3, 3, 2, 1 targets in
 * five seconds). Counting every drop in the target count both misses real
 * passes and overcounts clusters.
 *
 * Instead each reported range is matched to a track from the previous
 * packet (closest range within a tolerance that grows with elapsed time).
 * A track counts as a pass if it was observed at least [minSamples] times
 * and either came within [closeThresholdM] or was closing and last seen
 * within [closingThresholdM]. It must also have looked like a pass at some
 * point: the radar flagged it as approaching fast (threat level 2 or
 * more), or it was closing at [minPassClosingMps] or more when last seen,
 * or it was seen alongside at [alongsideRangeM]. A car that sits behind
 * the rider at the rider's speed farther back than that, and then drops
 * off the radar without doing any of those, is a follower, not a pass.
 *
 * [alongsideRangeM] is set by the beam, not by how fast the car was
 * going. The radar is Doppler: it reports a target only while the range
 * is changing, so a car matching the rider's speed vanishes without ever
 * passing, and a genuine pass can creep up with no closing speed and no
 * threat flag. What a pass cannot avoid is driving the range down. As the
 * car draws level its bearing swings out of the rear beam, so with the
 * metre or two of lateral separation an overtake actually uses, the last
 * range the radar reports is a few metres, and the sensor's own floor is
 * about three. Only an overtake gets that close behind a moving bicycle,
 * so a car seen there counts whatever its speed. Nine metres matches
 * mybiketraffic.com, which counts a run of ranges that ends under ten,
 * so the device and the site agree; the price is a car that settles
 * nine metres back and goes quiet, which counts once as if it passed.
 *
 * A car that first appears inside [alongsideRangeM] counts on a single
 * sample. It was following at the rider's speed, invisible to a Doppler
 * radar, and has just pulled out; the beam loses it within a few
 * hundred milliseconds, which is fewer packets than [minSamples] asks
 * for. A queue's lead car is missed without this.
 *
 * Turns are handled with the rider's heading, when [updateHeading] is fed:
 * a target that was already behind the rider before a turn through
 * [turnThresholdDeg] and vanishes during or just after it has gone
 * straight on, not past, so it is not counted. A target first seen close
 * ([crossingFirstRangeM] or less) and briefly right after a turn is a car
 * crossing the radar cone on the road just left. A car on the new road
 * that appears at normal radar range and passes is counted as usual.
 *
 * A track that has come within [closeThresholdM] is counted as soon as it
 * has been missing for [closeLostMs]: a car that close and then gone has
 * passed, and the radar cannot see it once it is alongside. The counted
 * track lingers as a ghost until [lostMs] so a range that reappears close
 * to it (a radar dropout) re-attaches without counting again. Tracks that
 * vanish farther out keep the full [lostMs] wait, because there a dropout
 * and a pass look alike.
 *
 * The closing speed of the nearest track is estimated from its range
 * history over at least [minSpeedSpanMs], which filters the burst-timing
 * artefacts that produced nonsense speeds before. The estimate freezes
 * once the car is inside [speedFreezeRangeM]: the last few samples before
 * a pass are the noisiest, so the speed at the pass is the approach speed.
 *
 * Not thread-safe: call from a single thread.
 */
class TargetTracker(
    @Volatile var closeThresholdM: Int = 20,
    @Volatile var closingThresholdM: Int = 60,
    private val minSamples: Int = 2,
    private val lostMs: Long = 1_800L,
    private val closeLostMs: Long = 700L,
    private val minSpeedSpanMs: Long = 1_000L,
    private val speedWindowMs: Long = 3_000L,
    private val maxSpeedMps: Double = 40.0,
    private val speedFreezeRangeM: Int = 10,
    private val minPassClosingMps: Double = 2.5,
    /** Range at which a car is alongside: set by the beam edge, not by speed. */
    private val alongsideRangeM: Int = 9,
    private val fastThreatLevel: Int = 2,
    private val turnThresholdDeg: Double = 45.0,
    private val turnWindowMs: Long = 6_000L,
    private val turnHoldMs: Long = 4_000L,
    private val afterTurnMs: Long = 12_000L,
    private val crossingFirstRangeM: Int = 30
) {

    private class Track(range: Int, nowMs: Long) {
        var range = range
        val firstRange = range
        var minRange = range
        var firstSeenMs = nowMs
        var lastSeenMs = nowMs
        var samples = 1
        var closing = false
        /** Pass decision already made (counted or rejected); the track is a ghost. */
        var resolved = false
        /** Closing speed captured when the car came inside the freeze range. */
        var frozenSpeedMps: Double? = null
        /** Closing speed estimate as of the last observation. */
        var lastSpeedMps = 0.0
        /** Highest radar threat level reported while this track was seen. */
        var maxThreat = 0
        /** (timestampMs, rangeM) history for speed estimation */
        val history = ArrayDeque<Pair<Long, Int>>()

        init {
            history.addLast(nowMs to range)
        }

        fun observe(newRange: Int, nowMs: Long, threat: Int, freezeRangeM: Int, estimate: (ArrayDeque<Pair<Long, Int>>) -> Double) {
            // Ignore duplicate packets delivered within a burst
            if (nowMs - lastSeenMs >= MIN_SAMPLE_GAP_MS) {
                samples++
            }
            if (frozenSpeedMps == null && newRange < freezeRangeM) {
                val v = estimate(history)
                if (v > 0.0) frozenSpeedMps = v
            }
            closing = newRange < range
            range = newRange
            if (newRange < minRange) minRange = newRange
            lastSeenMs = nowMs
            history.addLast(nowMs to newRange)
            if (threat > maxThreat) maxThreat = threat
            lastSpeedMps = frozenSpeedMps ?: estimate(history)
        }

        fun isPass(
            closeThresholdM: Int, closingThresholdM: Int, minSamples: Int,
            minClosingMps: Double, alongsideM: Int, fastThreat: Int
        ): Boolean {
            if (samples < minSamples && firstRange > alongsideM) return false
            val lookedLikePass = maxThreat >= fastThreat || lastSpeedMps >= minClosingMps || minRange <= alongsideM
            if (!lookedLikePass) return false
            if (minRange <= closeThresholdM) return true
            return closing && range <= closingThresholdM
        }
    }

    companion object {
        private const val MIN_SAMPLE_GAP_MS = 250L
        /** Heading drift that marks the start of a turn. */
        private const val TURN_START_DEG = 10.0
        /** Base matching tolerance; ranges are quantised to ~3 m */
        private const val MATCH_BASE_M = 12.0
        /** How much farther back than its last range a ghost may re-attach a target. */
        private const val GHOST_BEHIND_M = 2
        /** Extra tolerance per second elapsed (a relative speed of 30 m/s) */
        private const val MATCH_PER_SEC_M = 30.0
    }

    private val tracks = ArrayList<Track>()

    /** (timestampMs, heading in degrees) over the last [turnWindowMs]. */
    private val headings = ArrayDeque<Pair<Long, Double>>()
    /** A detected turn: when the heading started to swing, and when it passed [turnThresholdDeg]. */
    private class Turn(val startMs: Long, val detectedMs: Long)
    private val turns = ArrayDeque<Turn>()

    /** Diagnostic sink: one line per track decision or ghost re-attach. */
    @Volatile var trace: ((String) -> Unit)? = null

    /** Diagnostics for ride-file analysis: how often each veto fires. */
    var turnCount = 0
        private set
    var rejectedTurnedAway = 0
        private set
    var rejectedCrossingAfterTurn = 0
        private set
    var rejectedNotPass = 0
        private set

    /** Feed the rider's heading (0-360). Call whenever the Karoo reports it. */
    fun updateHeading(degrees: Double, nowMs: Long) {
        headings.addLast(nowMs to degrees)
        while (headings.size > 1 && nowMs - headings.first().first > turnWindowMs) headings.removeFirst()
        val oldest = headings.first().second
        // Until the window has filled, "oldest" is the first sample of the
        // ride and a heading fix settling in reads as a turn.
        val windowFull = nowMs - headings.first().first >= turnWindowMs
        if (windowFull && angleDiff(oldest, degrees) >= turnThresholdDeg) {
            if (turns.isEmpty() || nowMs - turns.last().detectedMs > 1_000L) {
                // The turn started at the last sample still on the old heading.
                val start = headings.lastOrNull { angleDiff(oldest, it.second) < TURN_START_DEG }?.first ?: headings.first().first
                turns.addLast(Turn(start, nowMs))
                turnCount++
            }
        }
        while (turns.isNotEmpty() && nowMs - turns.first().detectedMs > afterTurnMs + turnWindowMs + 60_000L) turns.removeFirst()
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val d = Math.abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Was a turn detected within [fromMs, toMs]? */
    private fun turnedBetween(fromMs: Long, toMs: Long): Boolean = turns.any { it.detectedMs in fromMs..toMs }

    /** Was a turn detected within [fromMs, toMs] that had started after [trackedSinceMs]? */
    private fun turnedAwayFrom(trackedSinceMs: Long, fromMs: Long, toMs: Long): Boolean =
        turns.any { it.detectedMs in fromMs..toMs && it.startMs > trackedSinceMs }

    /** Number of currently tracked targets (ghosts of counted cars excluded). */
    val activeCount: Int get() = tracks.count { !it.resolved }

    /**
     * Feed one radar packet.
     *
     * @param rangesM ranges of all reported targets in metres (may be empty
     *                when the radar reports a threat without ranges)
     * @param nowMs   packet time in milliseconds
     * @param threat  the radar's threat level for this packet (0-3)
     * @return number of targets that ended as passes on this packet
     */
    fun update(rangesM: List<Int>, nowMs: Long, threat: Int = 0): Int {
        // --- match reported ranges to existing tracks (closest pair first) ---
        val unmatchedRanges = rangesM.filter { it > 0 }.toMutableList()
        val matched = HashSet<Track>()

        while (unmatchedRanges.isNotEmpty()) {
            var bestTrack: Track? = null
            var bestRange = -1
            var bestDelta = Double.MAX_VALUE
            for (t in tracks) {
                if (t in matched) continue
                val dtSec = (nowMs - t.lastSeenMs).coerceAtLeast(0) / 1000.0
                // A ghost only re-attaches a range right next to where the
                // car vanished; a new car farther out must start a new track.
                val tol = if (t.resolved) MATCH_BASE_M else MATCH_BASE_M + MATCH_PER_SEC_M * dtSec
                for (r in unmatchedRanges) {
                    // Prefer the interpretation where targets approach: a
                    // range increase is penalised so a new closer car is
                    // not mistaken for an old one jumping backwards.
                    val d = if (r > t.range) (r - t.range) * 2.0 else (t.range - r).toDouble()
                    // A counted car's ghost only takes back a dropout, which
                    // reappears where it was or nearer. A range one bin or
                    // more farther back is the next car in the queue.
                    if (t.resolved && r - t.range > GHOST_BEHIND_M) continue
                    if (abs(r - t.range) <= tol && d < bestDelta) {
                        bestDelta = d; bestTrack = t; bestRange = r
                    }
                }
            }
            if (bestTrack == null) break
            if (bestTrack.resolved) trace?.invoke("REATTACH,${nowMs},${bestTrack.range},${bestRange},${nowMs - bestTrack.lastSeenMs}")
            bestTrack.observe(bestRange, nowMs, threat, speedFreezeRangeM, ::estimateClosingSpeed)
            matched.add(bestTrack)
            unmatchedRanges.remove(bestRange)
        }

        // --- leftover ranges become new tracks ---
        for (r in unmatchedRanges) {
            tracks.add(Track(r, nowMs).also { it.maxThreat = threat })
        }

        // --- decide passes and expire tracks not seen recently ---
        var passed = 0
        val it = tracks.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t in matched) continue
            val missingMs = nowMs - t.lastSeenMs
            if (!t.resolved) {
                val closeCar = t.minRange <= closeThresholdM
                val decideNow = missingMs > (if (closeCar) closeLostMs else lostMs)
                if (decideNow) {
                    // Was behind the rider before the turn and vanished during or just after it: went straight on.
                    val turnedAway = turnedAwayFrom(t.firstSeenMs, t.lastSeenMs - turnHoldMs, nowMs)
                    // Brief, first seen close, right after a turn: a car crossing the cone on the road just left.
                    val crossingAfterTurn = t.samples <= 3 && t.firstRange <= crossingFirstRangeM &&
                        turnedBetween(t.firstSeenMs - afterTurnMs, t.firstSeenMs)
                    val looksLikePass = t.isPass(closeThresholdM, closingThresholdM, minSamples, minPassClosingMps, alongsideRangeM, fastThreatLevel)
                    val pass = !turnedAway && !crossingAfterTurn && looksLikePass
                    trace?.invoke(
                        "TRACK,${nowMs},${t.firstRange},${t.minRange},${t.range},${t.samples},${t.lastSeenMs - t.firstSeenMs}," +
                            "${t.maxThreat},${"%.2f".format(t.lastSpeedMps)},${if (pass) "pass" else if (turnedAway) "turnedAway" else if (crossingAfterTurn) "crossing" else "notPass"}"
                    )
                    if (pass) passed++
                    else if (turnedAway) rejectedTurnedAway++
                    else if (crossingAfterTurn) rejectedCrossingAfterTurn++
                    else rejectedNotPass++
                    t.resolved = true
                }
            }
            if (missingMs > lostMs) it.remove()
        }

        // trim speed histories
        for (t in tracks) {
            while (t.history.size > 1 && nowMs - t.history.first().first > speedWindowMs) {
                t.history.removeFirst()
            }
        }

        return passed
    }

    private fun nearestTrack(): Track? = tracks.filter { !it.resolved }.minByOrNull { it.range }

    /** Range of the nearest tracked target in metres, or 0 if none. */
    fun nearestRangeM(): Int = nearestTrack()?.range ?: 0

    /**
     * Estimated closing speed of the nearest target in m/s (positive =
     * approaching), or null when there is no target or not yet enough
     * history to fit a slope. Null means "not known yet" and is distinct
     * from a real 0, which means the car is holding station or falling
     * back. Once the car is inside [speedFreezeRangeM] the estimate holds
     * at its approach value instead of chasing the noisy last samples.
     */
    fun nearestClosingSpeedMps(): Double? {
        val t = nearestTrack() ?: return null
        t.frozenSpeedMps?.let { return it }
        if (!hasSpeedEstimate(t.history)) return null
        return estimateClosingSpeed(t.history)
    }

    /** True once [h] spans enough time to fit a slope. */
    private fun hasSpeedEstimate(h: ArrayDeque<Pair<Long, Int>>): Boolean =
        h.size >= 2 && h.last().first - h.first().first >= minSpeedSpanMs

    private fun estimateClosingSpeed(h: ArrayDeque<Pair<Long, Int>>): Double {
        if (h.size < 2) return 0.0
        val spanMs = h.last().first - h.first().first
        if (spanMs < minSpeedSpanMs) return 0.0
        // Least-squares slope of range over time; less sensitive to
        // per-sample jitter than the first/last endpoints.
        val t0 = h.first().first
        val n = h.size
        var sumT = 0.0; var sumR = 0.0; var sumTT = 0.0; var sumTR = 0.0
        for ((ts, r) in h) {
            val x = (ts - t0) / 1000.0
            sumT += x; sumR += r; sumTT += x * x; sumTR += x * r
        }
        val denom = n * sumTT - sumT * sumT
        if (denom <= 0.0) return 0.0
        val slope = (n * sumTR - sumT * sumR) / denom
        // + 0.0 normalises the -0.0 that a flat slope produces.
        return (-slope).coerceIn(0.0, maxSpeedMps) + 0.0
    }

    /**
     * Drop all tracks without counting anything (radar disconnected or
     * streaming stopped).
     */
    fun clear() {
        tracks.clear()
    }

    /** Reset the diagnostic counters (start of a ride). */
    fun resetDiagnostics() {
        turnCount = 0
        rejectedTurnedAway = 0
        rejectedCrossingAfterTurn = 0
        rejectedNotPass = 0
    }

    /** Test hook: has a turn been detected at or after [sinceMs]? */
    internal fun turnedSince(sinceMs: Long): Boolean = turns.any { it.detectedMs >= sinceMs }
}

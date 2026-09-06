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
 * A track that is not seen for [lostMs] ends. It counts as a pass if it
 * was observed at least [minSamples] times and either came within
 * [closeThresholdM] or was closing and last seen within [closingThresholdM].
 *
 * The closing speed of the nearest track is estimated from its range
 * history over at least [minSpeedSpanMs], which filters the burst-timing
 * artefacts that produced nonsense speeds before.
 *
 * Not thread-safe: call from a single thread.
 */
class TargetTracker(
    private val closeThresholdM: Int = 20,
    private val closingThresholdM: Int = 60,
    private val minSamples: Int = 2,
    private val lostMs: Long = 1_800L,
    private val minSpeedSpanMs: Long = 1_500L,
    private val speedWindowMs: Long = 5_000L,
    private val maxSpeedMps: Double = 40.0
) {

    private class Track(range: Int, nowMs: Long) {
        var range = range
        var minRange = range
        var firstSeenMs = nowMs
        var lastSeenMs = nowMs
        var samples = 1
        var closing = false
        /** (timestampMs, rangeM) history for speed estimation */
        val history = ArrayDeque<Pair<Long, Int>>()

        init {
            history.addLast(nowMs to range)
        }

        fun observe(newRange: Int, nowMs: Long) {
            // Ignore duplicate packets delivered within a burst
            if (nowMs - lastSeenMs >= MIN_SAMPLE_GAP_MS) {
                samples++
            }
            closing = newRange < range
            range = newRange
            if (newRange < minRange) minRange = newRange
            lastSeenMs = nowMs
            history.addLast(nowMs to newRange)
        }

        fun isPass(closeThresholdM: Int, closingThresholdM: Int, minSamples: Int): Boolean {
            if (samples < minSamples) return false
            if (minRange <= closeThresholdM) return true
            return closing && range <= closingThresholdM
        }
    }

    companion object {
        private const val MIN_SAMPLE_GAP_MS = 250L
        /** Base matching tolerance; ranges are quantised to ~3 m */
        private const val MATCH_BASE_M = 12.0
        /** Extra tolerance per second elapsed (a relative speed of 30 m/s) */
        private const val MATCH_PER_SEC_M = 30.0
    }

    private val tracks = ArrayList<Track>()

    /** Number of currently tracked targets. */
    val activeCount: Int get() = tracks.size

    /**
     * Feed one radar packet.
     *
     * @param rangesM ranges of all reported targets in metres (may be empty
     *                when the radar reports a threat without ranges)
     * @param nowMs   packet time in milliseconds
     * @return number of targets that ended as passes on this packet
     */
    fun update(rangesM: List<Int>, nowMs: Long): Int {
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
                val tol = MATCH_BASE_M + MATCH_PER_SEC_M * dtSec
                for (r in unmatchedRanges) {
                    // Prefer the interpretation where targets approach: a
                    // range increase is penalised so a new closer car is
                    // not mistaken for an old one jumping backwards.
                    val d = if (r > t.range) (r - t.range) * 2.0 else (t.range - r).toDouble()
                    if (abs(r - t.range) <= tol && d < bestDelta) {
                        bestDelta = d; bestTrack = t; bestRange = r
                    }
                }
            }
            if (bestTrack == null) break
            bestTrack.observe(bestRange, nowMs)
            matched.add(bestTrack)
            unmatchedRanges.remove(bestRange)
        }

        // --- leftover ranges become new tracks ---
        for (r in unmatchedRanges) {
            tracks.add(Track(r, nowMs))
        }

        // --- expire tracks not seen recently ---
        var passed = 0
        val it = tracks.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t in matched || nowMs - t.lastSeenMs <= lostMs) continue
            if (t.isPass(closeThresholdM, closingThresholdM, minSamples)) passed++
            it.remove()
        }

        // trim speed histories
        for (t in tracks) {
            while (t.history.size > 1 && nowMs - t.history.first().first > speedWindowMs) {
                t.history.removeFirst()
            }
        }

        return passed
    }

    /** Range of the nearest tracked target in metres, or 0 if none. */
    fun nearestRangeM(): Int = tracks.minOfOrNull { it.range } ?: 0

    /**
     * Estimated closing speed of the nearest target in m/s (positive =
     * approaching). Returns 0 if there is not yet enough history.
     */
    fun nearestClosingSpeedMps(): Double {
        val t = tracks.minByOrNull { it.range } ?: return 0.0
        val first = t.history.firstOrNull() ?: return 0.0
        val last = t.history.lastOrNull() ?: return 0.0
        val spanMs = last.first - first.first
        if (spanMs < minSpeedSpanMs) return 0.0
        val speed = (first.second - last.second) / (spanMs / 1000.0)
        return speed.coerceIn(0.0, maxSpeedMps)
    }

    /**
     * Drop all tracks without counting anything (radar disconnected or
     * streaming stopped).
     */
    fun clear() {
        tracks.clear()
    }
}

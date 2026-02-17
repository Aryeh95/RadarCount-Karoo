package io.github.ykn.variaradarpro.engine

/**
 * Tracks traffic density over a rolling [WINDOW_SIZE]-second window.
 *
 * Dense traffic is defined as an average vehicle count >= [DENSE_THRESHOLD]
 * with at least [MIN_SAMPLES] samples collected (avoids false positives
 * during the first seconds after connecting).
 */
class TrafficDensityTracker {

    companion object {
        const val WINDOW_SIZE = 30
        const val DENSE_THRESHOLD = 2.0
        const val MIN_SAMPLES = 10

        fun calculateAverage(samples: Collection<Int>): Double {
            if (samples.isEmpty()) return 0.0
            return samples.sum().toDouble() / samples.size
        }

        fun isDense(average: Double, sampleCount: Int): Boolean {
            return sampleCount >= MIN_SAMPLES && average >= DENSE_THRESHOLD
        }
    }

    private val buffer = ArrayDeque<Int>(WINDOW_SIZE)

    fun addSample(vehicleCount: Int) {
        buffer.addLast(vehicleCount)
        if (buffer.size > WINDOW_SIZE) {
            buffer.removeFirst()
        }
    }

    fun isDenseTraffic(): Boolean {
        return isDense(calculateAverage(buffer), buffer.size)
    }

    fun reset() {
        buffer.clear()
    }
}

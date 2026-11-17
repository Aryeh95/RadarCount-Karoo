package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The radar to the FIT file end to end: packets into [TargetTracker], and once a second the
 * tracked ranges and the pass total into [FitRecordWriter], as the extension's FIT ticker does.
 * mybiketraffic.com counts from the records, so it must count what the device counted.
 */
@DisplayName("Radar to FIT file")
class FitPathTest {

    private val tracker = TargetTracker()
    private val writer = FitRecordWriter()
    private val ranges = ArrayList<Double>()
    private var now = 100_000L
    private var passes = 0
    private var nextTickMs = now + TICK_MS

    /** Feeds one packet [dtMs] after the last, writing a record at each whole second passed. */
    private fun packet(vararg rangesM: Int, dtMs: Long = PACKET_MS) {
        now += dtMs
        while (nextTickMs <= now) {
            tick()
            nextTickMs += TICK_MS
        }
        passes += tracker.update(rangesM.toList(), now, threat = if (rangesM.isEmpty()) 0 else 2)
    }

    private fun tick() {
        val tracked = tracker.trackedRangesM().sorted()
        val record = writer.next(FitRecordWriter.Sample(
            connected = true,
            vehicleCount = tracked.size,
            nearestM = tracked.firstOrNull() ?: 0,
            passTotal = passes,
            closingMps = 10.0,
            riderMps = 5.0,
            imperial = false,
        ))
        ranges.add(record.rangeM)
    }

    /** Several seconds of empty packets, so every car is decided and written. */
    private fun quiet() = repeat(40) { packet() }

    /** As in FitRecordWriterTest: runs of non-zero ranges ending under 10 m, followed by a 0. */
    private fun siteCars(): Int {
        var cars = 0
        var run = ArrayList<Double>()
        for (v in ranges + 0.0) {
            if (v > 0) run.add(v) else { if (run.isNotEmpty() && run.last() < 10) cars++; run = ArrayList() }
        }
        return cars
    }

    private fun approach(vararg rangesM: Int) = rangesM.forEach { r -> repeat(4) { packet(r) } }

    @Test
    @DisplayName("a counted car's echo coming back alongside is one car on the site too")
    fun ghostEchoIsOneCar() {
        quiet()
        approach(46, 34, 25, 12, 6, 3)
        packet(dtMs = 800)                     // gone: counted
        repeat(4) { packet(3) }                // its body echoes back at 3 m for a second
        quiet()
        assertThat(passes).isEqualTo(1)
        assertThat(siteCars()).isEqualTo(1)
    }

    @Test
    @DisplayName("an empty packet while a car is close does not split it in two")
    fun emptyPacketWhileCloseIsOneCar() {
        quiet()
        approach(46, 34, 25, 12)
        repeat(5) { packet(6) }                // written at 6 m at least once
        now = nextTickMs - PACKET_MS - 10      // the next packet lands just before a record...
        packet()                               // ...and is empty: "threat, no targets"
        repeat(4) { packet(6) }
        repeat(4) { packet(3) }
        quiet()
        assertThat(passes).isEqualTo(1)
        assertThat(siteCars()).isEqualTo(1)
    }

    @Test
    @DisplayName("two cars in a row are two cars on the site")
    fun twoCars() {
        quiet()
        approach(46, 34, 25, 12, 6, 3)
        quiet()
        approach(52, 40, 28, 15, 7, 3)
        quiet()
        assertThat(passes).isEqualTo(2)
        assertThat(siteCars()).isEqualTo(2)
    }

    private companion object {
        const val PACKET_MS = 250L
        const val TICK_MS = 1_000L
    }
}

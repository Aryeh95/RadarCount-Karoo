package io.github.aryeh95.radarcount.engine

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FitRecordWriter")
class FitRecordWriterTest {

    private lateinit var writer: FitRecordWriter
    private val ranges = ArrayList<Double>()
    private val speeds = ArrayList<Int>()
    private var total = 0

    @BeforeEach
    fun setUp() {
        writer = FitRecordWriter()
        ranges.clear(); speeds.clear(); total = 0
    }

    /** One record with the nearest car at [nearestM] (0 = nothing tracked). */
    private fun rec(nearestM: Int, closingMps: Double = 10.0, connected: Boolean = true, riderMps: Double = 5.0): FitRecordWriter.Record {
        val r = writer.next(FitRecordWriter.Sample(
            connected = connected,
            vehicleCount = if (nearestM > 0) 1 else 0,
            nearestM = nearestM,
            passTotal = total,
            closingMps = closingMps,
            riderMps = riderMps,
            imperial = false
        ))
        ranges.add(r.rangeM); speeds.add(r.passingSpeed)
        return r
    }

    /** The pass counter ticked before this record. */
    private fun pass() { total++ }

    /** How mybiketraffic.com splits cars: runs of non-zero ranges ending under 10 m, followed by a 0. */
    private fun siteCars(): Int {
        var cars = 0
        var run = ArrayList<Double>()
        for (v in ranges + 0.0) {
            if (v > 0) run.add(v) else { if (run.isNotEmpty() && run.last() < 10) cars++; run = ArrayList() }
        }
        return cars
    }

    @Test
    @DisplayName("a close pass counted as the car vanishes closes the run with a single 0")
    fun closePass() {
        rec(0)
        for (r in listOf(40, 25, 12, 6)) rec(r)
        pass(); rec(0)
        rec(0)
        assertThat(ranges).containsExactly(0.0, 40.0, 25.0, 12.0, 6.0, 0.0, 0.0).inOrder()
        assertThat(siteCars()).isEqualTo(1)
    }

    @Test
    @DisplayName("a pass counted while the next car is already nearest still writes the 0 first")
    fun nextCarAlreadyNearest() {
        rec(0)
        for (r in listOf(40, 25, 12, 6)) rec(r)
        pass(); rec(50)
        for (r in listOf(35, 20, 8)) rec(r)
        pass(); rec(0)
        assertThat(ranges).containsExactly(0.0, 40.0, 25.0, 12.0, 6.0, 0.0, 35.0, 20.0, 8.0, 0.0).inOrder()
        assertThat(siteCars()).isEqualTo(2)
    }

    @Test
    @DisplayName("a pass counted one record after the run already closed adds nothing")
    fun lateCountAfterClosedRun() {
        rec(0)
        for (r in listOf(40, 25, 12, 6)) rec(r)
        rec(0)
        pass(); rec(0)
        rec(0)
        assertThat(ranges).containsExactly(0.0, 40.0, 25.0, 12.0, 6.0, 0.0, 0.0, 0.0).inOrder()
        assertThat(siteCars()).isEqualTo(1)
    }

    @Test
    @DisplayName("a pass counted two records after the run closed adds nothing either")
    fun laterCountAfterClosedRun() {
        rec(0)
        for (r in listOf(40, 25, 12, 6)) rec(r)
        rec(0); rec(0)
        pass(); rec(0)
        assertThat(siteCars()).isEqualTo(1)
        assertThat(ranges.count { it == 3.0 }).isEqualTo(0)
    }

    @Test
    @DisplayName("a far pass gets the 3 m then 0 marker so the site counts it")
    fun farPassMarker() {
        rec(0)
        for (r in listOf(120, 85, 45)) rec(r)
        rec(0); rec(0)
        pass(); rec(0)
        rec(0)
        assertThat(ranges).containsExactly(0.0, 120.0, 85.0, 45.0, 0.0, 0.0, 3.0, 0.0).inOrder()
        assertThat(siteCars()).isEqualTo(1)
    }

    @Test
    @DisplayName("the marker carries the speed of the car it stands for")
    fun markerSpeed() {
        rec(0)
        for (r in listOf(120, 85, 45)) rec(r, closingMps = 15.0)
        rec(0); rec(0)
        pass()
        val marker = rec(0)
        assertThat(marker.rangeM).isEqualTo(3.0)
        assertThat(marker.passingSpeed).isEqualTo(54) // 15 m/s in km/h
        assertThat(marker.passingSpeedAbs).isEqualTo(54 + 18)
        assertThat(rec(0).passingSpeed).isEqualTo(0)
    }

    @Test
    @DisplayName("radar off writes the Garmin sentinels and forgets the run")
    fun radarOff() {
        for (r in listOf(40, 25, 12, 6)) rec(r)
        val off = rec(0, connected = false)
        assertThat(off.rangeM).isEqualTo(FitRecordWriter.RANGE_RADAR_OFF)
        assertThat(off.speedMps).isEqualTo(FitRecordWriter.SPEED_RADAR_OFF)
        pass(); rec(0); rec(0)
        // no run was open when the count arrived, so a marker is written
        assertThat(ranges.takeLast(2)).containsExactly(3.0, 0.0).inOrder()
    }

    @Test
    @DisplayName("a mixed sequence of close and far passes counts the same on the site as on the device")
    fun mixedSequence() {
        rec(0)
        for (r in listOf(60, 40, 20, 8)) rec(r)
        pass(); rec(0)                              // close, immediate
        for (r in listOf(130, 90, 55)) rec(r)
        rec(0); rec(0); pass(); rec(0)              // far, late
        for (r in listOf(45, 30, 15, 5)) rec(r)
        pass(); rec(70)                             // close, next car already there
        for (r in listOf(50, 30, 12, 4)) rec(r)
        rec(0); pass(); rec(0)                      // close, count one record late
        rec(0)
        assertThat(total).isEqualTo(4)
        assertThat(siteCars()).isEqualTo(4)
    }

    @Test
    @DisplayName("a count arriving right after a marker gets its own marker, not skipped")
    fun countAfterMarkerNotSkipped() {
        // Seen on a ride: marker written for car 1 at t=27, car 2 counted at t=29 while
        // car 3 was already nearest; the 3 m marker must not pass for car 2's closed run.
        rec(0)
        for (r in listOf(40, 25, 15)) rec(r)
        rec(0); rec(0)
        pass(); rec(6); rec(18)          // marker 3,0 written; car 3 appears meanwhile
        pass(); rec(12); rec(37)         // car 2's marker must not be skipped
        for (r in listOf(30, 12, 4)) rec(r)
        pass(); rec(0)                   // car 3
        rec(0)
        assertThat(total).isEqualTo(3)
        assertThat(siteCars()).isEqualTo(3)
    }

    @Test
    @DisplayName("two cars counted in the same second both get a marker")
    fun twoCountsOneRecord() {
        rec(0)
        for (r in listOf(31, 28, 21, 18, 15, 12, 9)) rec(r)   // car A nearest, car B behind it
        rec(25)                                             // car C now nearest
        pass(); pass(); rec(0)                              // A and B counted together
        rec(0); rec(0); rec(0)
        assertThat(ranges.takeLast(5)).containsExactly(25.0, 3.0, 0.0, 3.0, 0.0).inOrder()
        assertThat(siteCars()).isEqualTo(2)
    }

    @Test
    @DisplayName("a closed run stands in for one car only")
    fun closedRunClaimedOnce() {
        rec(0)
        for (r in listOf(40, 25, 12, 6)) rec(r)
        rec(0)
        pass(); rec(0)       // claims the closed run
        pass(); rec(0)       // a second car: needs a marker
        rec(0); rec(0)
        assertThat(siteCars()).isEqualTo(2)
    }

    @Test
    @DisplayName("a run claimed by a forced 0 cannot be claimed again by the next count")
    fun forcedZeroRunClaimedOnce() {
        // Seen on a ride: car A counted while its run was open (forced 0), car B
        // counted one second later while car C was already nearest at 40 m.
        // B must get a marker; the forced 0 was A's.
        rec(0)
        for (r in listOf(21, 15, 12, 12, 9)) rec(r)
        pass(); rec(0)            // A: forced 0 closes the run
        pass(); rec(40)           // B: needs a marker
        rec(40); rec(9); rec(6)
        pass(); rec(0)            // C
        rec(0)
        assertThat(ranges.subList(6, 9)).containsExactly(0.0, 3.0, 0.0).inOrder()
        assertThat(total).isEqualTo(3)
        assertThat(siteCars()).isEqualTo(3)
    }

    @Test
    @DisplayName("live records carry closing speed in the rider's units")
    fun liveSpeeds() {
        val r = rec(40, closingMps = 10.0, riderMps = 5.0)
        assertThat(r.passingSpeed).isEqualTo(36)
        assertThat(r.passingSpeedAbs).isEqualTo(54)
        assertThat(rec(0).passingSpeed).isEqualTo(0)
    }
}

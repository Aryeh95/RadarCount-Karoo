package io.github.aryeh95.radarcount

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.FitEffectWithValues
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

// The tracker timestamps packets with System.currentTimeMillis. Instrumenting the engine package
// puts that call on the Robolectric clock, so a test advances it instead of waiting between packets.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.github.aryeh95.radarcount.engine"])
class RadarCountExtensionTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private fun FitEffectWithValues.field(name: String): Double? =
        values.firstOrNull { it.developerField?.fieldName == name }?.value

    private fun radarPacket(threat: Int, vararg ranges: Double) {
        karoo.system.setDataPoint(SensorPoints.radar(threat, *ranges))
        RobolectricPump.advanceBy(1.seconds)
    }

    /** A car closing from far out to alongside, then gone from the radar. */
    private fun carOvertakes() {
        for (range in doubleArrayOf(60.0, 45.0, 30.0, 18.0, 9.0, 3.0)) radarPacket(2, range)
        radarPacket(0)
    }

    @Test
    fun `fit reports the radar as off until one streams`() {
        val fit = karoo.host<RadarCountExtension>().startFit()

        val record = fit.awaitOf<WriteToRecordMesg>(15_000)

        assertEquals(-1.0, record.field("radar_ranges")!!, 0.0)
        assertEquals(255.0, record.field("radar_speeds")!!, 0.0)
        assertNull(record.field("radar_threat_level"))
        assertEquals(0.0, fit.awaitOf<WriteToSessionMesg>(15_000).field("radar_total")!!, 0.0)
    }

    @Test
    fun `an overtaking car is counted into the fit file`() {
        val fit = karoo.host<RadarCountExtension>().startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        carOvertakes()

        fit.awaitOf<WriteToSessionMesg>(15_000) { it.field("radar_total") == 1.0 }
        fit.awaitOf<WriteToRecordMesg>(15_000) { it.field("radar_current") == 1.0 }
    }

    @Test
    fun `threat level and target ranges reach the fit record`() {
        val fit = karoo.host<RadarCountExtension>().startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        karoo.system.setDataPoint(SensorPoints.radar(3, 40.0, 12.0, 25.0))

        val record = fit.awaitOf<WriteToRecordMesg>(15_000) { it.field("radar_vehicle_count") == 3.0 }
        assertEquals(3.0, record.field("radar_threat_level")!!, 0.0)
        assertEquals(12.0, record.field("radar_nearest_distance")!!, 0.0)
        assertEquals(25.0, record.field("radar_range_2")!!, 0.0)
        assertEquals(40.0, record.field("radar_range_3")!!, 0.0)
    }

    @Test
    fun `a threat without target ranges is recorded with no vehicles`() {
        val fit = karoo.host<RadarCountExtension>().startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        karoo.system.setDataPoint(SensorPoints.radar(1))

        val record = fit.awaitOf<WriteToRecordMesg>(15_000) { it.field("radar_threat_level") == 1.0 }
        assertEquals(0.0, record.field("radar_vehicle_count")!!, 0.0)
    }

    @Test
    fun `cars passing while the ride is paused are not counted`() {
        val host = karoo.host<RadarCountExtension>()
        karoo.system.setRideState(RideState.Recording)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        val fit = host.startFit()

        karoo.system.setRideState(RideState.Paused(auto = false))
        carOvertakes()
        karoo.system.setRideState(RideState.Recording)
        carOvertakes()

        fit.awaitOf<WriteToSessionMesg>(15_000) { it.field("radar_total") == 1.0 }
        assertTrue(fit.items.filterIsInstance<WriteToSessionMesg>().none { it.field("radar_total")!! > 1.0 })
    }

    @Test
    fun `a new ride resets the count`() {
        val fit = karoo.host<RadarCountExtension>().startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        carOvertakes()
        fit.awaitOf<WriteToSessionMesg>(15_000) { it.field("radar_total") == 1.0 }

        val seen = fit.items.size
        karoo.system.setRideState(RideState.Recording)

        fit.awaitOf<WriteToSessionMesg>(15_000, after = seen) { it.field("radar_total") == 0.0 }
    }

    @Test
    fun `losing the radar puts the off markers back in the fit file`() {
        val fit = karoo.host<RadarCountExtension>().startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        karoo.system.setDataPoint(SensorPoints.radar(1, 40.0))
        fit.awaitOf<WriteToRecordMesg>(15_000) { it.field("radar_ranges") == 40.0 }

        karoo.system.setStreamState(DataType.Type.RADAR, StreamState.NotAvailable)

        val record = fit.awaitOf<WriteToRecordMesg>(15_000, after = fit.items.size) { it.field("radar_ranges") == -1.0 }
        assertEquals(255.0, record.field("radar_speeds")!!, 0.0)
    }

    @Test
    fun `a radar error is treated as a lost radar`() {
        val fit = karoo.host<RadarCountExtension>().startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        karoo.system.setDataPoint(SensorPoints.radar(1, 40.0))
        fit.awaitOf<WriteToRecordMesg>(15_000) { it.field("radar_ranges") == 40.0 }

        val error = SensorPoints.radar(0)
        karoo.system.setDataPoint(DataPoint(error.dataTypeId, error.values + (DataType.Field.RADAR_ERROR to 3.0)))

        fit.awaitOf<WriteToRecordMesg>(15_000, after = fit.items.size) { it.field("radar_ranges") == -1.0 }
    }

    @Test
    fun `the radar stream is open only while something needs it`() {
        val host = karoo.host<RadarCountExtension>()
        assertFalse(karoo.system.hasStreamConsumer(DataType.Type.RADAR))

        val fit = host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        host.stopFit(fit)

        assertFalse(karoo.system.hasStreamConsumer(DataType.Type.RADAR))
    }
}

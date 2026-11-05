package io.github.aryeh95.radarcount

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.github.aryeh95.radarcount.engine"])
class RadarCountRadarStateTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private val extension: RadarCountExtension get() = requireNotNull(RadarCountExtension.running)

    @Test
    fun `a searching radar reports connecting until data arrives`() {
        val host = karoo.host<RadarCountExtension>()
        host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        karoo.system.setStreamState(DataType.Type.RADAR, StreamState.Searching)
        assertEquals(RadarStatus.Searching, extension.radarFeed.status.value)

        karoo.system.setDataPoint(SensorPoints.radar(1, 20.0))
        assertEquals(
            RadarStatus.Live(level = 1, vehicles = 1, nearestM = 20),
            extension.radarFeed.status.value,
        )
    }

    @Test
    fun `losing a radar that streamed reports connection lost`() {
        val host = karoo.host<RadarCountExtension>()
        host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        karoo.system.setDataPoint(SensorPoints.radar(1, 30.0))

        karoo.system.setStreamState(DataType.Type.RADAR, StreamState.NotAvailable)

        assertEquals(RadarStatus.Lost, extension.radarFeed.status.value)
    }

    @Test
    fun `a radar error drops the tracks and reports a lost radar`() {
        val host = karoo.host<RadarCountExtension>()
        host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        karoo.system.setDataPoint(SensorPoints.radar(1, 30.0))

        karoo.system.setDataPoint(SensorPoints.radar(0, error = 3))

        assertEquals(RadarStatus.Lost, extension.radarFeed.status.value)
        assertFalse((extension.radarFeed.status.value is RadarStatus.Live))
        assertNull(extension.radarFeed.closingSpeedMps.value)

        karoo.system.setDataPoint(SensorPoints.radar(1, 12.0))
        assertEquals(RadarStatus.Live(level = 1, vehicles = 1, nearestM = 12), extension.radarFeed.status.value)
    }

    @Test
    fun `a radar that never streamed reports not connected`() {
        val host = karoo.host<RadarCountExtension>()
        host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        karoo.system.setStreamState(DataType.Type.RADAR, StreamState.NotAvailable)

        assertEquals(RadarStatus.Off, extension.radarFeed.status.value)
    }
}

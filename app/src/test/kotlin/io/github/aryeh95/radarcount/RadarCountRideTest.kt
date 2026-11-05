package io.github.aryeh95.radarcount

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

// The extension package is instrumented too so the ride-time ticker reads the Robolectric clock
// and a test advances it instead of waiting in real time.
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    instrumentedPackages = [
        "io.github.aryeh95.radarcount",
        "io.github.aryeh95.radarcount.engine",
        "io.hammerhead.karooext.internal",
    ],
)
class RadarCountRideTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private val extension: RadarCountExtension get() = requireNotNull(RadarCountExtension.running)

    private val config = ViewConfig(gridSize = 30 to 15, viewSize = 240 to 120, textSize = 40)

    private fun radarPacket(threat: Int, vararg ranges: Double) {
        karoo.system.setDataPoint(SensorPoints.radar(threat, *ranges))
        RobolectricPump.advanceBy(1.seconds)
    }

    private fun carOvertakes() {
        for (range in doubleArrayOf(60.0, 45.0, 30.0, 18.0, 9.0, 3.0)) radarPacket(2, range)
        radarPacket(0)
    }

    @Test
    fun `ride time accrues while recording and is cleared on idle`() {
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitConsumer(RideState.Params)

        karoo.system.setRideState(RideState.Recording)
        RobolectricPump.advanceBy(3.seconds)
        val recorded = extension.rideTimeMs.value
        assertTrue("ride time after 3s: $recorded", recorded >= 2_000)

        karoo.system.setRideState(RideState.Idle)
        assertEquals(0L, extension.rideTimeMs.value)
    }

    @Test
    fun `ride time stops while paused and resumes on recording`() {
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitConsumer(RideState.Params)
        karoo.system.setRideState(RideState.Recording)
        RobolectricPump.advanceBy(3.seconds)
        val recorded = extension.rideTimeMs.value

        karoo.system.setRideState(RideState.Paused(auto = false))
        RobolectricPump.advanceBy(3.seconds)
        assertTrue(
            "paused time must not grow: recorded=$recorded, now=${extension.rideTimeMs.value}",
            extension.rideTimeMs.value in recorded..(recorded + 300),
        )

        karoo.system.setRideState(RideState.Recording)
        RobolectricPump.advanceBy(2.seconds)
        assertTrue(
            "resumed time must grow: ${extension.rideTimeMs.value}",
            extension.rideTimeMs.value >= recorded + 1_500,
        )
    }

    @Test
    fun `pausing keeps the radar stream open but idle releases it`() {
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitConsumer(RideState.Params)
        karoo.system.setRideState(RideState.Recording)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        assertTrue(karoo.system.hasStreamConsumer(DataType.Type.RADAR))

        karoo.system.setRideState(RideState.Paused(auto = false))
        assertTrue(
            "a paused ride keeps streaming, it only stops counting",
            karoo.system.hasStreamConsumer(DataType.Type.RADAR),
        )

        karoo.system.setRideState(RideState.Idle)
        assertFalse(karoo.system.hasStreamConsumer(DataType.Type.RADAR))
    }

    @Test
    fun `the radar stream stays open until the last session releases it`() {
        val host = karoo.host<RadarCountExtension>()

        val view = host.startView("vehicle-count", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        val fit = host.startFit()
        assertTrue(karoo.system.hasStreamConsumer(DataType.Type.RADAR))

        host.stopView(view)
        assertTrue(
            "the FIT writer still holds the stream",
            karoo.system.hasStreamConsumer(DataType.Type.RADAR),
        )

        host.stopFit(fit)
        assertFalse(karoo.system.hasStreamConsumer(DataType.Type.RADAR))
    }

    @Test
    fun `restarting mid-ride reopens the streams and counts again`() {
        karoo.host<RadarCountExtension>()
        karoo.awaitConsumer(RideState.Params)
        karoo.system.setRideState(RideState.Recording)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        carOvertakes()
        karoo.awaitValue { extension.radarFeed.passCount.value == 1 }

        karoo.restart<RadarCountExtension>()

        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        carOvertakes()
        // The exact total depends on whether the sticky Recording replay reads as a new ride;
        // what matters here is that stream and counting work again after the process restarts.
        karoo.awaitValue { extension.radarFeed.passCount.value >= 1 }
    }

    @Test
    fun `destroying the extension drops the singleton`() {
        karoo.host<RadarCountExtension>()
        karoo.awaitConsumer(RideState.Params)
        assertNotNull(RadarCountExtension.running)

        karoo.close()

        assertNull(RadarCountExtension.running)
    }
}

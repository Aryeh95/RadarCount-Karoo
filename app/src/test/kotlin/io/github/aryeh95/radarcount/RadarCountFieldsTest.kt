package io.github.aryeh95.radarcount

import android.widget.RemoteViews
import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.ViewUpdate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

// The SDK view emitter throttles frames on System.currentTimeMillis, and the tracker timestamps
// packets with it; instrumenting both puts them on the Robolectric clock.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    instrumentedPackages = ["io.github.aryeh95.radarcount.engine", "io.hammerhead.karooext.internal"],
)
class RadarCountFieldsTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private val config = ViewConfig(gridSize = 30 to 15, viewSize = 240 to 120, textSize = 40)

    private fun radarPacket(threat: Int, vararg ranges: Double) {
        karoo.system.setDataPoint(SensorPoints.radar(threat, *ranges))
        RobolectricPump.advanceBy(1.seconds)
    }

    private fun awaitDrawn(view: fi.nikosavola.karooext.testing.ViewRecorder, wanted: FieldImages, after: Int = 0) {
        view.await(15_000, after = after) {
            it is ViewUpdate.Frame && sameImages(framesOf(karoo.app, config, it.views), wanted)
        }
    }

    private fun drawn(typeId: String, state: RadarStatus, passCount: Int = 0, closing: Double? = null) =
        drawn(karoo.app, config, typeId, state, passCount, closingSpeedMps = closing)

    @Test
    fun `the approach speed field waits for a closing estimate`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("approach-speed", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        radarPacket(2, 30.0)

        awaitDrawn(view, drawn("approach-speed", RadarStatus.Live(2, 1, 30)))
    }

    @Test
    fun `the approach speed field shows the closing speed once a track has history`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("approach-speed", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        for (range in doubleArrayOf(40.0, 30.0, 20.0)) radarPacket(2, range)

        awaitDrawn(view, drawn("approach-speed", RadarStatus.Live(2, 1, 20), closing = 10.0))
    }

    @Test
    fun `the distance field shows the nearest target`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("closest-distance", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        radarPacket(1, 25.0)

        awaitDrawn(view, drawn("closest-distance", RadarStatus.Live(1, 1, 25)))
    }

    @Test
    fun `the rate field shows nothing before two minutes of recording`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("vehicles-per-hour", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        radarPacket(1, 30.0)

        awaitDrawn(view, drawn("vehicles-per-hour", RadarStatus.Live(1, 1, 30)))
    }

    @Test
    fun `the combo field shows the count idle and speed and distance while a vehicle is on the radar`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("radar-combo", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        val idle = drawn("radar-combo", RadarStatus.Live.CLEAR)
        val active = drawn("radar-combo", RadarStatus.Live(2, 1, 20), closing = 10.0)
        assertFalse(sameImages(idle, active))

        radarPacket(0)
        awaitDrawn(view, idle)

        val before = view.items.size
        for (range in doubleArrayOf(40.0, 30.0, 20.0)) radarPacket(2, range)
        awaitDrawn(view, active, after = before)
    }

    @Test
    fun `a field falls back to the no-radar tile when the radar is lost`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("vehicle-count", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        radarPacket(1, 40.0)
        awaitDrawn(view, drawn("vehicle-count", RadarStatus.Live(1, 1, 40)))

        val before = view.items.size
        karoo.system.setStreamState(DataType.Type.RADAR, StreamState.NotAvailable)

        awaitDrawn(view, drawn("vehicle-count", RadarStatus.Lost), after = before)
    }
}

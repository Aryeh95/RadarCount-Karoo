package io.github.aryeh95.radarcount

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.ViewUpdate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.github.aryeh95.radarcount.engine.RadarStatus
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

// The SDK throttles frames on System.currentTimeMillis, the tracker timestamps packets with it.
// The fields are drawn into bitmaps, so a frame is checked against the field's own render.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    instrumentedPackages = ["io.github.aryeh95.radarcount.engine", "io.hammerhead.karooext.internal"],
)
class RadarFieldsTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private val config = ViewConfig(gridSize = 30 to 15, viewSize = 240 to 120, textSize = 40)

    private fun bitmaps(frame: android.widget.RemoteViews) = framesOf(karoo.app, config, frame)

    private fun expected(state: RadarStatus, passCount: Int) =
        drawn(karoo.app, config, "vehicle-count", state, passCount)

    @Test
    fun `a field with no radar draws the no radar tile and hides the karoo header`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("vehicle-count", config)

        val frame = view.awaitFrame(15_000)

        assertTrue(sameImages(bitmaps(frame), expected(RadarStatus.Off, 0)))
        assertTrue(ShowCustomStreamState(message = "", color = null) in view.events)
        assertTrue(UpdateGraphicConfig(showHeader = false) in view.events)
    }

    @Test
    fun `the count field draws one car once a radar streams and a car has passed`() {
        RobolectricPump.advanceBy(1.seconds)
        val view = karoo.host<RadarCountExtension>().startView("vehicle-count", config)
        val noRadar = bitmaps(view.awaitFrame(15_000))
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        for (range in doubleArrayOf(60.0, 45.0, 30.0, 18.0, 9.0, 3.0)) {
            karoo.system.setDataPoint(SensorPoints.radar(2, range))
            RobolectricPump.advanceBy(1.seconds)
        }
        karoo.system.setDataPoint(SensorPoints.radar(0))
        RobolectricPump.advanceBy(1.seconds)

        val wanted = expected(RadarStatus.Live.CLEAR, 1)
        val frame = view.await(15_000) {
            it is ViewUpdate.Frame && sameImages(bitmaps(it.views), wanted)
        } as ViewUpdate.Frame
        assertTrue(sameImages(bitmaps(frame.views), wanted))
        assertFalse(sameImages(noRadar, wanted))
    }
}

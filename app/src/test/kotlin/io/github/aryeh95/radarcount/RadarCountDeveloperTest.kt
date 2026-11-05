package io.github.aryeh95.radarcount

import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], instrumentedPackages = ["io.github.aryeh95.radarcount.engine"])
class RadarCountDeveloperTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private val extension: RadarCountExtension get() = requireNotNull(RadarCountExtension.running)

    @Before
    fun clearStoredSettings() {
        runBlocking { SettingsRepository.of(karoo.app).resetChoices() }
    }

    private fun radarPacket(threat: Int, vararg ranges: Double) {
        karoo.system.setDataPoint(SensorPoints.radar(threat, *ranges))
        RobolectricPump.advanceBy(1.seconds)
    }

    @Test
    fun `the track trace records a pass during a ride`() {
        runBlocking { SettingsRepository.of(karoo.app).update(Settings(traceTracks = true)) }
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitValue { extension.settings.value.traceTracks }
        karoo.awaitConsumer(RideState.Params)
        karoo.system.setRideState(RideState.Recording)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        for (range in doubleArrayOf(60.0, 45.0, 30.0, 18.0, 9.0, 3.0)) radarPacket(2, range)
        radarPacket(0)
        karoo.awaitValue { extension.radarFeed.passCount.value == 1 }

        // Closing the ride flushes and closes the writer, so the file can be read.
        karoo.system.setRideState(RideState.Idle)

        val tracks = File(karoo.app.getExternalFilesDir(null), "tracks")
        val file = tracks.listFiles()?.firstOrNull { it.name.startsWith("tracks-") }
        assertNotNull("no track trace written in $tracks", file)

        val lines = file!!.readLines()
        assertTrue(lines.toString(), lines.first().startsWith("kind,ms,first,min,last,samples"))
        assertTrue(lines.toString(), lines.any { it.startsWith("TRACK,") && it.endsWith(",pass") })
    }
}

package io.github.aryeh95.radarcount

import io.github.aryeh95.radarcount.engine.RadarStatus
import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.data.UnitsSetting
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.FitEffectWithValues
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.WriteToRecordMesg
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    instrumentedPackages = ["io.github.aryeh95.radarcount.engine", "io.hammerhead.karooext.internal"],
)
class RadarCountTelemetryTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private val extension: RadarCountExtension get() = requireNotNull(RadarCountExtension.running)

    @Before
    fun clearStoredSettings() {
        runBlocking { SettingsRepository.of(karoo.app).resetChoices() }
    }

    private fun FitEffectWithValues.field(name: String): Double? =
        values.firstOrNull { it.developerField?.fieldName == name }?.value

    private fun radarPacket(threat: Int, vararg ranges: Double) {
        karoo.system.setDataPoint(SensorPoints.radar(threat, *ranges))
        RobolectricPump.advanceBy(1.seconds)
    }

    @Test
    fun `fit passing speed follows the rider's unit setting`() {
        runBlocking { SettingsRepository.of(karoo.app).update(Settings(units = UnitsSetting.IMPERIAL)) }
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitValue { extension.imperialUnits.value }
        val fit = host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR, DataType.Type.SPEED)

        for (range in doubleArrayOf(40.0, 30.0, 20.0)) radarPacket(2, range)
        karoo.system.setDataPoint(DataType.Type.SPEED, 7.0)
        RobolectricPump.advanceBy(1.seconds)

        val mark = fit.items.size
        val record = fit.awaitOf<WriteToRecordMesg>(15_000, after = mark) {
            it.field("radar_nearest_distance") == 20.0 && it.field("passing_speed") != null
        }
        assertEquals(22.0, record.field("passing_speed")!!, 0.0)
        assertEquals(38.0, record.field("passing_speedabs")!!, 0.0)
    }

    @Test
    fun `a transient radar loss recovers when data returns`() {
        val host = karoo.host<RadarCountExtension>()
        val fit = host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        karoo.system.setDataPoint(SensorPoints.radar(1, 40.0))
        fit.awaitOf<WriteToRecordMesg>(15_000) { it.field("radar_ranges") == 40.0 }

        karoo.system.setStreamState(DataType.Type.RADAR, StreamState.NotAvailable)
        fit.awaitOf<WriteToRecordMesg>(15_000, after = fit.items.size) { it.field("radar_ranges") == -1.0 }

        karoo.system.setDataPoint(SensorPoints.radar(1, 35.0))
        val record = fit.awaitOf<WriteToRecordMesg>(15_000, after = fit.items.size) {
            it.field("radar_ranges") == 35.0
        }

        assertEquals(1.0, record.field("radar_threat_level")!!, 0.0)
        assertTrue((extension.radarFeed.status.value is RadarStatus.Live))
    }

    @Test
    fun `a four-target packet fills every extra range field`() {
        val host = karoo.host<RadarCountExtension>()
        val fit = host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        karoo.system.setDataPoint(SensorPoints.radar(3, 40.0, 25.0, 15.0, 8.0))

        val record = fit.awaitOf<WriteToRecordMesg>(15_000) { it.field("radar_vehicle_count") == 4.0 }
        assertEquals(8.0, record.field("radar_nearest_distance")!!, 0.0)
        assertEquals(15.0, record.field("radar_range_2")!!, 0.0)
        assertEquals(25.0, record.field("radar_range_3")!!, 0.0)
        assertEquals(40.0, record.field("radar_range_4")!!, 0.0)
    }

    @Test
    fun `the rider's heading reaches the engine`() {
        val host = karoo.host<RadarCountExtension>()
        host.startFit()
        karoo.awaitConsumer(OnLocationChanged.Params)

        karoo.system.setLocation(60.17, 24.94, orientation = 123.0)

        karoo.awaitValue { extension.radarFeed.lastHeadingDeg == 123.0 }
        assertEquals(123.0, extension.radarFeed.lastHeadingDeg, 0.0)
    }
}

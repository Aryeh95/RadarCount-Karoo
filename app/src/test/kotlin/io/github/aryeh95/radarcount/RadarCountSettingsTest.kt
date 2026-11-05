package io.github.aryeh95.radarcount

import io.github.aryeh95.radarcount.engine.RadarStatus
import io.github.aryeh95.radarcount.data.FieldSizes
import fi.nikosavola.karooext.testing.FakeKarooSystem
import fi.nikosavola.karooext.testing.SensorPoints
import fi.nikosavola.karooext.testing.ViewUpdate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.RobolectricPump
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.data.SensitivitySetting
import io.github.aryeh95.radarcount.data.SpeedSetting
import io.github.aryeh95.radarcount.data.UnitsSetting
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    instrumentedPackages = ["io.github.aryeh95.radarcount.engine", "io.hammerhead.karooext.internal"],
)
class RadarCountSettingsTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private val extension: RadarCountExtension get() = requireNotNull(RadarCountExtension.running)

    private val config = ViewConfig(gridSize = 30 to 15, viewSize = 240 to 120, textSize = 40)

    @Before
    fun clearStoredSettings() {
        runBlocking { SettingsRepository.of(karoo.app).resetChoices() }
    }

    private fun store(settings: Settings) {
        runBlocking { SettingsRepository.of(karoo.app).update(settings) }
    }

    private fun radarPacket(threat: Int, vararg ranges: Double) {
        karoo.system.setDataPoint(SensorPoints.radar(threat, *ranges))
        RobolectricPump.advanceBy(1.seconds)
    }

    private fun carTo(minRange: Double) {
        for (range in doubleArrayOf(60.0, 45.0, 32.0, 24.0, 18.0, 14.0)) radarPacket(2, range)
        radarPacket(2, minRange)
    }

    private fun carOvertakes() {
        for (range in doubleArrayOf(60.0, 45.0, 30.0, 18.0, 9.0, 3.0)) radarPacket(2, range)
        radarPacket(0)
    }

    @Test
    fun `relaxed sensitivity counts a car that drops off short of the normal threshold`() {
        store(Settings(sensitivity = SensitivitySetting.RELAXED))
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitValue { extension.settings.value.sensitivity == SensitivitySetting.RELAXED }
        RobolectricPump.advanceBy(1.seconds)
        host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        carTo(10.0)
        radarPacket(0)
        radarPacket(0)

        assertEquals(1, extension.radarFeed.passCount.value)
    }

    @Test
    fun `normal sensitivity ignores a car that never came inside nine metres`() {
        val host = karoo.host<RadarCountExtension>()
        host.startFit()
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        carTo(10.0)
        repeat(3) { radarPacket(0) }

        assertEquals(0, extension.radarFeed.passCount.value)
    }

    @Test
    fun `turning the ride-start reset off keeps the count across rides`() {
        store(Settings(resetOnRideStart = false))
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitValue { !extension.settings.value.resetOnRideStart }
        karoo.awaitConsumer(RideState.Params)
        karoo.system.setRideState(RideState.Recording)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)
        carOvertakes()
        karoo.awaitValue { extension.radarFeed.passCount.value == 1 }

        karoo.system.setRideState(RideState.Idle)
        karoo.system.setRideState(RideState.Recording)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        assertEquals(1, extension.radarFeed.passCount.value)
    }

    @Test
    fun `the metric setting overrides an imperial profile`() {
        store(Settings(units = UnitsSetting.METRIC))
        karoo.system.setUserProfile(FakeKarooSystem.imperialProfile())
        karoo.host<RadarCountExtension>()
        karoo.awaitConsumer(UserProfile.Params)

        assertFalse(extension.imperialUnits.value)
    }

    @Test
    fun `the imperial setting overrides a metric profile`() {
        store(Settings(units = UnitsSetting.IMPERIAL))
        karoo.host<RadarCountExtension>()

        karoo.awaitValue { extension.imperialUnits.value }
        assertTrue(extension.imperialUnits.value)
    }

    @Test
    fun `an imperial profile switches the distance field to feet`() {
        karoo.system.setUserProfile(FakeKarooSystem.imperialProfile())
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitValue { extension.imperialUnits.value }
        RobolectricPump.advanceBy(1.seconds)
        val view = host.startView("closest-distance", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR)

        radarPacket(1, 45.0)

        val wanted = drawn(karoo.app, config, "closest-distance", RadarStatus.Live(1, 1, 45), useImperial = true)
        view.await(15_000) {
            it is ViewUpdate.Frame && sameImages(framesOf(karoo.app, config, it.views), wanted)
        }
    }

    @Test
    fun `the auto unit follows a profile change mid ride`() {
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitConsumer(UserProfile.Params)
        assertFalse(extension.imperialUnits.value)

        karoo.system.setUserProfile(FakeKarooSystem.imperialProfile())

        karoo.awaitValue { extension.imperialUnits.value }
        assertTrue(extension.imperialUnits.value)
    }

    @Test
    fun `the radar field size is remembered from the Karoo`() {
        val host = karoo.host<RadarCountExtension>()

        host.startView("radar-combo", config)

        karoo.awaitValue { FieldSizes.saved(extension.settings.value, "radar-combo") != null }
        val saved = requireNotNull(FieldSizes.saved(extension.settings.value, "radar-combo"))
        assertEquals(30 to 15, saved.gridSize)
        assertEquals(240 to 120, saved.viewSize)
        assertEquals(40, saved.textSize)
    }

    @Test
    fun `the absolute speed field adds the rider's speed`() {
        store(Settings(speedMode = SpeedSetting.ABSOLUTE))
        val host = karoo.host<RadarCountExtension>()
        karoo.awaitValue { extension.settings.value.speedMode == SpeedSetting.ABSOLUTE }
        RobolectricPump.advanceBy(1.seconds)
        val view = host.startView("approach-speed", config)
        karoo.awaitStreamConsumer(DataType.Type.RADAR, DataType.Type.SPEED)

        for (range in doubleArrayOf(40.0, 30.0, 20.0)) radarPacket(2, range)
        karoo.system.setDataPoint(DataType.Type.SPEED, 7.0)
        RobolectricPump.advanceBy(1.seconds)

        val wanted = drawn(
            karoo.app, config, "approach-speed", RadarStatus.Live(2, 1, 20),
            closingSpeedMps = 10.0, riderSpeedMps = 7.0, settings = Settings(speedMode = SpeedSetting.ABSOLUTE),
        )
        view.await(15_000) {
            it is ViewUpdate.Frame && sameImages(framesOf(karoo.app, config, it.views), wanted)
        }
    }
}

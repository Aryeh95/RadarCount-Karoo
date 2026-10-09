package io.github.aryeh95.radarcount

import android.util.Log
import io.github.aryeh95.radarcount.data.FieldSizes
import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.data.UnitsSetting
import io.github.aryeh95.radarcount.datatypes.ApproachSpeedDataType
import io.github.aryeh95.radarcount.datatypes.ClosestDistanceDataType
import io.github.aryeh95.radarcount.datatypes.ComboDataType
import io.github.aryeh95.radarcount.datatypes.VehicleCountDataType
import io.github.aryeh95.radarcount.datatypes.VehiclesPerHourDataType
import io.github.aryeh95.radarcount.engine.FitRecordWriter
import io.github.aryeh95.radarcount.engine.RadarFeed
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * RadarCount for Karoo.
 *
 * Counts vehicles that pass the rider, estimates their approach speed, and
 * records everything to the ride FIT file using the same developer fields
 * as the Garmin "My Bike Radar Traffic" data field so rides can be
 * uploaded to mybiketraffic.com.
 */
class RadarCountExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {

    companion object {
        const val EXTENSION_ID = "radarcount"
        private const val TAG = "RadarCountExt"

        // FIT base type ids (Garmin FIT SDK)
        private const val FIT_BASE_TYPE_ENUM: Short = 0
        private const val FIT_BASE_TYPE_UINT8: Short = 2
        private const val FIT_BASE_TYPE_SINT16: Short = 131
        private const val FIT_BASE_TYPE_UINT16: Short = 132

        private const val FIT_WRITE_INTERVAL_MS = 1000L

        @Volatile
        private var current: RadarCountExtension? = null

        /**
         * The service while it is up, for the app's own screens, which run
         * in the same process and read the radar through it. Null before
         * the Karoo has started the service and after it has stopped it.
         */
        val running: RadarCountExtension? get() = current

        /** m/s to the rider's speed unit, rounded, never negative. */
        internal fun toUserSpeedUnits(metersPerSecond: Double, imperial: Boolean): Int =
            FitRecordWriter.toUserSpeedUnits(metersPerSecond, imperial)
    }

    private lateinit var karoo: KarooSystemService

    lateinit var radarFeed: RadarFeed
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    val settings: StateFlow<Settings>
        get() = settingsRepository.settings

    private val mainScope = MainScope()

    /** Whether the Karoo connection is up. The sensors only start while it is. */
    @Volatile private var connected = false

    /** Set in onDestroy. A FIT job the Karoo never cancelled stops writing from then on. */
    @Volatile private var destroyed = false

    /** The distance unit in the rider's Karoo profile, true for imperial. */
    private val profileImperial = MutableStateFlow(false)

    /** Units the fields use: the units setting, or the Karoo profile's when it is AUTO. */
    lateinit var imperialUnits: StateFlow<Boolean>
        private set

    /** The last size the Karoo gave each field in a ride, by type id, so the settings previews can draw them at true size. */
    val fieldViewConfigs = MutableStateFlow<Map<String, ViewConfig>>(emptyMap())

    /**
     * Records the size the Karoo gave field [typeId] and remembers it
     * across restarts. The page editor's preview is left out: its size is
     * not the one the field has in a ride.
     */
    fun reportViewConfig(typeId: String, config: ViewConfig) {
        if (config.preview) return
        fieldViewConfigs.update { it + (typeId to config) }
        val encoded = FieldSizes.encode(config)
        if (settingsRepository.settings.value.fieldSizes[typeId] != encoded) {
            CoroutineScope(Dispatchers.IO).launch { settingsRepository.saveFieldSize(typeId, encoded) }
        }
    }

    // Rider ground speed in m/s from the Karoo SPEED stream
    private val _riderSpeedMps = MutableStateFlow(0.0)
    val riderSpeedMps: StateFlow<Double> = _riderSpeedMps.asStateFlow()

    private var rideRecording = false

    // Time spent recording this ride (paused time excluded), ticked once a
    // second while recording, for the vehicles-per-hour field.
    private val _rideTimeMs = MutableStateFlow(0L)
    val rideTimeMs: StateFlow<Long> = _rideTimeMs.asStateFlow()
    private var rideTimeBaseMs = 0L
    private var recordingSinceMs = 0L
    private var rideTimeJob: Job? = null

    // Demand-driven sensor streams: radar and speed are only open while a
    // ride is recording, a data field is on screen, the FIT writer is
    // active, or the status screen is open. At boot nothing runs except
    // the cheap ride-state and profile consumers.
    private val sensorLock = Any()
    private var sensorDemand = 0
    private var sensorsRunning = false

    /** Karoo consumers that live as long as the connection: ride state and the rider profile. */
    private val connectionConsumers = mutableListOf<String>()

    /** Karoo consumers that live as long as the sensors run: speed and heading. Touched only under [sensorLock]. */
    private val sensorConsumers = mutableListOf<String>()

    private fun MutableList<String>.releaseConsumers() {
        forEach { karoo.removeConsumer(it) }
        clear()
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RadarCount ${BuildConfig.VERSION_NAME} starting")

        karoo = KarooSystemService(this)
        radarFeed = RadarFeed(karoo)
        settingsRepository = SettingsRepository.of(this)

        imperialUnits = combine(profileImperial, settings) { profile, s ->
            when (s.units) {
                UnitsSetting.AUTO -> profile
                UnitsSetting.METRIC -> false
                UnitsSetting.IMPERIAL -> true
            }
        }.stateIn(mainScope, SharingStarted.Eagerly, false)

        mainScope.launch {
            settings.collect { radarFeed.setSensitivity(it.sensitivity.closeThresholdM) }
        }

        current = this
        karoo.connect { up ->
            connected = up
            if (up) onKarooConnected() else onKarooDisconnected()
        }
    }

    private fun onKarooConnected() {
        Log.i(TAG, "Connected to the Karoo")
        connectionConsumers += karoo.addConsumer(RideState.Params) { state: RideState ->
            when (state) {
                is RideState.Recording -> onRecording()
                is RideState.Paused -> onPaused(state.auto)
                is RideState.Idle -> onIdle()
            }
        }
        connectionConsumers += karoo.addConsumer(UserProfile.Params) { profile: UserProfile ->
            val distanceUnit = profile.preferredUnit.distance
            Log.i(TAG, "Profile distance unit: $distanceUnit")
            profileImperial.value = distanceUnit == UserProfile.PreferredUnit.UnitType.IMPERIAL
        }
        synchronized(sensorLock) {
            if (sensorDemand > 0) startSensorsLocked()
        }
    }

    private fun onKarooDisconnected() {
        Log.w(TAG, "Lost the Karoo connection")
        releaseKaroo()
    }

    /** Lets go of everything held on the Karoo: sensor streams, the connection's consumers, and the ride timer. */
    private fun releaseKaroo() {
        synchronized(sensorLock) { stopSensorsLocked() }
        connectionConsumers.releaseConsumers()
        pauseRideTime()
    }

    /**
     * Register interest in live radar data. Streams open on the first
     * acquire and close on the last release. Every acquire must be paired
     * with exactly one [releaseRadar].
     */
    fun acquireRadar() {
        synchronized(sensorLock) {
            sensorDemand++
            if (sensorDemand == 1 && connected) startSensorsLocked()
        }
    }

    fun releaseRadar() {
        synchronized(sensorLock) {
            if (sensorDemand == 0) return
            sensorDemand--
            if (sensorDemand == 0) stopSensorsLocked()
        }
    }

    private fun startSensorsLocked() {
        if (sensorsRunning) return
        sensorsRunning = true
        Log.i(TAG, "Sensors on")
        radarFeed.start()
        // The rider's speed, for a passing car's absolute speed.
        sensorConsumers += karoo.addConsumer(OnStreamState.StartStreaming(DataType.Type.SPEED)) { event: OnStreamState ->
            (event.state as? StreamState.Streaming)?.dataPoint?.singleValue?.let { speedMs ->
                _riderSpeedMps.value = speedMs
                radarFeed.riderSpeedMps = speedMs
            }
        }
        // The rider's heading, so the pass counter can tell a car that went
        // straight on at a turn from one that passed.
        sensorConsumers += karoo.addConsumer(OnLocationChanged.Params) { event: OnLocationChanged ->
            event.orientation?.let { radarFeed.updateHeading(it) }
        }
    }

    private fun stopSensorsLocked() {
        if (!sensorsRunning) return
        sensorsRunning = false
        Log.i(TAG, "Sensors off")
        radarFeed.stop()
        sensorConsumers.releaseConsumers()
        _riderSpeedMps.value = 0.0
        radarFeed.riderSpeedMps = 0.0
    }

    /**
     * Reset the pass counters when a new ride starts recording.
     * Pause/resume re-emits Recording, so only reset on Idle -> Recording.
     */
    private fun onRecording() {
        radarFeed.setCountingEnabled(true)
        if (!rideRecording) {
            Log.i(TAG, "Ride started")
            rideRecording = true
            startTrackTrace()
            if (settings.value.resetOnRideStart) radarFeed.resetPassCounts()
            acquireRadar()
        }
        resumeRideTime()
    }

    /**
     * Nothing is written to the FIT file while paused, so a pass counted now
     * would never reach the site. Keep tracking, stop counting.
     */
    private fun onPaused(auto: Boolean) {
        Log.d(TAG, if (auto) "Ride auto-paused" else "Ride paused")
        radarFeed.setCountingEnabled(false)
        pauseRideTime()
    }

    private fun onIdle() {
        Log.i(TAG, "No ride in progress")
        radarFeed.setCountingEnabled(true)
        pauseRideTime()
        rideTimeBaseMs = 0L
        _rideTimeMs.value = 0L
        if (rideRecording) {
            rideRecording = false
            releaseRadar()
            stopTrackTrace()
        }
    }

    // Diagnostic, off unless enabled with five taps on the version line in
    // Settings: one CSV line per track decision, in app-private storage so
    // it needs no permission. Pull with
    //   adb pull /sdcard/Android/data/io.github.aryeh95.radarcount/files/tracks/
    private var traceWriter: BufferedWriter? = null
    private val traceLock = Any()

    private fun startTrackTrace() {
        if (!settingsRepository.settings.value.traceTracks) return
        try {
            val dir = File(getExternalFilesDir(null), "tracks").also { it.mkdirs() }
            // Keep the last few rides; a file is a few KB.
            dir.listFiles { f -> f.name.startsWith("tracks-") }?.sortedBy { it.name }?.dropLast(9)?.forEach { it.delete() }
            val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val w = File(dir, "tracks-$name.csv").bufferedWriter()
            w.write("kind,ms,first,min,last,samples,durMs,threat,speedMps,decision\n")
            synchronized(traceLock) { traceWriter = w }
            // The sink runs inside the tracker's update; it must never throw into it.
            radarFeed.setTrace { line ->
                try {
                    synchronized(traceLock) { traceWriter?.let { it.write(line); it.newLine() } }
                } catch (_: Exception) {
                    synchronized(traceLock) { traceWriter = null }
                }
            }
            mainScope.launch {
                while (isActive && traceWriter != null) {
                    delay(10_000L)
                    synchronized(traceLock) { runCatching { traceWriter?.flush() } }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "track trace unavailable: ${e.message}")
        }
    }

    private fun stopTrackTrace() {
        radarFeed.setTrace(null)
        synchronized(traceLock) {
            runCatching { traceWriter?.close() }
            traceWriter = null
        }
    }

    private fun resumeRideTime() {
        if (rideTimeJob != null) return
        recordingSinceMs = System.currentTimeMillis()
        rideTimeJob = mainScope.launch {
            while (isActive) {
                _rideTimeMs.value = rideTimeBaseMs + (System.currentTimeMillis() - recordingSinceMs)
                delay(1000L)
            }
        }
    }

    private fun pauseRideTime() {
        rideTimeJob?.let {
            it.cancel()
            rideTimeJob = null
            rideTimeBaseMs += System.currentTimeMillis() - recordingSinceMs
            _rideTimeMs.value = rideTimeBaseMs
        }
    }

    /**
     * Called by the Karoo while a ride file is open. Writes a record every
     * second, plus the session total whenever it changes, and holds the
     * radar open until the file closes.
     *
     * Field names, numbers and base types match the Garmin "My Bike Radar
     * Traffic" Connect IQ field so the file can be uploaded to
     * mybiketraffic.com. Karoo SDK limits: single values only (so
     * `radar_ranges` / `radar_speeds` carry the nearest target rather than
     * an 8-element array), and no lap-message API (so `radar_lap`, field 4,
     * is not written). Like the Garmin field, radar-off is encoded as
     * range -1 / speed 255 so "no radar" differs from "radar saw nothing".
     *
     * Extra fields (7-12) record threat level, simultaneous vehicle count,
     * nearest distance and the next three target ranges for analysis and
     * for tuning the pass counter.
     */
    override fun startFit(emitter: Emitter<FitEffect>) {
        Log.i(TAG, "Ride file open, writing radar fields")
        acquireRadar()

        val mbtRangesField = DeveloperField(0, FIT_BASE_TYPE_SINT16, "radar_ranges", "")
        val mbtSpeedsField = DeveloperField(1, FIT_BASE_TYPE_UINT8, "radar_speeds", "")
        val mbtCurrentField = DeveloperField(2, FIT_BASE_TYPE_UINT16, "radar_current", "")
        val mbtTotalField = DeveloperField(3, FIT_BASE_TYPE_UINT16, "radar_total", "")
        // 4 = radar_lap (lap message) is not writable with the Karoo SDK
        val mbtPassingSpeedField = DeveloperField(5, FIT_BASE_TYPE_UINT8, "passing_speed", "")
        val mbtPassingSpeedAbsField = DeveloperField(6, FIT_BASE_TYPE_UINT8, "passing_speedabs", "")

        val levelField = DeveloperField(7, FIT_BASE_TYPE_ENUM, "radar_threat_level", "")
        val carsField = DeveloperField(8, FIT_BASE_TYPE_UINT8, "radar_vehicle_count", "")
        val nearestField = DeveloperField(9, FIT_BASE_TYPE_UINT16, "radar_nearest_distance", "m")
        // Ranges of the 2nd-4th targets, for tuning the pass counter offline
        val extraRangeFields = listOf(
            DeveloperField(10, FIT_BASE_TYPE_UINT16, "radar_range_2", "m"),
            DeveloperField(11, FIT_BASE_TYPE_UINT16, "radar_range_3", "m"),
            DeveloperField(12, FIT_BASE_TYPE_UINT16, "radar_range_4", "m")
        )

        // Diagnostics (beta): enough to explain a missed count from the file.
        val dbgClearsField = DeveloperField(13, FIT_BASE_TYPE_UINT16, "radar_dbg_clears", "")
        val dbgHeadingField = DeveloperField(14, FIT_BASE_TYPE_UINT16, "radar_dbg_heading", "deg")
        val dbgPacketsField = DeveloperField(15, FIT_BASE_TYPE_UINT8, "radar_dbg_packets", "/s")
        val dbgTurnsField = DeveloperField(16, FIT_BASE_TYPE_UINT16, "radar_dbg_turns", "")
        val dbgRejTurnField = DeveloperField(17, FIT_BASE_TYPE_UINT16, "radar_dbg_rej_turn", "")
        val dbgRejCrossField = DeveloperField(18, FIT_BASE_TYPE_UINT16, "radar_dbg_rej_cross", "")
        val dbgRejNoPassField = DeveloperField(19, FIT_BASE_TYPE_UINT16, "radar_dbg_rej_nopass", "")

        val writer = FitRecordWriter()
        // The total last written to the session message; -1 so the first record writes it.
        var sessionTotal = -1

        fun writeSecond() {
            val feed = radarFeed
            val packet = feed.packet
            val ranges = packet?.rangesM?.sorted().orEmpty()
            val passTotal = feed.passCount.value

            val record = writer.next(FitRecordWriter.Sample(
                connected = packet != null,
                vehicleCount = ranges.size,
                nearestM = ranges.firstOrNull() ?: 0,
                passTotal = passTotal,
                // The FIT field has no "unknown", and the Garmin app writes 0
                // for a non-closing target, so an unknown speed writes 0 too.
                closingMps = feed.closingSpeedMps.value ?: 0.0,
                riderMps = _riderSpeedMps.value,
                imperial = imperialUnits.value
            ))

            val values = ArrayList<FieldValue>(12)
            values.add(FieldValue(mbtRangesField, record.rangeM))
            values.add(FieldValue(mbtSpeedsField, record.speedMps))
            values.add(FieldValue(mbtPassingSpeedField, record.passingSpeed.toDouble()))
            values.add(FieldValue(mbtPassingSpeedAbsField, record.passingSpeedAbs.toDouble()))

            if (packet != null) {
                values.add(FieldValue(levelField, packet.level.toDouble()))
                values.add(FieldValue(carsField, ranges.size.toDouble()))
                if (ranges.isNotEmpty()) {
                    values.add(FieldValue(nearestField, ranges[0].toDouble()))
                    for ((i, field) in extraRangeFields.withIndex()) {
                        ranges.getOrNull(i + 1)?.let { values.add(FieldValue(field, it.toDouble())) }
                    }
                }
            }
            values.add(FieldValue(mbtCurrentField, passTotal.toDouble()))

            values.add(FieldValue(dbgClearsField, feed.trackerClears.toDouble()))
            val hdg = feed.lastHeadingDeg
            if (hdg >= 0.0) values.add(FieldValue(dbgHeadingField, hdg.roundToInt().coerceIn(0, 359).toDouble()))
            values.add(FieldValue(dbgPacketsField, feed.takePacketCount().coerceAtMost(255).toDouble()))
            values.add(FieldValue(dbgTurnsField, feed.turnCount.toDouble()))
            values.add(FieldValue(dbgRejTurnField, feed.rejectedTurnedAway.toDouble()))
            values.add(FieldValue(dbgRejCrossField, feed.rejectedCrossingAfterTurn.toDouble()))
            values.add(FieldValue(dbgRejNoPassField, feed.rejectedNotPass.toDouble()))

            emitter.onNext(WriteToRecordMesg(values = values))

            if (passTotal != sessionTotal) {
                sessionTotal = passTotal
                emitter.onNext(WriteToSessionMesg(FieldValue(mbtTotalField, passTotal.toDouble())))
            }
        }

        val ticker = everySecond(::writeSecond)
        emitter.setCancellable {
            Log.i(TAG, "Ride file closed, radar released")
            ticker.cancel()
            releaseRadar()
        }
    }

    /**
     * Calls [write] once a second, the first time a second after the ride
     * file opens, on a background thread so a slow write never holds up the
     * fields. It outlives [mainScope], so it goes quiet once the service is
     * destroyed rather than relying on the Karoo to cancel it. A second
     * that throws is logged and skipped, so one bad record does not end the
     * ride's radar data.
     */
    private fun everySecond(write: () -> Unit): Job = CoroutineScope(Dispatchers.Default).launch {
        while (true) {
            delay(FIT_WRITE_INTERVAL_MS)
            if (destroyed) continue
            runCatching(write).onFailure { e ->
                if (e !is Exception || e is CancellationException) throw e
                Log.w(TAG, "No FIT record this second", e)
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "RadarCount stopping")
        current = null
        // With the connection marked down nothing can start the sensors
        // again, so the demand can be cleared before they are stopped.
        connected = false
        synchronized(sensorLock) { sensorDemand = 0 }
        releaseKaroo()
        radarFeed.stop()
        destroyed = true
        mainScope.cancel()
        try {
            karoo.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Karoo connection did not close cleanly", e)
        }
        super.onDestroy()
    }

    override val types by lazy {
        listOf(
            ComboDataType(this),
            VehicleCountDataType(this),
            ApproachSpeedDataType(this),
            ClosestDistanceDataType(this),
            VehiclesPerHourDataType(this)
        )
    }
}

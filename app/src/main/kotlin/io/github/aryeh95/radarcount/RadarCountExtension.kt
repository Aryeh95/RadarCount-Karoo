package io.github.aryeh95.radarcount

import io.github.aryeh95.radarcount.data.Settings
import io.github.aryeh95.radarcount.data.SettingsRepository
import io.github.aryeh95.radarcount.data.UnitsSetting
import io.github.aryeh95.radarcount.datatypes.glance.ApproachSpeedGlanceDataType
import io.github.aryeh95.radarcount.datatypes.glance.ComboGlanceDataType
import io.github.aryeh95.radarcount.datatypes.glance.ClosestDistanceGlanceDataType
import io.github.aryeh95.radarcount.datatypes.glance.VehicleCountGlanceDataType
import io.github.aryeh95.radarcount.datatypes.glance.VehiclesPerHourGlanceDataType
import io.github.aryeh95.radarcount.engine.FitRecordWriter
import io.github.aryeh95.radarcount.engine.RadarEngine
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
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        var instance: RadarCountExtension? = null
            private set

        /** m/s to the rider's speed unit, rounded, never negative. */
        internal fun toUserSpeedUnits(metersPerSecond: Double, imperial: Boolean): Int =
            FitRecordWriter.toUserSpeedUnits(metersPerSecond, imperial)
    }

    lateinit var karooSystem: KarooSystemService
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var _radarEngine: RadarEngine? = null
    val radarEngine: RadarEngine
        get() = _radarEngine ?: throw IllegalStateException("RadarEngine not initialized")

    private var _settingsRepository: SettingsRepository? = null
    val settingsRepository: SettingsRepository
        get() = _settingsRepository ?: throw IllegalStateException("SettingsRepository not initialized")

    val settings: StateFlow<Settings>
        get() = settingsRepository.settings

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Rider's preferred distance unit from the Karoo profile (metric/imperial)
    private val _profileImperial = MutableStateFlow(false)

    /** Effective unit: the settings override, or the Karoo profile when AUTO. */
    lateinit var useImperial: StateFlow<Boolean>
        private set

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

    // Consumer IDs for cleanup
    private var rideStateConsumerId: String? = null
    private var userProfileConsumerId: String? = null
    private var speedConsumerId: String? = null
    private var locationConsumerId: String? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        android.util.Log.i(TAG, "Initializing RadarCount v${BuildConfig.VERSION_NAME}")

        karooSystem = KarooSystemService(this)
        _radarEngine = RadarEngine(karooSystem)
        _settingsRepository = SettingsRepository.getInstance(this)

        useImperial = combine(_profileImperial, settingsRepository.settings) { profile, s ->
            when (s.units) {
                UnitsSetting.AUTO -> profile
                UnitsSetting.METRIC -> false
                UnitsSetting.IMPERIAL -> true
            }
        }.stateIn(serviceScope, SharingStarted.Eagerly, false)

        serviceScope.launch {
            settingsRepository.settings.collect { s ->
                _radarEngine?.setSensitivity(s.sensitivity.closeThresholdM, s.sensitivity.closingThresholdM)
            }
        }

        karooSystem.connect { connected ->
            _isConnected.value = connected
            if (connected) {
                android.util.Log.i(TAG, "KarooSystemService connected")
                startRideStateTracking()
                startUserProfileTracking()
                synchronized(sensorLock) {
                    if (sensorDemand > 0) startSensorsLocked()
                }
            } else {
                android.util.Log.w(TAG, "KarooSystemService disconnected")
                synchronized(sensorLock) { stopSensorsLocked() }
                stopRideStateTracking()
                stopUserProfileTracking()
            }
        }
    }

    /**
     * Register interest in live radar data. Streams open on the first
     * acquire and close on the last release. Every acquire must be paired
     * with exactly one [releaseRadar].
     */
    fun acquireRadar() {
        synchronized(sensorLock) {
            sensorDemand++
            if (sensorDemand == 1 && _isConnected.value) startSensorsLocked()
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
        android.util.Log.i(TAG, "Starting radar/speed streams")
        _radarEngine?.startStreaming()
        startSpeedTracking()
        startHeadingTracking()
    }

    private fun stopSensorsLocked() {
        if (!sensorsRunning) return
        sensorsRunning = false
        android.util.Log.i(TAG, "Stopping radar/speed streams")
        _radarEngine?.stopStreaming()
        stopSpeedTracking()
        stopHeadingTracking()
        _riderSpeedMps.value = 0.0
    }

    /**
     * Reset the pass counters when a new ride starts recording.
     * Pause/resume re-emits Recording, so only reset on Idle -> Recording.
     */
    private fun startRideStateTracking() {
        rideStateConsumerId = karooSystem.addConsumer(RideState.Params) { event: RideState ->
            when (event) {
                is RideState.Recording -> {
                    _radarEngine?.setCountingEnabled(true)
                    if (!rideRecording) {
                        android.util.Log.i(TAG, "Ride recording started")
                        rideRecording = true
                        startTrackTrace()
                        if (settings.value.resetOnRideStart) _radarEngine?.resetPassCounts()
                        acquireRadar()
                    }
                    resumeRideTime()
                }
                is RideState.Idle -> {
                    android.util.Log.i(TAG, "Ride idle")
                    _radarEngine?.setCountingEnabled(true)
                    pauseRideTime()
                    rideTimeBaseMs = 0L
                    _rideTimeMs.value = 0L
                    if (rideRecording) {
                        rideRecording = false
                        releaseRadar()
                        stopTrackTrace()
                    }
                }
                is RideState.Paused -> {
                    android.util.Log.d(TAG, "Ride paused (auto=${event.auto})")
                    // Nothing is written to the FIT file while paused, so a pass counted now
                    // would never reach the site. Keep tracking, stop counting.
                    _radarEngine?.setCountingEnabled(false)
                    pauseRideTime()
                }
            }
        }
    }

    // Diagnostic (beta): one CSV line per track decision, in app-private
    // storage so it needs no permission. Pull with
    //   adb pull /sdcard/Android/data/io.github.aryeh95.radarcount/files/tracks/
    private var traceWriter: java.io.BufferedWriter? = null
    private val traceLock = Any()

    private fun startTrackTrace() {
        try {
            val dir = java.io.File(getExternalFilesDir(null), "tracks").also { it.mkdirs() }
            // Keep the last few rides; a file is a few KB.
            dir.listFiles { f -> f.name.startsWith("tracks-") }?.sortedBy { it.name }?.dropLast(9)?.forEach { it.delete() }
            val name = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
            val w = java.io.File(dir, "tracks-$name.csv").bufferedWriter()
            w.write("kind,ms,first,min,last,samples,durMs,threat,speedMps,decision\n")
            synchronized(traceLock) { traceWriter = w }
            // The sink runs inside the tracker's update; it must never throw into it.
            _radarEngine?.setTrace { line ->
                try {
                    synchronized(traceLock) { traceWriter?.let { it.write(line); it.newLine() } }
                } catch (_: Exception) {
                    synchronized(traceLock) { traceWriter = null }
                }
            }
            serviceScope.launch {
                while (isActive && traceWriter != null) {
                    delay(10_000L)
                    synchronized(traceLock) { runCatching { traceWriter?.flush() } }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "track trace unavailable: ${e.message}")
        }
    }

    private fun stopTrackTrace() {
        _radarEngine?.setTrace(null)
        synchronized(traceLock) {
            runCatching { traceWriter?.close() }
            traceWriter = null
        }
    }

    private fun stopRideStateTracking() {
        rideStateConsumerId?.let { karooSystem.removeConsumer(it) }
        rideStateConsumerId = null
        pauseRideTime()
    }

    private fun resumeRideTime() {
        if (rideTimeJob != null) return
        recordingSinceMs = System.currentTimeMillis()
        rideTimeJob = serviceScope.launch {
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
     * Track user profile for unit preference (metric/imperial).
     */
    private fun startUserProfileTracking() {
        userProfileConsumerId = karooSystem.addConsumer(UserProfile.Params) { event: UserProfile ->
            val isImperial = event.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
            _profileImperial.value = isImperial
            android.util.Log.i(TAG, "User unit preference: ${if (isImperial) "Imperial" else "Metric"}")
        }
    }

    private fun stopUserProfileTracking() {
        userProfileConsumerId?.let { karooSystem.removeConsumer(it) }
        userProfileConsumerId = null
    }

    /**
     * Track rider speed for absolute passing speed.
     */
    private fun startSpeedTracking() {
        speedConsumerId = karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.SPEED)
        ) { event: OnStreamState ->
            (event.state as? StreamState.Streaming)?.dataPoint?.singleValue?.let { speedMs ->
                _riderSpeedMps.value = speedMs
            }
        }
    }

    private fun stopSpeedTracking() {
        speedConsumerId?.let { karooSystem.removeConsumer(it) }
        speedConsumerId = null
    }

    /**
     * Track the rider's heading so the pass counter can tell a car that
     * went straight on at a turn from one that passed.
     */
    private fun startHeadingTracking() {
        locationConsumerId = karooSystem.addConsumer(OnLocationChanged.Params) { event: OnLocationChanged ->
            event.orientation?.let { _radarEngine?.updateHeading(it) }
        }
    }

    private fun stopHeadingTracking() {
        locationConsumerId?.let { karooSystem.removeConsumer(it) }
        locationConsumerId = null
    }

    /**
     * Write radar data to the ride FIT file at 1 Hz.
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
        android.util.Log.i(TAG, "Starting FIT file recording for radar data")
        acquireRadar()

        val mbtRangesField = DeveloperField(0, FIT_BASE_TYPE_SINT16, "radar_ranges", "")
        val mbtSpeedsField = DeveloperField(1, FIT_BASE_TYPE_UINT8, "radar_speeds", "")
        val mbtCurrentField = DeveloperField(2, FIT_BASE_TYPE_UINT16, "radar_current", "")
        val mbtTotalField = DeveloperField(3, FIT_BASE_TYPE_UINT16, "radar_total", "")
        // 4 = radar_lap (lap message) is not writable with the Karoo SDK
        val mbtPassingSpeedField = DeveloperField(5, FIT_BASE_TYPE_UINT8, "passing_speed", "")
        val mbtPassingSpeedAbsField = DeveloperField(6, FIT_BASE_TYPE_UINT8, "passing_speedabs", "")

        val threatField = DeveloperField(7, FIT_BASE_TYPE_ENUM, "radar_threat_level", "")
        val vehicleCountField = DeveloperField(8, FIT_BASE_TYPE_UINT8, "radar_vehicle_count", "")
        val nearestDistanceField = DeveloperField(9, FIT_BASE_TYPE_UINT16, "radar_nearest_distance", "m")
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

        val fitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        fitScope.launch {
            var lastSessionTotal = -1
            val writer = FitRecordWriter()
            while (isActive) {
                delay(FIT_WRITE_INTERVAL_MS)
                try {
                    val engine = _radarEngine ?: continue
                    val connected = engine.isRadarConnected.value
                    val vehicleCount = engine.vehicleCount.value
                    val nearestM = engine.nearestDistanceM.value
                    val passTotal = engine.passCount.value

                    val record = writer.next(FitRecordWriter.Sample(
                        connected = connected,
                        vehicleCount = vehicleCount,
                        nearestM = nearestM,
                        passTotal = passTotal,
                        // The FIT field has no "unknown", and the Garmin app writes 0
                        // for a non-closing target, so an unknown speed writes 0 too.
                        closingMps = engine.closingSpeedMps.value ?: 0.0,
                        riderMps = _riderSpeedMps.value,
                        imperial = useImperial.value
                    ))

                    val values = ArrayList<FieldValue>(12)
                    values.add(FieldValue(mbtRangesField, record.rangeM))
                    values.add(FieldValue(mbtSpeedsField, record.speedMps))
                    values.add(FieldValue(mbtPassingSpeedField, record.passingSpeed.toDouble()))
                    values.add(FieldValue(mbtPassingSpeedAbsField, record.passingSpeedAbs.toDouble()))

                    if (connected) {
                        values.add(FieldValue(threatField, engine.threatLevel.value.ordinal.toDouble()))
                        values.add(FieldValue(vehicleCountField, vehicleCount.toDouble()))
                        if (vehicleCount > 0) {
                            values.add(FieldValue(nearestDistanceField, nearestM.toDouble()))
                            val sorted = engine.targetDistances.value.sorted()
                            for ((i, field) in extraRangeFields.withIndex()) {
                                sorted.getOrNull(i + 1)?.let { values.add(FieldValue(field, it.toDouble())) }
                            }
                        }
                    }
                    values.add(FieldValue(mbtCurrentField, passTotal.toDouble()))

                    values.add(FieldValue(dbgClearsField, engine.trackerClears.toDouble()))
                    val hdg = engine.lastHeadingDeg
                    if (hdg >= 0.0) values.add(FieldValue(dbgHeadingField, hdg.roundToInt().coerceIn(0, 359).toDouble()))
                    values.add(FieldValue(dbgPacketsField, engine.takePacketCount().coerceAtMost(255).toDouble()))
                    values.add(FieldValue(dbgTurnsField, engine.turnCount.toDouble()))
                    values.add(FieldValue(dbgRejTurnField, engine.rejectedTurnedAway.toDouble()))
                    values.add(FieldValue(dbgRejCrossField, engine.rejectedCrossingAfterTurn.toDouble()))
                    values.add(FieldValue(dbgRejNoPassField, engine.rejectedNotPass.toDouble()))

                    emitter.onNext(WriteToRecordMesg(values = values))

                    if (passTotal != lastSessionTotal) {
                        lastSessionTotal = passTotal
                        emitter.onNext(WriteToSessionMesg(FieldValue(mbtTotalField, passTotal.toDouble())))
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.w(TAG, "FIT write error: ${e.message}")
                }
            }
        }

        emitter.setCancellable {
            android.util.Log.i(TAG, "Stopping FIT file recording")
            fitScope.cancel()
            releaseRadar()
        }
    }

    override fun onDestroy() {
        android.util.Log.i(TAG, "onDestroy called")

        instance = null
        _isConnected.value = false

        synchronized(sensorLock) {
            sensorDemand = 0
            stopSensorsLocked()
        }
        stopRideStateTracking()
        stopUserProfileTracking()

        _radarEngine?.destroy()
        _radarEngine = null

        serviceScope.cancel()

        try {
            karooSystem.disconnect()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error disconnecting: ${e.message}")
        }

        super.onDestroy()
    }

    override val types by lazy {
        listOf(
            ComboGlanceDataType(this),
            VehicleCountGlanceDataType(this),
            ApproachSpeedGlanceDataType(this),
            ClosestDistanceGlanceDataType(this),
            VehiclesPerHourGlanceDataType(this)
        )
    }
}

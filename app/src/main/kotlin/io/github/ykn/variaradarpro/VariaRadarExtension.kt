package io.github.ykn.variaradarpro

import io.github.ykn.variaradarpro.data.PreferencesRepository
import io.github.ykn.variaradarpro.data.database.VariaRadarDatabase
import io.github.ykn.variaradarpro.datatypes.glance.LargeWidgetGlanceDataType
import io.github.ykn.variaradarpro.datatypes.glance.MediumWidgetGlanceDataType
import io.github.ykn.variaradarpro.datatypes.glance.PassCountGlanceDataType
import io.github.ykn.variaradarpro.datatypes.glance.SmallWidgetGlanceDataType
import io.github.ykn.variaradarpro.engine.AlertManager
import io.github.ykn.variaradarpro.engine.NightModeManager
import io.github.ykn.variaradarpro.engine.RadarEngine
import io.github.ykn.variaradarpro.engine.SoundEngine
import io.github.ykn.variaradarpro.engine.StatisticsCollector
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.Lap
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.SystemNotification
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Main extension entry point for eiRadar.
 *
 * Provides enhanced alerts and visualization for rear radar
 * on Hammerhead Karoo devices.
 */
class VariaRadarExtension : KarooExtension("eiradar", BuildConfig.VERSION_NAME) {

    companion object {
        private const val TAG = "VariaRadarExtension"

        // FIT base type ids (Garmin FIT SDK)
        private const val FIT_BASE_TYPE_ENUM: Short = 0
        private const val FIT_BASE_TYPE_UINT8: Short = 2
        private const val FIT_BASE_TYPE_SINT16: Short = 131
        private const val FIT_BASE_TYPE_UINT16: Short = 132

        // Sentinels used by the Garmin MyBikeTraffic field when the radar is off
        private const val MBT_RANGE_RADAR_OFF = -1.0
        private const val MBT_SPEED_RADAR_OFF = 255.0
        private const val MBT_SPEED_MAX = 254.0

        private const val FIT_WRITE_INTERVAL_MS = 1000L

        @Volatile
        var instance: VariaRadarExtension? = null
            private set
    }

    lateinit var karooSystem: KarooSystemService
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Lazy-initialized components (null-safe pattern)
    private var _radarEngine: RadarEngine? = null
    val radarEngine: RadarEngine
        get() = _radarEngine ?: throw IllegalStateException("RadarEngine not initialized")

    private var _preferencesRepository: PreferencesRepository? = null
    val preferencesRepository: PreferencesRepository
        get() = _preferencesRepository ?: throw IllegalStateException("PreferencesRepository not initialized")

    private var _alertManager: AlertManager? = null
    val alertManager: AlertManager
        get() = _alertManager ?: throw IllegalStateException("AlertManager not initialized")

    private var _statisticsCollector: StatisticsCollector? = null
    val statisticsCollector: StatisticsCollector
        get() = _statisticsCollector ?: throw IllegalStateException("StatisticsCollector not initialized")

    private var _nightModeManager: NightModeManager? = null
    val nightModeManager: NightModeManager
        get() = _nightModeManager ?: throw IllegalStateException("NightModeManager not initialized")

    private var _soundEngine: SoundEngine? = null
    val soundEngine: SoundEngine
        get() = _soundEngine ?: throw IllegalStateException("SoundEngine not initialized")

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Rider's preferred distance unit (metric/imperial)
    private val _useImperial = MutableStateFlow(false)
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    // Rider ground speed in m/s from the Karoo SPEED stream
    private val _riderSpeedMps = MutableStateFlow(0.0)
    val riderSpeedMps: StateFlow<Double> = _riderSpeedMps.asStateFlow()

    // Alert mute state (toggled via BonusAction, resets on ride end)
    private val _alertsMuted = MutableStateFlow(false)
    val alertsMuted: StateFlow<Boolean> = _alertsMuted.asStateFlow()

    // Consumer IDs for cleanup
    private var rideStateConsumerId: String? = null
    private var lapConsumerId: String? = null
    private var userProfileConsumerId: String? = null
    private var speedConsumerId: String? = null
    private var radarNotifierJob: Job? = null
    private var passCountJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        android.util.Log.i(TAG, "Initializing eiRadar v${BuildConfig.VERSION_NAME}")

        // Initialize KarooSystemService first
        karooSystem = KarooSystemService(this)

        // Initialize repositories
        _preferencesRepository = PreferencesRepository.getInstance(this)

        // Night mode uses Karoo SUNRISE/SUNSET streams (requires karooSystem)
        _nightModeManager = NightModeManager(karooSystem)

        // Initialize engine
        _radarEngine = RadarEngine(karooSystem)

        // Initialize database and statistics collector
        val database = VariaRadarDatabase.getInstance(this)
        _statisticsCollector = StatisticsCollector(database.rideStatisticsDao())

        // Initialize sound engine
        _soundEngine = SoundEngine(karooSystem)

        // Initialize alert manager
        _alertManager = AlertManager(
            extension = this,
            radarEngine = radarEngine,
            preferencesRepository = preferencesRepository,
            nightModeManager = nightModeManager,
            soundEngine = soundEngine,
            statisticsCollector = statisticsCollector
        )

        // Connect to Karoo
        karooSystem.connect { connected ->
            _isConnected.value = connected
            if (connected) {
                android.util.Log.i(TAG, "KarooSystemService connected")
                _radarEngine?.startStreaming()
                _nightModeManager?.startMonitoring()
                _alertManager?.startMonitoring()
                startRideStateTracking()
                startLapTracking()
                startUserProfileTracking()
                startSpeedTracking()
                startRadarConnectionNotifier()
                startPassCountTracking()
            } else {
                android.util.Log.w(TAG, "KarooSystemService disconnected")
                _alertManager?.stopMonitoring()
                _radarEngine?.stopStreaming()
                _nightModeManager?.destroy()
                _statisticsCollector?.endSession()
                stopRideStateTracking()
                stopLapTracking()
                stopUserProfileTracking()
                stopSpeedTracking()
                radarNotifierJob?.cancel()
                passCountJob?.cancel()
            }
        }
    }

    /**
     * Track ride state to properly scope statistics sessions and the
     * per-ride vehicle pass counter.
     * Consumer receives current state on subscription.
     */
    private fun startRideStateTracking() {
        rideStateConsumerId = karooSystem.addConsumer(
            RideState.Params
        ) { event: RideState ->
            when (event) {
                is RideState.Recording -> {
                    val collector = _statisticsCollector
                    if (collector != null && !collector.isSessionActive()) {
                        android.util.Log.i(TAG, "Ride recording started")
                        _radarEngine?.resetPassCounts()
                        collector.startSession()
                    }
                }
                is RideState.Idle -> {
                    android.util.Log.i(TAG, "Ride idle")
                    _alertsMuted.value = false
                    _statisticsCollector?.endSession()
                }
                is RideState.Paused -> {
                    // Keep session alive during pause (rider will resume)
                    android.util.Log.d(TAG, "Ride paused (auto=${event.auto})")
                }
            }
        }
    }

    private fun stopRideStateTracking() {
        rideStateConsumerId?.let { karooSystem.removeConsumer(it) }
        rideStateConsumerId = null
    }

    /**
     * Reset the lap pass counter whenever the Karoo records a new lap.
     */
    private fun startLapTracking() {
        lapConsumerId = karooSystem.addConsumer(Lap.Params) { lap: Lap ->
            android.util.Log.d(TAG, "Lap ${lap.number} (${lap.trigger})")
            _radarEngine?.resetLapPassCount()
        }
    }

    private fun stopLapTracking() {
        lapConsumerId?.let { karooSystem.removeConsumer(it) }
        lapConsumerId = null
    }

    /**
     * Mirror the engine's pass count into the statistics session.
     */
    private fun startPassCountTracking() {
        passCountJob?.cancel()
        passCountJob = serviceScope.launch {
            _radarEngine?.passCount?.collect { total ->
                _statisticsCollector?.recordVehiclesPassed(total)
            }
        }
    }

    /**
     * Track user profile for unit preference (metric/imperial).
     */
    private fun startUserProfileTracking() {
        userProfileConsumerId = karooSystem.addConsumer(
            UserProfile.Params
        ) { event: UserProfile ->
            val isImperial = event.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
            _useImperial.value = isImperial
            android.util.Log.i(TAG, "User unit preference: ${if (isImperial) "Imperial" else "Metric"}")
        }
    }

    private fun stopUserProfileTracking() {
        userProfileConsumerId?.let { karooSystem.removeConsumer(it) }
        userProfileConsumerId = null
    }

    /**
     * Track rider speed for the alert speed gate and absolute passing speed.
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
     * Observe radar connection state and dispatch SystemNotifications.
     */
    private fun startRadarConnectionNotifier() {
        radarNotifierJob?.cancel()
        radarNotifierJob = serviceScope.launch {
            var previousConnected: Boolean? = null
            _radarEngine?.isRadarConnected
                ?.collect { connected ->
                    // Skip initial state to avoid spam on startup
                    if (previousConnected != null) {
                        if (connected) {
                            karooSystem.dispatch(SystemNotification(
                                id = "eiradar_radar_status",
                                message = "Radar connected",
                                style = SystemNotification.Style.EVENT
                            ))
                        } else {
                            karooSystem.dispatch(SystemNotification(
                                id = "eiradar_radar_status",
                                message = "Radar connection lost",
                                style = SystemNotification.Style.ERROR
                            ))
                        }
                    }
                    previousConnected = connected
                }
        }
    }

    /**
     * Write radar data to the ride FIT file at 1Hz.
     *
     * Two sets of developer fields are written:
     *
     * 1. MyBikeTraffic-compatible fields, using the same names, field
     *    numbers and base types as the Garmin "My Bike Radar Traffic" data
     *    field so the ride can be uploaded to mybiketraffic.com. The Karoo
     *    SDK cannot write arrays, so `radar_ranges` / `radar_speeds` carry
     *    the nearest target only, and there is no lap message API so
     *    `radar_lap` (field 4) is not written. Like the Garmin field, the
     *    radar-off state is encoded as range -1 / speed 255 so "no radar"
     *    is distinguishable from "radar saw nothing".
     * 2. eiRadar's own fields (threat level, simultaneous vehicle count,
     *    nearest distance), only written while the radar is connected.
     */
    override fun startFit(emitter: Emitter<FitEffect>) {
        android.util.Log.i(TAG, "Starting FIT file recording for radar data")

        // --- MyBikeTraffic-compatible (record message) ---
        val mbtRangesField = DeveloperField(
            fieldDefinitionNumber = 0,
            fitBaseTypeId = FIT_BASE_TYPE_SINT16,
            fieldName = "radar_ranges",
            units = ""
        )
        val mbtSpeedsField = DeveloperField(
            fieldDefinitionNumber = 1,
            fitBaseTypeId = FIT_BASE_TYPE_UINT8,
            fieldName = "radar_speeds",
            units = ""
        )
        val mbtCurrentField = DeveloperField(
            fieldDefinitionNumber = 2,
            fitBaseTypeId = FIT_BASE_TYPE_UINT16,
            fieldName = "radar_current",
            units = ""
        )
        // Field 3 is radar_total (session), field 4 is radar_lap (lap, not writable on Karoo)
        val mbtTotalField = DeveloperField(
            fieldDefinitionNumber = 3,
            fitBaseTypeId = FIT_BASE_TYPE_UINT16,
            fieldName = "radar_total",
            units = ""
        )
        val mbtPassingSpeedField = DeveloperField(
            fieldDefinitionNumber = 5,
            fitBaseTypeId = FIT_BASE_TYPE_UINT8,
            fieldName = "passing_speed",
            units = ""
        )
        val mbtPassingSpeedAbsField = DeveloperField(
            fieldDefinitionNumber = 6,
            fitBaseTypeId = FIT_BASE_TYPE_UINT8,
            fieldName = "passing_speedabs",
            units = ""
        )

        // --- eiRadar fields (record message) ---
        val threatField = DeveloperField(
            fieldDefinitionNumber = 7,
            fitBaseTypeId = FIT_BASE_TYPE_ENUM,
            fieldName = "radar_threat_level",
            units = ""
        )
        val vehicleCountField = DeveloperField(
            fieldDefinitionNumber = 8,
            fitBaseTypeId = FIT_BASE_TYPE_UINT8,
            fieldName = "radar_vehicle_count",
            units = ""
        )
        val nearestDistanceField = DeveloperField(
            fieldDefinitionNumber = 9,
            fitBaseTypeId = FIT_BASE_TYPE_UINT16,
            fieldName = "radar_nearest_distance",
            units = "m"
        )

        val fitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        fitScope.launch {
            var lastSessionTotal = -1
            while (isActive) {
                delay(FIT_WRITE_INTERVAL_MS)
                try {
                    val engine = _radarEngine ?: continue
                    val connected = engine.isRadarConnected.value
                    val vehicleCount = engine.vehicleCount.value
                    val nearestM = engine.nearestDistanceM.value
                    val passTotal = engine.passCount.value
                    val closingMps = engine.closingSpeedMps.value
                    val imperial = _useImperial.value

                    val values = ArrayList<FieldValue>(9)

                    if (connected) {
                        val tracked = vehicleCount > 0
                        // Relative speed of the nearest target in the rider's units,
                        // like the Garmin field (km/h metric, mph imperial).
                        val passingSpeed = if (tracked) toUserSpeedUnits(closingMps, imperial) else 0
                        val passingSpeedAbs = if (passingSpeed > 0) {
                            passingSpeed + toUserSpeedUnits(_riderSpeedMps.value, imperial)
                        } else {
                            0
                        }

                        values.add(FieldValue(mbtRangesField, if (tracked) nearestM.toDouble() else 0.0))
                        values.add(FieldValue(mbtSpeedsField, if (tracked) closingMps.coerceIn(0.0, MBT_SPEED_MAX) else 0.0))
                        values.add(FieldValue(mbtPassingSpeedField, passingSpeed.toDouble().coerceAtMost(MBT_SPEED_MAX)))
                        values.add(FieldValue(mbtPassingSpeedAbsField, passingSpeedAbs.toDouble().coerceAtMost(MBT_SPEED_MAX)))

                        values.add(FieldValue(threatField, engine.threatLevel.value.ordinal.toDouble()))
                        values.add(FieldValue(vehicleCountField, vehicleCount.toDouble()))
                        if (tracked) {
                            values.add(FieldValue(nearestDistanceField, nearestM.toDouble()))
                        }
                    } else {
                        values.add(FieldValue(mbtRangesField, MBT_RANGE_RADAR_OFF))
                        values.add(FieldValue(mbtSpeedsField, MBT_SPEED_RADAR_OFF))
                        values.add(FieldValue(mbtPassingSpeedField, 0.0))
                        values.add(FieldValue(mbtPassingSpeedAbsField, 0.0))
                    }
                    values.add(FieldValue(mbtCurrentField, passTotal.toDouble()))

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
        }
    }

    private fun toUserSpeedUnits(metersPerSecond: Double, imperial: Boolean): Int {
        val v = if (imperial) metersPerSecond * 2.23694 else metersPerSecond * 3.6
        return v.roundToInt().coerceAtLeast(0)
    }

    override fun onDestroy() {
        android.util.Log.i(TAG, "onDestroy called")

        instance = null
        _isConnected.value = false

        stopRideStateTracking()
        stopLapTracking()
        stopUserProfileTracking()
        stopSpeedTracking()
        radarNotifierJob?.cancel()
        radarNotifierJob = null
        passCountJob?.cancel()
        passCountJob = null

        _alertManager?.destroy()
        _alertManager = null

        _soundEngine = null

        _nightModeManager?.destroy()
        _nightModeManager = null

        _radarEngine?.destroy()
        _radarEngine = null

        _statisticsCollector?.endSession()
        _statisticsCollector?.destroy()
        _statisticsCollector = null

        _preferencesRepository = null

        serviceScope.cancel()

        try {
            karooSystem.disconnect()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error disconnecting: ${e.message}")
        }

        super.onDestroy()
    }

    override fun onBonusAction(actionId: String) {
        when (actionId) {
            "toggle-alerts" -> {
                val muted = !_alertsMuted.value
                _alertsMuted.value = muted

                val message = if (muted) getString(R.string.alerts_muted) else getString(R.string.alerts_enabled)
                val color = if (muted) R.color.alert_background_approaching else R.color.threat_clear

                karooSystem.dispatch(
                    InRideAlert(
                        id = "eiradar_mute_toggle",
                        icon = R.drawable.ic_radar,
                        title = message,
                        detail = "",
                        autoDismissMs = 2000,
                        backgroundColor = color,
                        textColor = R.color.alert_text
                    )
                )
                android.util.Log.i(TAG, "Alerts ${if (muted) "muted" else "enabled"} via BonusAction")
            }
        }
    }

    override val types by lazy {
        listOf(
            SmallWidgetGlanceDataType(this),
            MediumWidgetGlanceDataType(this),
            LargeWidgetGlanceDataType(this),
            PassCountGlanceDataType(this)
        )
    }
}

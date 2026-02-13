package io.github.ykn.variaradarpro

import io.github.ykn.variaradarpro.data.PreferencesRepository
import io.github.ykn.variaradarpro.data.database.VariaRadarDatabase
import io.github.ykn.variaradarpro.datatypes.glance.LargeWidgetGlanceDataType
import io.github.ykn.variaradarpro.datatypes.glance.MediumWidgetGlanceDataType
import io.github.ykn.variaradarpro.datatypes.glance.SmallWidgetGlanceDataType
import io.github.ykn.variaradarpro.engine.AlertManager
import io.github.ykn.variaradarpro.engine.HapticEngine
import io.github.ykn.variaradarpro.engine.NightModeManager
import io.github.ykn.variaradarpro.engine.RadarEngine
import io.github.ykn.variaradarpro.engine.SoundEngine
import io.github.ykn.variaradarpro.engine.StatisticsCollector
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SystemNotification
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.WriteToRecordMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Main extension entry point for eiRadar.
 *
 * Provides enhanced alerts and visualization for rear radar
 * on Hammerhead Karoo devices.
 */
class VariaRadarExtension : KarooExtension("eiradar", BuildConfig.VERSION_NAME) {

    companion object {
        private const val TAG = "VariaRadarExtension"

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

    private var _hapticEngine: HapticEngine? = null
    val hapticEngine: HapticEngine
        get() = _hapticEngine ?: throw IllegalStateException("HapticEngine not initialized")

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Rider's preferred distance unit (metric/imperial)
    private val _useImperial = MutableStateFlow(false)
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    // Consumer IDs for cleanup
    private var rideStateConsumerId: String? = null
    private var userProfileConsumerId: String? = null
    private var radarNotifierJob: Job? = null

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

        // Initialize sound and haptic engines
        _soundEngine = SoundEngine(karooSystem)
        _hapticEngine = HapticEngine(this)

        // Initialize alert manager
        _alertManager = AlertManager(
            extension = this,
            radarEngine = radarEngine,
            preferencesRepository = preferencesRepository,
            nightModeManager = nightModeManager,
            soundEngine = soundEngine,
            hapticEngine = hapticEngine,
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
                startUserProfileTracking()
                startRadarConnectionNotifier()
            } else {
                android.util.Log.w(TAG, "KarooSystemService disconnected")
                _alertManager?.stopMonitoring()
                _radarEngine?.stopStreaming()
                _nightModeManager?.destroy()
                _statisticsCollector?.endSession()
                stopRideStateTracking()
                stopUserProfileTracking()
                radarNotifierJob?.cancel()
            }
        }
    }

    /**
     * Track ride state to properly scope statistics sessions.
     * Consumer receives current state on subscription.
     */
    private fun startRideStateTracking() {
        rideStateConsumerId = karooSystem.addConsumer(
            RideState.Params
        ) { event: RideState ->
            when (event) {
                is RideState.Recording -> {
                    android.util.Log.i(TAG, "Ride recording started")
                    _statisticsCollector?.startSession()
                }
                is RideState.Idle -> {
                    android.util.Log.i(TAG, "Ride idle")
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
     * Observe radar connection state and dispatch SystemNotifications.
     */
    private fun startRadarConnectionNotifier() {
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
     * Records threat level, vehicle count, and nearest distance as developer fields.
     */
    override fun startFit(emitter: Emitter<FitEffect>) {
        android.util.Log.i(TAG, "Starting FIT file recording for radar data")

        val threatField = DeveloperField(
            fieldDefinitionNumber = 0,
            fitBaseTypeId = 0, // enum (uint8)
            fieldName = "radar_threat_level",
            units = ""
        )
        val vehicleCountField = DeveloperField(
            fieldDefinitionNumber = 1,
            fitBaseTypeId = 0, // uint8
            fieldName = "radar_vehicle_count",
            units = ""
        )
        val nearestDistanceField = DeveloperField(
            fieldDefinitionNumber = 2,
            fitBaseTypeId = 132.toShort(), // uint16
            fieldName = "radar_nearest_distance",
            units = "m"
        )

        val fitScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        fitScope.launch {
            while (isActive) {
                delay(1000L)
                try {
                    val engine = _radarEngine ?: continue
                    val threatLevel = engine.threatLevel.value.ordinal.toDouble()
                    val vehicleCount = engine.vehicleCount.value.toDouble()
                    val nearestDistance = engine.nearestDistanceM.value.toDouble()

                    emitter.onNext(WriteToRecordMesg(
                        values = listOf(
                            FieldValue(threatField, threatLevel),
                            FieldValue(vehicleCountField, vehicleCount),
                            FieldValue(nearestDistanceField, nearestDistance)
                        )
                    ))
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

    override fun onDestroy() {
        android.util.Log.i(TAG, "onDestroy called")

        instance = null
        _isConnected.value = false

        stopRideStateTracking()
        stopUserProfileTracking()
        radarNotifierJob?.cancel()
        radarNotifierJob = null

        _alertManager?.destroy()
        _alertManager = null

        _hapticEngine?.cancel()
        _hapticEngine = null

        _soundEngine = null

        _nightModeManager?.destroy()
        _nightModeManager = null

        _radarEngine?.destroy()
        _radarEngine = null

        _statisticsCollector?.endSession()
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

    override val types by lazy {
        listOf(
            SmallWidgetGlanceDataType(this),
            MediumWidgetGlanceDataType(this),
            LargeWidgetGlanceDataType(this)
        )
    }
}

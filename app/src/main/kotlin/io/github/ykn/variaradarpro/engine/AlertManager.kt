package io.github.ykn.variaradarpro.engine

import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.PreferencesRepository
import io.github.ykn.variaradarpro.data.models.AlertSettings
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ScreenWakePolicy
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages alert dispatch with throttling.
 *
 * Alert evaluation is driven by every radar packet (see [RadarEngine.packets])
 * so the closing-speed and traffic-density trackers get one sample per packet,
 * not one per state change. Settings and rider speed are cached from their
 * own flows and read at evaluation time.
 *
 * All evaluation happens on a single coroutine, so the trackers and the
 * throttler are never touched concurrently.
 */
class AlertManager(
    private val extension: VariaRadarExtension,
    private val radarEngine: RadarEngine,
    private val preferencesRepository: PreferencesRepository,
    private val nightModeManager: NightModeManager,
    private val soundEngine: SoundEngine,
    private val statisticsCollector: StatisticsCollector
) {

    companion object {
        private const val TAG = "AlertManager"
        internal const val NIGHT_MODE_DISTANCE_MULTIPLIER = 1.33f

        internal fun applyNightModeOverrides(settings: PresetSettings): PresetSettings {
            return settings.copy(
                approachingDistanceM = (settings.approachingDistanceM * NIGHT_MODE_DISTANCE_MULTIPLIER).toInt(),
                warningDistanceM = (settings.warningDistanceM * NIGHT_MODE_DISTANCE_MULTIPLIER).toInt(),
                criticalDistanceM = (settings.criticalDistanceM * NIGHT_MODE_DISTANCE_MULTIPLIER).toInt()
            )
        }

        internal fun getAutoDismissMs(level: ThreatLevel): Long {
            return when (level) {
                ThreatLevel.CRITICAL -> 5000L
                ThreatLevel.WARNING -> 4000L
                ThreatLevel.APPROACHING -> 2000L
                ThreatLevel.CLEAR -> 1500L
            }
        }
    }

    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val throttler = AlertThrottler()
    private val closingSpeedTracker = ClosingSpeedTracker()
    private val trafficDensityTracker = TrafficDensityTracker()

    // Cached settings, written by their collectors and read on the packet coroutine
    @Volatile private var currentSettings: PresetSettings = PresetSettings()
    @Volatile private var currentAlertSettings: AlertSettings = AlertSettings()

    // Monitoring state
    private val isMonitoring = AtomicBoolean(false)
    private var monitoringJob: Job? = null

    // Last alert state
    private val _lastAlertLevel = MutableStateFlow<ThreatLevel?>(null)
    val lastAlertLevel: StateFlow<ThreatLevel?> = _lastAlertLevel.asStateFlow()

    /**
     * Start monitoring radar state and dispatching alerts.
     */
    fun startMonitoring() {
        if (!isMonitoring.compareAndSet(false, true)) {
            android.util.Log.w(TAG, "Already monitoring")
            return
        }

        android.util.Log.i(TAG, "Starting alert monitoring")
        throttler.reset()

        monitoringJob = alertScope.launch {
            launch {
                preferencesRepository.settingsFlow.collect { settings ->
                    currentSettings = settings
                    soundEngine.setSoundSet(settings.soundSet)
                }
            }
            launch {
                preferencesRepository.alertSettingsFlow.collect { currentAlertSettings = it }
            }
            radarEngine.packets.collect { state ->
                processWidgetState(state)
            }
        }
    }

    /**
     * Stop monitoring and clean up.
     */
    fun stopMonitoring() {
        if (!isMonitoring.compareAndSet(true, false)) {
            return
        }

        android.util.Log.i(TAG, "Stopping alert monitoring")
        monitoringJob?.cancel()
        monitoringJob = null
        throttler.reset()
        closingSpeedTracker.reset()
        trafficDensityTracker.reset()
    }

    private fun processWidgetState(state: WidgetState) {
        // Feed trackers on every packet
        when (state) {
            is WidgetState.Threat -> {
                closingSpeedTracker.addSample(state.nearestDistanceM)
                trafficDensityTracker.addSample(state.vehicleCount)
            }
            is WidgetState.Clear -> {
                closingSpeedTracker.reset()
                trafficDensityTracker.addSample(0)
            }
            else -> {
                closingSpeedTracker.reset()
                trafficDensityTracker.reset()
            }
        }

        // Handle state
        when (state) {
            is WidgetState.Threat -> handleThreat(state)
            is WidgetState.Clear -> handleClear()
            else -> { }
        }
    }

    private fun handleThreat(threat: WidgetState.Threat) {
        // Record vehicle detection for statistics (always, regardless of mute/speed gate)
        statisticsCollector.recordVehicleDetection(threat.vehicleCount, threat.nearestDistanceM)
        statisticsCollector.startThreatTracking()

        val settings = currentSettings
        val alertSettings = currentAlertSettings

        if (!alertSettings.globalEnabled || extension.alertsMuted.value) {
            return
        }

        // Apply night mode overrides to thresholds
        val effectiveSettings = if (nightModeManager.isNightMode.value) {
            applyNightModeOverrides(settings)
        } else {
            settings
        }

        // Calculate threat level based on (possibly adjusted) thresholds
        val calculatedLevel = RadarEngine.calculateThreatLevel(
            distanceM = threat.nearestDistanceM,
            approachingThreshold = effectiveSettings.approachingDistanceM,
            warningThreshold = effectiveSettings.warningDistanceM,
            criticalThreshold = effectiveSettings.criticalDistanceM
        )

        // Use the more severe level
        var effectiveLevel = maxOf(threat.level, calculatedLevel)

        if (effectiveLevel == ThreatLevel.CLEAR) {
            return
        }

        // Closing speed escalation: fast-closing vehicle gets bumped up one level
        if (closingSpeedTracker.isFastApproach()) {
            effectiveLevel = when (effectiveLevel) {
                ThreatLevel.APPROACHING -> ThreatLevel.WARNING
                ThreatLevel.WARNING -> ThreatLevel.CRITICAL
                else -> effectiveLevel
            }
        }

        // Holding/receding suppression: vehicle not actually approaching
        if (closingSpeedTracker.isHoldingOrReceding()
            && effectiveLevel == ThreatLevel.APPROACHING) {
            return
        }

        // Speed gate: suppress APPROACHING alerts when stopped or moving slowly.
        // WARNING and CRITICAL always fire — a stopped cyclist is the most
        // vulnerable target (e.g. traffic light, intersection).
        val speedGate = settings.speedGateKmh
        val riderSpeedKmh = extension.riderSpeedMps.value * 3.6
        if (speedGate > 0 && riderSpeedKmh < speedGate
            && effectiveLevel == ThreatLevel.APPROACHING) {
            return
        }

        // Traffic density suppression: suppress APPROACHING in sustained heavy traffic
        if (trafficDensityTracker.isDenseTraffic()
            && effectiveLevel == ThreatLevel.APPROACHING) {
            return
        }

        // Check throttling
        if (!throttler.shouldAlert(effectiveLevel, settings.alertCooldownMs)) {
            return
        }

        // Dispatch alerts
        dispatchAlerts(effectiveLevel, threat, settings, alertSettings)

        // Record for statistics
        _lastAlertLevel.value = effectiveLevel
        statisticsCollector.recordAlert(effectiveLevel)
    }

    private fun handleClear() {
        statisticsCollector.endThreatTracking()

        // Play clear chime if enabled (skip when muted)
        if (currentSettings.clearChimeEnabled && _lastAlertLevel.value != null && !extension.alertsMuted.value) {
            playClearChime()
        }

        _lastAlertLevel.value = null
        throttler.reset()
    }

    private fun dispatchAlerts(
        level: ThreatLevel,
        threat: WidgetState.Threat,
        settings: PresetSettings,
        alertSettings: AlertSettings
    ) {
        android.util.Log.d(TAG, "Dispatching alerts for level: $level, distance: ${threat.nearestDistanceM}m")

        // Visual alert
        if (alertSettings.visualAlert) {
            dispatchVisualAlert(level, threat)
        }

        // Sound alert
        if (alertSettings.soundAlert && settings.soundEnabled) {
            soundEngine.playAlert(level)
        }

        // Screen wake
        handleScreenWake(level, settings.screenWakePolicy)
    }

    private fun dispatchVisualAlert(level: ThreatLevel, threat: WidgetState.Threat) {
        val alert = InRideAlert(
            id = "eiradar_${System.currentTimeMillis()}",
            icon = R.drawable.ic_radar_warning,
            title = formatAlertTitle(threat.vehicleCount),
            detail = formatAlertDetail(level, threat),
            autoDismissMs = getAutoDismissMs(level),
            backgroundColor = getAlertColorRes(level),
            textColor = R.color.alert_text
        )

        extension.karooSystem.dispatch(alert)
    }

    private fun handleScreenWake(level: ThreatLevel, policy: ScreenWakePolicy) {
        val shouldWake = when (policy) {
            ScreenWakePolicy.NEVER -> false
            ScreenWakePolicy.CRITICAL_ONLY -> level == ThreatLevel.CRITICAL
            ScreenWakePolicy.ALWAYS -> true
        }

        if (shouldWake) {
            extension.karooSystem.dispatch(TurnScreenOn)
        }
    }

    private fun playClearChime() {
        if (currentAlertSettings.soundAlert && currentSettings.soundEnabled) {
            soundEngine.playClearChime()
        }
    }

    private fun formatAlertTitle(vehicleCount: Int): String {
        return if (vehicleCount == 1) {
            extension.getString(R.string.vehicle_singular, vehicleCount)
        } else {
            extension.getString(R.string.vehicle_plural, vehicleCount)
        }
    }

    private fun formatAlertDetail(level: ThreatLevel, threat: WidgetState.Threat): String {
        val distance = threat.nearestDistanceM
        if (distance <= 0) return extension.getString(R.string.widget_behind)
        val distanceText = Units.formatDistance(distance, extension.useImperial.value)
        return when (level) {
            ThreatLevel.CRITICAL -> "$distanceText!"
            ThreatLevel.WARNING -> distanceText
            ThreatLevel.APPROACHING -> distanceText
            ThreatLevel.CLEAR -> ""
        }
    }

    private fun getAlertColorRes(level: ThreatLevel): Int {
        return when (level) {
            ThreatLevel.CRITICAL -> R.color.alert_background_critical
            ThreatLevel.WARNING -> R.color.alert_background_warning
            ThreatLevel.APPROACHING -> R.color.alert_background_approaching
            ThreatLevel.CLEAR -> R.color.threat_clear
        }
    }

    /**
     * Force an immediate alert (for testing).
     */
    fun forceAlert(level: ThreatLevel) {
        val mockThreat = WidgetState.Threat(
            level = level,
            vehicleCount = 1,
            nearestDistanceM = when (level) {
                ThreatLevel.CRITICAL -> 15
                ThreatLevel.WARNING -> 40
                ThreatLevel.APPROACHING -> 100
                ThreatLevel.CLEAR -> 200
            }
        )
        dispatchAlerts(level, mockThreat, currentSettings, currentAlertSettings)
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopMonitoring()
        alertScope.cancel()
    }
}

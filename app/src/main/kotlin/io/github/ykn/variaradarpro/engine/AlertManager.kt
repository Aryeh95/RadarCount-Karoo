package io.github.ykn.variaradarpro.engine

import io.github.ykn.variaradarpro.R
import io.github.ykn.variaradarpro.VariaRadarExtension
import io.github.ykn.variaradarpro.data.PreferencesRepository
import io.github.ykn.variaradarpro.data.models.AlertSettings
import io.github.ykn.variaradarpro.data.models.PresetSettings
import io.github.ykn.variaradarpro.data.models.ScreenWakePolicy
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.TurnScreenOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages alert dispatch with thread-safe throttling.
 *
 * Combines radar state, user settings, and alert settings to determine
 * when and how to alert the user.
 */
class AlertManager(
    private val extension: VariaRadarExtension,
    private val radarEngine: RadarEngine,
    private val preferencesRepository: PreferencesRepository,
    private val nightModeManager: NightModeManager,
    private val soundEngine: SoundEngine,
    private val hapticEngine: HapticEngine,
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

    /** Combined input for the alert evaluation pipeline. */
    private data class AlertInput(
        val widgetState: WidgetState,
        val settings: PresetSettings,
        val alertSettings: AlertSettings,
        val speedKmh: Int
    )

    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val throttler = AlertThrottler()

    // Current cached settings
    private var currentSettings: PresetSettings = PresetSettings()
    private var currentAlertSettings: AlertSettings = AlertSettings()

    // Speed gate: current rider speed in km/h
    private val currentSpeedKmh = MutableStateFlow(0.0)
    private val speedConsumerId = AtomicReference<String?>(null)

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

        // Subscribe to rider speed for speed gate
        speedConsumerId.set(extension.karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.SPEED)
        ) { event: OnStreamState ->
            if (event.state is StreamState.Streaming) {
                val speedMs = (event.state as StreamState.Streaming).dataPoint.singleValue
                if (speedMs != null) {
                    currentSpeedKmh.value = speedMs * 3.6 // m/s → km/h
                }
            }
        })

        monitoringJob = alertScope.launch {
            // Include speed (rounded to int) so crossing the speed gate
            // threshold triggers re-evaluation even if radar state is unchanged.
            combine(
                radarEngine.widgetState,
                preferencesRepository.settingsFlow,
                preferencesRepository.alertSettingsFlow,
                currentSpeedKmh
            ) { widgetState, settings, alertSettings, speedKmh ->
                AlertInput(widgetState, settings, alertSettings, speedKmh.toInt())
            }
                .distinctUntilChanged()
                .collect { input ->
                    currentSettings = input.settings
                    currentAlertSettings = input.alertSettings

                    soundEngine.setSoundSet(input.settings.soundSet)
                    soundEngine.setVolume(input.settings.soundVolume)
                    hapticEngine.setEnabled(input.alertSettings.hapticAlert)

                    processWidgetState(input.widgetState)
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
        speedConsumerId.getAndSet(null)?.let { extension.karooSystem.removeConsumer(it) }
        throttler.reset()
    }

    private fun processWidgetState(state: WidgetState) {
        when (state) {
            is WidgetState.Threat -> {
                handleThreat(state)
            }
            is WidgetState.Clear -> {
                handleClear()
            }
            else -> {
                // No alerts for other states
            }
        }
    }

    private fun handleThreat(threat: WidgetState.Threat) {
        // Record vehicle detection for statistics (always, regardless of mute/speed gate)
        statisticsCollector.recordVehicleDetection(threat.vehicleCount, threat.nearestDistanceM)
        statisticsCollector.startThreatTracking()

        if (!currentAlertSettings.globalEnabled || extension.alertsMuted.value) {
            return
        }

        // Apply night mode overrides to thresholds
        val isNight = nightModeManager.isNightMode.value
        val effectiveSettings = if (isNight) {
            applyNightModeOverrides(currentSettings)
        } else {
            currentSettings
        }

        // Calculate threat level based on (possibly adjusted) thresholds
        val calculatedLevel = RadarEngine.calculateThreatLevel(
            distanceM = threat.nearestDistanceM,
            approachingThreshold = effectiveSettings.approachingDistanceM,
            warningThreshold = effectiveSettings.warningDistanceM,
            criticalThreshold = effectiveSettings.criticalDistanceM
        )

        // Use the more severe level
        val effectiveLevel = maxOf(threat.level, calculatedLevel, compareBy { it.ordinal })

        if (effectiveLevel == ThreatLevel.CLEAR) {
            return
        }

        // Speed gate: suppress APPROACHING alerts when stopped or moving slowly.
        // WARNING and CRITICAL always fire — a stopped cyclist is the most
        // vulnerable target (e.g. traffic light, intersection).
        val speedGate = currentSettings.speedGateKmh
        if (speedGate > 0 && currentSpeedKmh.value < speedGate
            && effectiveLevel == ThreatLevel.APPROACHING) {
            return
        }

        // Check throttling
        if (!throttler.shouldAlert(effectiveLevel, currentSettings.alertCooldownMs)) {
            return
        }

        // Dispatch alerts
        dispatchAlerts(effectiveLevel, threat)

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

    private fun dispatchAlerts(level: ThreatLevel, threat: WidgetState.Threat) {
        android.util.Log.d(TAG, "Dispatching alerts for level: $level, distance: ${threat.nearestDistanceM}m")

        // Visual alert
        if (currentAlertSettings.visualAlert) {
            dispatchVisualAlert(level, threat)
        }

        // Sound alert
        if (currentAlertSettings.soundAlert && currentSettings.soundEnabled) {
            soundEngine.playAlert(level)
        }

        // Haptic alert
        if (currentAlertSettings.hapticAlert) {
            hapticEngine.vibrate(level)
        }

        // Screen wake
        handleScreenWake(level)
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

    private fun handleScreenWake(level: ThreatLevel) {
        val policy = currentSettings.screenWakePolicy

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
        if (currentAlertSettings.hapticAlert) {
            hapticEngine.vibrateClear()
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
        if (distance <= 0) return "Behind"
        val distanceText = formatDistance(distance)
        return when (level) {
            ThreatLevel.CRITICAL -> "$distanceText!"
            ThreatLevel.WARNING -> distanceText
            ThreatLevel.APPROACHING -> distanceText
            ThreatLevel.CLEAR -> ""
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (extension.useImperial.value) {
            "${(meters * 3.281).toInt()}ft"
        } else {
            "${meters}m"
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
        dispatchAlerts(level, mockThreat)
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopMonitoring()
        hapticEngine.cancel()
        alertScope.cancel()
    }
}

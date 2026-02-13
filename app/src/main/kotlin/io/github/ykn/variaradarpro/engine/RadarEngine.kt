package io.github.ykn.variaradarpro.engine

import io.github.ykn.variaradarpro.data.models.ThreatLevel
import io.github.ykn.variaradarpro.data.models.WidgetState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Core radar data processing engine.
 *
 * Streams radar data from KarooSystemService using DataType.Type.RADAR
 * and transforms it into [WidgetState] for UI consumption.
 *
 * Karoo SDK provides a single RADAR data type with fields:
 * - Field.RADAR_THREAT_LEVEL (required)
 * - Field.RADAR_TARGET_1_RANGE through RADAR_TARGET_8_RANGE (optional)
 */
class RadarEngine(private val karooSystem: KarooSystemService) {

    companion object {
        private const val TAG = "RadarEngine"

        /** All target range fields for up to 8 vehicles */
        private val TARGET_RANGE_FIELDS = listOf(
            DataType.Field.RADAR_TARGET_1_RANGE,
            DataType.Field.RADAR_TARGET_2_RANGE,
            DataType.Field.RADAR_TARGET_3_RANGE,
            DataType.Field.RADAR_TARGET_4_RANGE,
            DataType.Field.RADAR_TARGET_5_RANGE,
            DataType.Field.RADAR_TARGET_6_RANGE,
            DataType.Field.RADAR_TARGET_7_RANGE,
            DataType.Field.RADAR_TARGET_8_RANGE,
        )

        internal fun mapThreatLevel(karooLevel: Int): ThreatLevel {
            return when (karooLevel) {
                0 -> ThreatLevel.CLEAR
                1 -> ThreatLevel.APPROACHING
                2 -> ThreatLevel.WARNING
                3 -> ThreatLevel.CRITICAL
                else -> ThreatLevel.CLEAR
            }
        }

        internal fun calculateThreatLevel(
            distanceM: Int,
            approachingThreshold: Int,
            warningThreshold: Int,
            criticalThreshold: Int
        ): ThreatLevel {
            return when {
                distanceM <= criticalThreshold -> ThreatLevel.CRITICAL
                distanceM <= warningThreshold -> ThreatLevel.WARNING
                distanceM <= approachingThreshold -> ThreatLevel.APPROACHING
                else -> ThreatLevel.CLEAR
            }
        }
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Consumer ID for cleanup
    private val radarConsumerId = AtomicReference<String?>(null)

    // Raw radar data
    private val _vehicleCount = MutableStateFlow(0)
    val vehicleCount: StateFlow<Int> = _vehicleCount.asStateFlow()

    private val _nearestDistanceM = MutableStateFlow(0)
    val nearestDistanceM: StateFlow<Int> = _nearestDistanceM.asStateFlow()

    private val _threatLevel = MutableStateFlow(ThreatLevel.CLEAR)
    val threatLevel: StateFlow<ThreatLevel> = _threatLevel.asStateFlow()

    // Per-target distances (up to 8)
    private val _targetDistances = MutableStateFlow<List<Int>>(emptyList())
    val targetDistances: StateFlow<List<Int>> = _targetDistances.asStateFlow()

    // Computed widget state
    private val _widgetState = MutableStateFlow<WidgetState>(WidgetState.NotConnected)
    val widgetState: StateFlow<WidgetState> = _widgetState.asStateFlow()

    // Connection tracking
    private val _isRadarConnected = MutableStateFlow(false)
    val isRadarConnected: StateFlow<Boolean> = _isRadarConnected.asStateFlow()

    private var wasEverConnected = false

    /**
     * Start streaming radar data from Karoo.
     */
    fun startStreaming() {
        android.util.Log.i(TAG, "Starting radar data stream")

        radarConsumerId.set(karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.RADAR)
        ) { event: OnStreamState ->
            handleStreamState(event.state)
        })
    }

    private fun handleStreamState(state: StreamState) {
        when (state) {
            is StreamState.Streaming -> {
                processRadarData(state.dataPoint.values)
            }
            is StreamState.NotAvailable -> {
                handleDisconnection()
            }
            is StreamState.Searching -> {
                _widgetState.value = WidgetState.Connecting
            }
            is StreamState.Idle -> {
                android.util.Log.d(TAG, "Radar stream idle")
            }
        }
    }

    /**
     * Stop streaming radar data.
     */
    fun stopStreaming() {
        android.util.Log.i(TAG, "Stopping radar data stream")
        radarConsumerId.getAndSet(null)?.let { karooSystem.removeConsumer(it) }
    }

    private fun processRadarData(values: Map<String, Double>) {
        engineScope.launch {
            // Check for radar hardware error
            val radarError = values[DataType.Field.RADAR_ERROR]
            if (radarError != null && radarError > 0) {
                android.util.Log.w(TAG, "Radar error reported: $radarError")
                _isRadarConnected.value = false
                _widgetState.value = WidgetState.ConnectionLost
                return@launch
            }

            // Read threat level
            val rawThreatLevel = values[DataType.Field.RADAR_THREAT_LEVEL]?.toInt() ?: 0
            _threatLevel.value = mapThreatLevel(rawThreatLevel)

            // Read individual target ranges
            val distances = mutableListOf<Int>()
            for (field in TARGET_RANGE_FIELDS) {
                val range = values[field]
                if (range != null && range > 0) {
                    distances.add(range.toInt())
                }
            }

            _targetDistances.value = distances
            _vehicleCount.value = distances.size
            _nearestDistanceM.value = distances.minOrNull() ?: 0

            // Update connection state
            _isRadarConnected.value = true
            wasEverConnected = true

            updateWidgetState()
        }
    }

    private fun handleDisconnection() {
        _isRadarConnected.value = false
        _widgetState.value = if (wasEverConnected) {
            WidgetState.ConnectionLost
        } else {
            WidgetState.NotConnected
        }
    }

    private fun updateWidgetState() {
        val count = _vehicleCount.value
        val distance = _nearestDistanceM.value
        val threat = _threatLevel.value

        _widgetState.value = if (count > 0) {
            WidgetState.Threat(
                level = threat,
                vehicleCount = count,
                nearestDistanceM = distance,
            )
        } else if (threat != ThreatLevel.CLEAR) {
            // Radar reports a threat but no individual target ranges yet.
            // This can happen when a vehicle is first detected before
            // range data resolves. Treat as 1 approaching vehicle.
            WidgetState.Threat(
                level = threat,
                vehicleCount = 1,
                nearestDistanceM = 0,
            )
        } else {
            WidgetState.Clear
        }
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopStreaming()
        engineScope.cancel()
    }
}

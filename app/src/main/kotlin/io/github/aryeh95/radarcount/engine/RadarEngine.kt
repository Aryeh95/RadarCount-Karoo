package io.github.aryeh95.radarcount.engine

import io.github.aryeh95.radarcount.data.models.ThreatLevel
import io.github.aryeh95.radarcount.data.models.WidgetState
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Core radar data processing engine.
 *
 * Streams radar data from KarooSystemService using DataType.Type.RADAR
 * and transforms it into [WidgetState] for UI consumption. Also counts
 * vehicles that pass the rider (see [VehiclePassCounter]).
 *
 * Karoo SDK provides a single RADAR data type with fields:
 * - Field.RADAR_THREAT_LEVEL (required)
 * - Field.RADAR_TARGET_1_RANGE through RADAR_TARGET_8_RANGE (optional)
 *
 * Packets are processed synchronously on the SDK callback thread and
 * serialised with a lock, so state never goes backwards even if the SDK
 * delivers two packets close together.
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

        internal fun toWidgetState(snapshot: RadarSnapshot): WidgetState {
            return if (snapshot.vehicleCount > 0) {
                WidgetState.Threat(
                    level = snapshot.threatLevel,
                    vehicleCount = snapshot.vehicleCount,
                    nearestDistanceM = snapshot.nearestDistanceM,
                )
            } else if (snapshot.threatLevel != ThreatLevel.CLEAR) {
                // Radar reports a threat but no individual target ranges yet.
                // This can happen when a vehicle is first detected before
                // range data resolves. Treat as 1 approaching vehicle.
                WidgetState.Threat(
                    level = snapshot.threatLevel,
                    vehicleCount = 1,
                    nearestDistanceM = 0,
                )
            } else {
                WidgetState.Clear
            }
        }
    }

    private val parser = RadarParser(
        threatLevelField = DataType.Field.RADAR_THREAT_LEVEL,
        errorField = DataType.Field.RADAR_ERROR,
        targetRangeFields = TARGET_RANGE_FIELDS
    )

    private val passCounter = VehiclePassCounter()
    private val closingSpeedTracker = ClosingSpeedTracker()
    private val lock = Any()

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

    // Vehicles that have passed the rider this ride / this lap
    private val _passCount = MutableStateFlow(0)
    val passCount: StateFlow<Int> = _passCount.asStateFlow()

    private val _lapPassCount = MutableStateFlow(0)
    val lapPassCount: StateFlow<Int> = _lapPassCount.asStateFlow()

    /**
     * Estimated closing speed of the nearest target in m/s, derived from
     * consecutive range samples (the Karoo SDK does not expose target speed).
     * 0 when no target is tracked or the target is holding/receding.
     */
    private val _closingSpeedMps = MutableStateFlow(0.0)
    val closingSpeedMps: StateFlow<Double> = _closingSpeedMps.asStateFlow()

    // Computed widget state (deduplicated: only changes are emitted)
    private val _widgetState = MutableStateFlow<WidgetState>(WidgetState.NotConnected)
    val widgetState: StateFlow<WidgetState> = _widgetState.asStateFlow()

    /**
     * Every processed packet, including repeats of an unchanged state.
     * Use this for time-based trackers that need one sample per packet.
     */
    private val _packets = MutableSharedFlow<WidgetState>(extraBufferCapacity = 32)
    val packets: SharedFlow<WidgetState> = _packets.asSharedFlow()

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
        synchronized(lock) {
            when (state) {
                is StreamState.Streaming -> processRadarData(state.dataPoint.values)
                is StreamState.NotAvailable -> handleDisconnection()
                is StreamState.Searching -> _widgetState.value = WidgetState.Connecting
                is StreamState.Idle -> android.util.Log.d(TAG, "Radar stream idle")
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
        val snapshot = when (val result = parser.parse(values)) {
            is RadarParseResult.Error -> {
                android.util.Log.w(TAG, "Radar error reported: ${result.code}")
                _isRadarConnected.value = false
                passCounter.clearTracking()
                closingSpeedTracker.reset()
                _closingSpeedMps.value = 0.0
                _widgetState.value = WidgetState.ConnectionLost
                _packets.tryEmit(WidgetState.ConnectionLost)
                return
            }
            is RadarParseResult.Data -> result.snapshot
        }

        _threatLevel.value = snapshot.threatLevel
        _targetDistances.value = snapshot.targetDistancesM
        _vehicleCount.value = snapshot.vehicleCount
        _nearestDistanceM.value = snapshot.nearestDistanceM

        val widgetState = toWidgetState(snapshot)

        closingSpeedTracker.addSample(snapshot.nearestDistanceM)
        _closingSpeedMps.value = (closingSpeedTracker.closingSpeedMps() ?: 0.0).coerceAtLeast(0.0)

        // Count passes using the effective tracked count (the widget state
        // treats a threat with no ranges as one vehicle).
        val trackedCount = (widgetState as? WidgetState.Threat)?.vehicleCount ?: 0
        val passed = passCounter.update(trackedCount, snapshot.nearestDistanceM)
        if (passed > 0) {
            android.util.Log.d(TAG, "$passed vehicle(s) passed, total=${passCounter.total}")
            _passCount.value = passCounter.total
            _lapPassCount.value = passCounter.lap
        }

        _isRadarConnected.value = true
        wasEverConnected = true

        _widgetState.value = widgetState
        _packets.tryEmit(widgetState)
    }

    private fun handleDisconnection() {
        _isRadarConnected.value = false
        passCounter.clearTracking()
        closingSpeedTracker.reset()
        _closingSpeedMps.value = 0.0
        val state = if (wasEverConnected) WidgetState.ConnectionLost else WidgetState.NotConnected
        _widgetState.value = state
        _packets.tryEmit(state)
    }

    /** Reset ride and lap pass counts (start of a new ride). */
    fun resetPassCounts() {
        synchronized(lock) {
            passCounter.reset()
            _passCount.value = 0
            _lapPassCount.value = 0
        }
    }

    /** Reset the lap pass count (lap button pressed / auto lap). */
    fun resetLapPassCount() {
        synchronized(lock) {
            passCounter.resetLap()
            _lapPassCount.value = 0
        }
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        stopStreaming()
    }
}

package io.github.aryeh95.radarcount.engine

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Listens to the Karoo's RADAR stream and turns each packet into the
 * [status] the fields show, the ride's pass count and the nearest car's
 * closing speed, with [TargetTracker] deciding what counts as a pass.
 *
 * Every stream event is handled on the SDK's callback thread under one
 * lock, so two packets delivered back to back can never be applied out
 * of order or half-applied.
 */
class RadarEngine(private val karooSystem: KarooSystemService) {

    private companion object {
        const val TAG = "RadarEngine"
    }

    private val parser = RadarParser(
        levelKey = DataType.Field.RADAR_THREAT_LEVEL,
        errorKey = DataType.Field.RADAR_ERROR,
        rangeKeys = with(DataType.Field) {
            listOf(
                RADAR_TARGET_1_RANGE, RADAR_TARGET_2_RANGE, RADAR_TARGET_3_RANGE, RADAR_TARGET_4_RANGE,
                RADAR_TARGET_5_RANGE, RADAR_TARGET_6_RANGE, RADAR_TARGET_7_RANGE, RADAR_TARGET_8_RANGE,
            )
        },
    )
    private val targetTracker = TargetTracker()
    private val lock = Any()
    /** The RADAR stream's consumer id while it is open. */
    private val consumer = AtomicReference<String?>(null)
    /** Set by the first packet and never cleared, so a later dropout reads as Lost rather than Off. */
    private var heardRadar = false
    /** False while the ride is paused: passes are still tracked but not counted, since the FIT file cannot carry them. */
    @Volatile private var countingEnabled = true

    private val _status = MutableStateFlow<RadarStatus>(RadarStatus.Off)
    val status: StateFlow<RadarStatus> = _status.asStateFlow()

    /**
     * The last packet while the radar is up, for the FIT file; null after
     * an error, a dropout or [stop]. A Searching spell keeps it.
     */
    @Volatile var packet: RadarPacket? = null
        private set

    /** Vehicles that have passed the rider this ride. */
    private val _passCount = MutableStateFlow(0)
    val passCount: StateFlow<Int> = _passCount.asStateFlow()

    /**
     * How fast the nearest target is closing in m/s, from its range history
     * (the Karoo does not report target speed). Null while no target has
     * been tracked long enough to tell.
     */
    private val _closingSpeedMps = MutableStateFlow<Double?>(null)
    val closingSpeedMps: StateFlow<Double?> = _closingSpeedMps.asStateFlow()

    /**
     * The last pass and its speed (see [TargetTracker.lastPass]), or null.
     * Published while the ride is paused too; null after a radar error,
     * disconnect, search, stop or ride reset.
     */
    private val _lastPass = MutableStateFlow<TargetTracker.Pass?>(null)
    val lastPass: StateFlow<TargetTracker.Pass?> = _lastPass.asStateFlow()

    // Diagnostics written to the FIT file in beta builds so a missed count can
    // be explained from the ride file rather than reconstructed.
    /** Times the tracker was wiped by a radar error or disconnect this ride. */
    @Volatile var trackerClears = 0
        private set
    private val packetsSinceRead = AtomicInteger(0)
    /** The rider's speed in m/s, recorded with each pass so a held absolute speed does not drift. */
    @Volatile var riderSpeedMps = 0.0
    /** Last heading fed to the tracker, or -1 if none yet. */
    @Volatile var lastHeadingDeg = -1.0
        private set

    /** Packets received since the previous call; the FIT writer calls this once a second. */
    fun takePacketCount(): Int = packetsSinceRead.getAndSet(0)
    val turnCount: Int get() = targetTracker.turnCount
    val rejectedTurnedAway: Int get() = targetTracker.rejectedTurnedAway
    val rejectedCrossingAfterTurn: Int get() = targetTracker.rejectedCrossingAfterTurn
    val rejectedNotPass: Int get() = targetTracker.rejectedNotPass

    /** Route tracker trace lines to a sink (a file, in beta builds). */
    fun setTrace(sink: ((String) -> Unit)?) {
        targetTracker.trace = sink
    }

    /** Open the RADAR stream. Paired with [stop] by the extension's sensor demand count. */
    fun start() {
        Log.i(TAG, "Opening the RADAR stream")
        consumer.set(karooSystem.addConsumer(OnStreamState.StartStreaming(DataType.Type.RADAR)) { event: OnStreamState ->
            synchronized(lock) { onStreamState(event.state) }
        })
    }

    /** Close the RADAR stream and forget every target; the pass count stays. */
    fun stop() {
        Log.i(TAG, "Closing the RADAR stream")
        consumer.getAndSet(null)?.let { karooSystem.removeConsumer(it) }
        synchronized(lock) { forgetTargets(RadarStatus.Off, fault = false) }
    }

    private fun onStreamState(state: StreamState) {
        when (state) {
            is StreamState.Streaming -> onPacket(state.dataPoint.values)
            is StreamState.NotAvailable -> forgetTargets(if (heardRadar) RadarStatus.Lost else RadarStatus.Off, fault = true)
            is StreamState.Searching -> {
                // Passes during the dropout go unrecorded: no stale held speed after it.
                targetTracker.dropPass()
                _lastPass.value = null
                _status.value = RadarStatus.Searching
            }
            is StreamState.Idle -> Log.d(TAG, "RADAR stream idle")
        }
    }

    private fun onPacket(values: Map<String, Double>) {
        packetsSinceRead.incrementAndGet()
        parser.errorCode(values)?.let { code ->
            Log.w(TAG, "Radar sent error code $code")
            forgetTargets(RadarStatus.Lost, fault = true)
            return
        }
        val next = parser.parse(values)
        val passed = targetTracker.update(next.rangesM, System.currentTimeMillis(), next.level, riderMps = riderSpeedMps)
        if (passed > 0 && countingEnabled) {
            _passCount.value += passed
            Log.d(TAG, "Counted $passed, ride total ${_passCount.value}")
        }
        _closingSpeedMps.value = targetTracker.nearestClosingSpeedMps()
        _lastPass.value = targetTracker.lastPass
        packet = next
        heardRadar = true
        _status.value = RadarStatus.Live.of(next)
    }

    /**
     * Drop every tracked target and what was shown from them. [fault] marks
     * an error or dropout, which the FIT diagnostics count.
     */
    private fun forgetTargets(status: RadarStatus, fault: Boolean) {
        packet = null
        targetTracker.clear()
        if (fault) trackerClears++
        _closingSpeedMps.value = null
        _lastPass.value = null
        _status.value = status
    }

    /** Count passes (true) or only track them (false, while the ride is paused). */
    fun setCountingEnabled(enabled: Boolean) {
        countingEnabled = enabled
    }

    /** Feed the rider's heading so the tracker can tell a turn from a pass. */
    fun updateHeading(degrees: Double) {
        lastHeadingDeg = degrees
        synchronized(lock) { targetTracker.updateHeading(degrees, System.currentTimeMillis()) }
    }

    /** Change how eager the pass counter is. Takes effect on the next packet. */
    fun setSensitivity(closeThresholdM: Int) {
        targetTracker.closeThresholdM = closeThresholdM
    }

    /** Reset the ride pass count (start of a new ride). */
    fun resetPassCounts() {
        synchronized(lock) {
            targetTracker.clear()
            targetTracker.resetHeading()
            targetTracker.resetDiagnostics()
            trackerClears = 0
            _passCount.value = 0
            _lastPass.value = null
        }
    }
}

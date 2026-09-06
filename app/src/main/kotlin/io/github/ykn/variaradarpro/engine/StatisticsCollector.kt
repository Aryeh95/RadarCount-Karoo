package io.github.ykn.variaradarpro.engine

import io.github.ykn.variaradarpro.data.database.RideStatisticsDao
import io.github.ykn.variaradarpro.data.database.RideStatisticsEntity
import io.github.ykn.variaradarpro.data.models.ThreatLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Collects ride session statistics.
 *
 * Thread-safe using atomic operations.
 * Optionally persists statistics to Room database.
 */
class StatisticsCollector(
    private val dao: RideStatisticsDao? = null
) {

    companion object {
        private const val TAG = "StatisticsCollector"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Session statistics
    private val sessionStartTime = AtomicLong(0L)
    private val totalAlerts = AtomicInteger(0)
    private val approachingAlerts = AtomicInteger(0)
    private val warningAlerts = AtomicInteger(0)
    private val criticalAlerts = AtomicInteger(0)
    private val maxVehicleCount = AtomicInteger(0)
    private val closestApproachM = AtomicInteger(Int.MAX_VALUE)
    private val threatTimeMs = AtomicLong(0L)
    private val lastThreatStartTime = AtomicLong(0L)
    private val vehiclesPassed = AtomicInteger(0)

    // Observable state
    private val _currentStats = MutableStateFlow(SessionStats())
    val currentStats: StateFlow<SessionStats> = _currentStats.asStateFlow()

    /**
     * Start a new session. No-op if a session is already active, so a
     * pause/resume (which re-emits RideState.Recording) does not wipe stats.
     */
    fun startSession() {
        if (isSessionActive()) {
            android.util.Log.d(TAG, "Session already active, ignoring start")
            return
        }
        android.util.Log.i(TAG, "Starting new statistics session")
        sessionStartTime.set(System.currentTimeMillis())
        resetCounters()
        updateState()
    }

    /**
     * End current session and return final stats.
     * Persists to database if a session was active and any vehicle was seen.
     */
    fun endSession(): SessionStats {
        if (!isSessionActive()) {
            return buildCurrentStats()
        }
        android.util.Log.i(TAG, "Ending statistics session")

        // End any ongoing threat time tracking
        endThreatTracking()

        val stats = buildCurrentStats()
        val startTime = sessionStartTime.get()

        // Persist to database
        if (dao != null && stats.hasVehicleData()) {
            val entity = RideStatisticsEntity(
                rideId = UUID.randomUUID().toString(),
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                durationMs = stats.sessionDurationMs,
                totalAlerts = stats.totalAlerts,
                approachingAlerts = stats.approachingAlerts,
                warningAlerts = stats.warningAlerts,
                criticalAlerts = stats.criticalAlerts,
                maxVehicleCount = stats.maxVehicleCount,
                closestApproachM = stats.closestApproachM,
                threatTimeMs = threatTimeMs.get(),
                vehiclesPassed = stats.vehiclesPassed
            )
            scope.launch {
                try {
                    dao.insert(entity)
                    android.util.Log.i(TAG, "Session saved to database: ${entity.rideId}")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to save session", e)
                }
            }
        }

        sessionStartTime.set(0L)
        resetCounters()
        return stats
    }

    /**
     * Start tracking threat presence time.
     */
    fun startThreatTracking() {
        lastThreatStartTime.compareAndSet(0L, System.currentTimeMillis())
    }

    /**
     * End tracking threat presence time.
     */
    fun endThreatTracking() {
        val startTime = lastThreatStartTime.getAndSet(0L)
        if (startTime > 0) {
            val elapsed = System.currentTimeMillis() - startTime
            threatTimeMs.addAndGet(elapsed)
        }
    }

    /**
     * Record an alert.
     */
    fun recordAlert(level: ThreatLevel) {
        totalAlerts.incrementAndGet()

        when (level) {
            ThreatLevel.APPROACHING -> approachingAlerts.incrementAndGet()
            ThreatLevel.WARNING -> warningAlerts.incrementAndGet()
            ThreatLevel.CRITICAL -> criticalAlerts.incrementAndGet()
            ThreatLevel.CLEAR -> { /* No-op */ }
        }

        updateState()
    }

    /**
     * Record vehicle detection.
     */
    fun recordVehicleDetection(count: Int, distanceM: Int) {
        // Update max vehicle count
        var current = maxVehicleCount.get()
        while (count > current && !maxVehicleCount.compareAndSet(current, count)) {
            current = maxVehicleCount.get()
        }

        // Update closest approach
        if (distanceM > 0) {
            current = closestApproachM.get()
            while (distanceM < current && !closestApproachM.compareAndSet(current, distanceM)) {
                current = closestApproachM.get()
            }
        }

        updateState()
    }

    /**
     * Record the running total of vehicles that have passed this ride.
     */
    fun recordVehiclesPassed(total: Int) {
        vehiclesPassed.set(total)
        updateState()
    }

    private fun resetCounters() {
        totalAlerts.set(0)
        approachingAlerts.set(0)
        warningAlerts.set(0)
        criticalAlerts.set(0)
        maxVehicleCount.set(0)
        closestApproachM.set(Int.MAX_VALUE)
        threatTimeMs.set(0L)
        lastThreatStartTime.set(0L)
        vehiclesPassed.set(0)
    }

    private fun updateState() {
        _currentStats.value = buildCurrentStats()
    }

    private fun buildCurrentStats(): SessionStats {
        val startTime = sessionStartTime.get()
        val durationMs = if (startTime > 0) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }

        val closest = closestApproachM.get()

        return SessionStats(
            sessionDurationMs = durationMs,
            totalAlerts = totalAlerts.get(),
            approachingAlerts = approachingAlerts.get(),
            warningAlerts = warningAlerts.get(),
            criticalAlerts = criticalAlerts.get(),
            maxVehicleCount = maxVehicleCount.get(),
            closestApproachM = if (closest == Int.MAX_VALUE) null else closest,
            vehiclesPassed = vehiclesPassed.get()
        )
    }

    /**
     * Cancel the coroutine scope.
     */
    fun destroy() {
        scope.cancel()
    }

    /**
     * Check if session is active.
     */
    fun isSessionActive(): Boolean = sessionStartTime.get() > 0
}

/**
 * Ride session statistics snapshot.
 */
data class SessionStats(
    val sessionDurationMs: Long = 0L,
    val totalAlerts: Int = 0,
    val approachingAlerts: Int = 0,
    val warningAlerts: Int = 0,
    val criticalAlerts: Int = 0,
    val maxVehicleCount: Int = 0,
    val closestApproachM: Int? = null,
    val vehiclesPassed: Int = 0
) {
    /**
     * Format duration as HH:MM:SS.
     */
    fun formatDuration(): String {
        val seconds = (sessionDurationMs / 1000) % 60
        val minutes = (sessionDurationMs / (1000 * 60)) % 60
        val hours = sessionDurationMs / (1000 * 60 * 60)
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Check if any alerts were recorded.
     */
    fun hasAlerts(): Boolean = totalAlerts > 0

    /**
     * Check if the radar saw any vehicle at all during the session.
     */
    fun hasVehicleData(): Boolean = maxVehicleCount > 0 || vehiclesPassed > 0 || totalAlerts > 0
}

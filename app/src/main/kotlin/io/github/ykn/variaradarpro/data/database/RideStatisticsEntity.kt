package io.github.ykn.variaradarpro.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing ride statistics history.
 */
@Entity(tableName = "ride_statistics")
data class RideStatisticsEntity(
    @PrimaryKey
    val rideId: String,

    /** Timestamp when the ride started */
    val startTime: Long,

    /** Timestamp when the ride ended */
    val endTime: Long,

    /** Total ride duration in milliseconds */
    val durationMs: Long,

    /** Total number of alerts triggered */
    val totalAlerts: Int,

    /** Number of approaching-level alerts */
    val approachingAlerts: Int,

    /** Number of warning-level alerts */
    val warningAlerts: Int,

    /** Number of critical-level alerts */
    val criticalAlerts: Int,

    /** Maximum vehicles detected at once */
    val maxVehicleCount: Int,

    /** Closest approach distance in meters (null if no vehicles detected) */
    val closestApproachM: Int?,

    /** Total time with threats present in milliseconds */
    val threatTimeMs: Long
)

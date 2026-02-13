package io.github.ykn.variaradarpro.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ride statistics.
 */
@Dao
interface RideStatisticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(statistics: RideStatisticsEntity)

    @Query("SELECT * FROM ride_statistics ORDER BY startTime DESC")
    fun getAllRides(): Flow<List<RideStatisticsEntity>>

    @Query("SELECT * FROM ride_statistics ORDER BY startTime DESC LIMIT :limit")
    fun getRecentRides(limit: Int): Flow<List<RideStatisticsEntity>>

    @Query("SELECT * FROM ride_statistics WHERE rideId = :rideId")
    suspend fun getRideById(rideId: String): RideStatisticsEntity?

    @Query("SELECT * FROM ride_statistics WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    fun getRidesInRange(startTime: Long, endTime: Long): Flow<List<RideStatisticsEntity>>

    @Query("DELETE FROM ride_statistics WHERE rideId = :rideId")
    suspend fun deleteRide(rideId: String)

    @Query("DELETE FROM ride_statistics")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ride_statistics")
    suspend fun getRideCount(): Int

    @Query("SELECT SUM(totalAlerts) FROM ride_statistics")
    suspend fun getTotalAlertsAllTime(): Int?

    @Query("SELECT SUM(durationMs) FROM ride_statistics")
    suspend fun getTotalRideTimeMs(): Long?

    @Query("SELECT MIN(closestApproachM) FROM ride_statistics WHERE closestApproachM IS NOT NULL")
    suspend fun getClosestApproachAllTime(): Int?
}

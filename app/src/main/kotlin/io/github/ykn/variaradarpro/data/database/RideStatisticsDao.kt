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

    @Query("SELECT * FROM ride_statistics ORDER BY startTime DESC LIMIT :limit")
    fun getRecentRides(limit: Int): Flow<List<RideStatisticsEntity>>

    @Query("DELETE FROM ride_statistics")
    suspend fun deleteAll()
}

package io.github.ykn.variaradarpro.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for eiRadar.
 */
@Database(
    entities = [RideStatisticsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VariaRadarDatabase : RoomDatabase() {

    abstract fun rideStatisticsDao(): RideStatisticsDao

    companion object {
        private const val DATABASE_NAME = "varia_radar_db"

        @Volatile
        private var INSTANCE: VariaRadarDatabase? = null

        fun getInstance(context: Context): VariaRadarDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): VariaRadarDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                VariaRadarDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}

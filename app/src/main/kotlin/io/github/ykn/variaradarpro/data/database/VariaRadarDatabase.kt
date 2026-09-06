package io.github.ykn.variaradarpro.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for eiRadar.
 */
@Database(
    entities = [RideStatisticsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VariaRadarDatabase : RoomDatabase() {

    abstract fun rideStatisticsDao(): RideStatisticsDao

    companion object {
        private const val DATABASE_NAME = "varia_radar_db"

        /** v1 → v2: add vehiclesPassed column. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ride_statistics ADD COLUMN vehiclesPassed INTEGER NOT NULL DEFAULT 0")
            }
        }

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
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}

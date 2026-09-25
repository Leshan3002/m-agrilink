package com.example.m_agrilink.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CropAdvisory::class, HydrologicalAlert::class, ExpertForumPost::class, FarmerProfile::class, CropSearchHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AgriLinkDatabase : RoomDatabase() {

    abstract fun farmerDao(): FarmerDao

    companion object {
        @Volatile
        private var INSTANCE: AgriLinkDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `farmer_profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `displayName` TEXT NOT NULL, `county` TEXT NOT NULL, `acreage` REAL NOT NULL, `authProvider` TEXT NOT NULL, `externalUid` TEXT, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `crop_search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `cropName` TEXT NOT NULL, `county` TEXT NOT NULL, `source` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_crop_search_history_cropName` ON `crop_search_history` (`cropName`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_crop_advisory_cropName_seasonalMarker` ON `crop_advisory` (`cropName`, `seasonalMarker`)")
            }
        }

        fun getDatabase(context: Context): AgriLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgriLinkDatabase::class.java,
                    "magrilink_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

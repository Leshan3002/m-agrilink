package com.example.m_agrilink.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [CropAdvisory::class, HydrologicalAlert::class, ExpertForumPost::class, FarmerProfile::class, CropSearchHistory::class, TransporterProfile::class, TransportTask::class],
    version = 3,
    exportSchema = false
)
abstract class AgriLinkDatabase : RoomDatabase() {

    abstract fun farmerDao(): FarmerDao

    abstract fun transportDao(): TransportDao

    companion object {
        @Volatile
        private var INSTANCE: AgriLinkDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `farmer_profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `displayName` TEXT NOT NULL, `county` TEXT NOT NULL, `acreage` REAL NOT NULL, `authProvider` TEXT NOT NULL, `externalUid` TEXT, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `crop_search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` INTEGER NOT NULL, `cropName` TEXT NOT NULL, `county` TEXT NOT NULL, `source` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_crop_search_history_cropName` ON `crop_search_history` (`cropName`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_crop_advisory_cropName_seasonalMarker` ON `crop_advisory` (`cropName`, `seasonalMarker`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `transporter_profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `driverName` TEXT NOT NULL, `phone` TEXT NOT NULL, `capacity` TEXT NOT NULL, `baseTown` TEXT NOT NULL, `route` TEXT NOT NULL, `available` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `transport_task` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transporterName` TEXT NOT NULL, `phone` TEXT NOT NULL, `crop` TEXT NOT NULL, `fromCounty` TEXT NOT NULL, `toHub` TEXT NOT NULL, `status` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transport_task_status` ON `transport_task` (`status`)")
            }
        }

        fun getDatabase(context: Context): AgriLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val passphrase = DatabaseKeyManager.getPassphrase(appContext)
                SqlCipherMigrator.ensureEncrypted(appContext, passphrase)
                val instance = Room.databaseBuilder(
                    appContext,
                    AgriLinkDatabase::class.java,
                    "magrilink_database"
                )
                .openHelperFactory(SupportFactory(passphrase))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

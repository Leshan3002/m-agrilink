package com.example.m_agrilink.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 1. DATA LAYER: Room Database
 */
@Database(entities = [MarketPrice::class, MarketPriceCache::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun marketPriceDao(): MarketPriceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agrilink_app_database"
                )
                .fallbackToDestructiveMigration() // Required for development safety
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

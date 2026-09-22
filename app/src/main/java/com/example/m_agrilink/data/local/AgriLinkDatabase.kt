package com.example.m_agrilink.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CropAdvisory::class, HydrologicalAlert::class, ExpertForumPost::class],
    version = 1,
    exportSchema = false
)
abstract class AgriLinkDatabase : RoomDatabase() {
    
    // Abstract definitions can be added here once DAOs are implemented
    
    companion object {
        @Volatile
        private var INSTANCE: AgriLinkDatabase? = null

        fun getDatabase(context: Context): AgriLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgriLinkDatabase::class.java,
                    "magrilink_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

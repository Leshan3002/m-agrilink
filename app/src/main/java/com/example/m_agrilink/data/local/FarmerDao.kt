package com.example.m_agrilink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FarmerDao {

    // --- Profile (single local row now; Firebase UID maps here later) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: FarmerProfile): Long

    @Query("SELECT * FROM farmer_profile ORDER BY id ASC LIMIT 1")
    suspend fun getProfile(): FarmerProfile?

    @Query("UPDATE farmer_profile SET county = :county, updatedAt = :now WHERE id = :id")
    suspend fun updateCounty(id: Long, county: String, now: Long = System.currentTimeMillis())

    // --- Crops history ---
    @Insert
    suspend fun addHistory(entry: CropSearchHistory): Long

    @Query("SELECT DISTINCT cropName FROM crop_search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentCropNames(limit: Int = 8): List<String>

    // --- Live-brief cache (stored inside crop_advisory as LIVE_BRIEF rows) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLiveBrief(advisory: CropAdvisory)

    @Query("SELECT * FROM crop_advisory WHERE cropName = :crop AND seasonalMarker = 'LIVE_BRIEF' LIMIT 1")
    suspend fun getLiveBrief(crop: String): CropAdvisory?
}

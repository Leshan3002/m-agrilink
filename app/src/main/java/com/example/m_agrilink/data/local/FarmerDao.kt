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

    @Query("UPDATE farmer_profile SET displayName = :name, updatedAt = :now WHERE id = :id")
    suspend fun updateDisplayName(id: Long, name: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE farmer_profile SET email = :email, phone = :phone, county = :county, acreage = :acreage, updatedAt = :now WHERE id = :id")
    suspend fun updateProfileDetails(id: Long, email: String, phone: String, county: String, acreage: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE farmer_profile SET displayName = :name, email = :email, phone = :phone, county = :county, acreage = :acreage, updatedAt = :now WHERE id = :id")
    suspend fun updateFullProfile(id: Long, name: String, email: String, phone: String, county: String, acreage: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE farmer_profile SET authProvider = :provider, externalUid = :uid, email = :email, displayName = :name, photoUrl = :photoUrl, updatedAt = :now WHERE id = :id")
    suspend fun linkGoogleAccount(id: Long, provider: String, uid: String, email: String, name: String, photoUrl: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE farmer_profile SET cookieChoice = :choice, consentAnalytics = :analytics, consentPersonalization = :personalization, consentMarketing = :marketing, updatedAt = :now WHERE id = :id")
    suspend fun updateCookieConsent(id: Long, choice: String, analytics: Boolean, personalization: Boolean, marketing: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM crop_search_history")
    suspend fun clearHistory()

    @Query("DELETE FROM crop_search_history WHERE id NOT IN (SELECT id FROM crop_search_history ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun pruneHistory(keep: Int = 200)

    @Query("DELETE FROM farmer_profile")
    suspend fun clearProfiles()

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

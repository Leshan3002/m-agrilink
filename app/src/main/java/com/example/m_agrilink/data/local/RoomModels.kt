package com.example.m_agrilink.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * M-AgriLink Core Engine.
 * 2. LOCAL OFFLINE DATA LAYER (Jetpack Room DB Architecture Entities)
 */

@Entity(
    tableName = "crop_advisory",
    indices = [Index(value = ["cropName", "seasonalMarker"], unique = true)]
)
data class CropAdvisory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cropName: String,
    val seasonalMarker: String, // Land Prep, Planting, Active Management, Harvest
    val directives: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "hydrological_alert")
data class HydrologicalAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val region: String,
    val temp: String,
    val evapotranspirationEto: String,
    val waterDeficitShortfall: String,
    val riskLevel: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "expert_forum_post")
data class ExpertForumPost(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val author: String,
    val textContent: String,
    val audioCachedPath: String?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 3. FARMER ACCOUNT LAYER (local profile now, Firebase UID seam later).
 * SQLite/Room is fully ACID: each write is an atomic, durable transaction.
 */
@Entity(tableName = "farmer_profile")
data class FarmerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String = "Guest Farmer",
    val county: String = "Baringo",
    val acreage: Double = 1.0,
    val email: String = "",
    val phone: String = "",
    val photoUrl: String? = null,
    val authProvider: String = "local", // "local" now, "google" after Gmail link
    val externalUid: String? = null, // Google sub / Firebase UID plugs in here later
    val cookieChoice: String = "pending", // pending | accepted | custom | declined
    val consentAnalytics: Boolean = false,
    val consentPersonalization: Boolean = false,
    val consentMarketing: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "crop_search_history",
    indices = [Index(value = ["cropName"]), Index(value = ["profileId", "timestamp"])]
)
data class CropSearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 0,
    val cropName: String,
    val county: String,
    val source: String, // LOCAL, GEMINI, WIKI, CACHE
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 4. SUBSIDY BASELINE CACHE (Ministry of Agriculture input telemetry).
 * Live telemetry snapshots persisted locally so the planner survives
 * cellular signal drops in remote fields.
 */
@Entity(tableName = "subsidy_baseline")
data class SubsidyBaseline(
    @PrimaryKey val key: String,
    val priceKes: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 5. LAYER 5 — IMAGE DIAGNOSTIC HISTORY CACHE (offline-first).
 * One row per vision run: crop + county + model verdict + source.
 * Only metadata/text is stored (never raw bitmap bytes) so the table
 * stays small and survives signal drops. Ordered by timestamp DESC.
 */
@Entity(
    tableName = "scan_diagnostic",
    indices = [Index(value = ["timestamp"]), Index(value = ["cropName", "timestamp"])]
)
data class ScanDiagnostic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cropName: String,
    val county: String,
    val verdict: String,
    val source: String, // GEMINI_VISION, OFFLINE, CACHE
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 6. COUNTY BRIEF CACHE (location-scoped durability anchor).
 * One row per county: the latest verified brief for their active crop in
 * that county. Keyed by county (not a static value) so switching counties
 * instantly drops the previous region's arrays and emits fresh context.
 */
@Entity(tableName = "crop_brief_table")
data class CropBriefEntity(
    @PrimaryKey val countyName: String,
    val cropName: String,
    val briefText: String,
    val source: String, // GEMINI, OPENFARM, WIKI, CACHE, OFFLINE
    val updatedAt: Long = System.currentTimeMillis()
)

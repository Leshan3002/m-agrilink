package com.example.m_agrilink.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
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
    val authProvider: String = "local", // "local" now, "firebase" later
    val externalUid: String? = null, // Firebase UID plugs in here later
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "crop_search_history",
    indices = [Index(value = ["cropName"])]
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
 * 4. LORRY MARKETPLACE (driver-registered lorries + farmer hire tracking).
 */
@Entity(tableName = "transporter_profile")
data class TransporterProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val driverName: String,
    val phone: String,
    val capacity: String, // e.g. "10T"
    val baseTown: String,
    val route: String, // e.g. "Marigat → Nairobi Hub"
    val available: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transport_task",
    indices = [Index(value = ["status"])]
)
data class TransportTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transporterName: String,
    val phone: String,
    val crop: String,
    val fromCounty: String,
    val toHub: String = "Nairobi Hub",
    val status: String = "REQUESTED", // REQUESTED, EN_ROUTE, DELIVERED, CANCELLED
    val updatedAt: Long = System.currentTimeMillis()
)

package com.example.m_agrilink.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 2. LOCAL OFFLINE DATA LAYER (Jetpack Room DB Architecture Entities)
 */

@Entity(tableName = "crop_advisory")
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

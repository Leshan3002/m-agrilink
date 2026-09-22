package com.example.m_agrilink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MODULE 1: LOCAL ROOM CACHE LAYER
 * Offline-first SQLite infrastructure for commodity value persistence.
 */
@Entity
data class MarketPriceCache(
    @PrimaryKey val cropName: String,
    val localHubPrice: Double,
    val targetHubPrice: Double,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

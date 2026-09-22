package com.example.m_agrilink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1. DATA LAYER: MarketPrice Entity
 */
@Entity(tableName = "market_prices")
data class MarketPrice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val commodityName: String,
    val pricePer90kg: Double,
    val hubName: String,
    val countyName: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

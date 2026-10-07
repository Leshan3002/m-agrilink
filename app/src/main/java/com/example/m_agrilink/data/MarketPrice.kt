package com.example.m_agrilink.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 1. DATA LAYER: MarketPrice Entity
 *
 * Composite county + commodity index keeps the corridor lookups
 * (getPricesByCounty / observePricesByCounty) on an indexed seek
 * instead of a full table scan as the 47-county matrix grows.
 */
@Entity(
    tableName = "market_prices",
    indices = [Index(value = ["countyName", "commodityName"])]
)
data class MarketPrice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val commodityName: String,
    val pricePer90kg: Double,
    val hubName: String,
    val countyName: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

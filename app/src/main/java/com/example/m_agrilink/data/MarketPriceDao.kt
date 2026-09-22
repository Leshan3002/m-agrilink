package com.example.m_agrilink.data

import androidx.room.*

/**
 * 1. DATA LAYER: MarketPrice DAO
 */
@Dao
interface MarketPriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePrice(price: MarketPrice)

    @Query("SELECT * FROM market_prices")
    suspend fun getAllPrices(): List<MarketPrice>

    @Query("SELECT * FROM market_prices WHERE countyName = :countyName")
    suspend fun getPricesByCounty(countyName: String): List<MarketPrice>

    @Query("DELETE FROM market_prices")
    suspend fun clearCaches()

    // MODULE 1: LOCAL ROOM CACHE LAYER ADDITIONS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cachePrice(price: MarketPriceCache)

    @Query("SELECT * FROM MarketPriceCache WHERE cropName = :crop")
    suspend fun getCachedPrice(crop: String): MarketPriceCache?
}

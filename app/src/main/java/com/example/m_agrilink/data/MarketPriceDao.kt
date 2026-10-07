package com.example.m_agrilink.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 1. DATA LAYER: MarketPrice DAO
 *
 * Every one-shot read is a `suspend` coroutine call (never blocks the
 * main thread). The `observe*` queries return reactive [Flow] streams so
 * the UI re-renders lag-free the moment cached rows change.
 */
@Dao
interface MarketPriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePrice(price: MarketPrice)

    @Query("SELECT * FROM market_prices")
    suspend fun getAllPrices(): List<MarketPrice>

    @Query("SELECT * FROM market_prices")
    fun observeAllPrices(): Flow<List<MarketPrice>>

    @Query("SELECT * FROM market_prices WHERE countyName = :countyName")
    suspend fun getPricesByCounty(countyName: String): List<MarketPrice>

    @Query("SELECT * FROM market_prices WHERE countyName = :countyName")
    fun observePricesByCounty(countyName: String): Flow<List<MarketPrice>>

    @Query("DELETE FROM market_prices")
    suspend fun clearCaches()

    // MODULE 1: LOCAL ROOM CACHE LAYER ADDITIONS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cachePrice(price: MarketPriceCache)

    @Query("SELECT * FROM MarketPriceCache WHERE cropName = :crop")
    suspend fun getCachedPrice(crop: String): MarketPriceCache?

    @Query("SELECT * FROM MarketPriceCache WHERE cropName = :crop")
    fun observeCachedPrice(crop: String): Flow<MarketPriceCache?>
}

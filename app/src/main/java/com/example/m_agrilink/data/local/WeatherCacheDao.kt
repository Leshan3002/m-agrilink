package com.example.m_agrilink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * County-scoped brief cache (location durability anchor).
 * Reactive Flow queries bind directly to their active county argument, so
 * the UI drops stale regional arrays the moment they switch corridors.
 * Telemetry wording keeps inclusive they/their/them.
 */
@Dao
interface WeatherCacheDao {

    @Query("SELECT * FROM crop_brief_table WHERE countyName = :countyName LIMIT 1")
    fun getCropBriefByCounty(countyName: String): Flow<CropBriefEntity?>

    @Query("SELECT * FROM crop_brief_table WHERE countyName = :countyName LIMIT 1")
    suspend fun getCropBriefOnce(countyName: String): CropBriefEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrief(entry: CropBriefEntity)

    @Query("DELETE FROM crop_brief_table WHERE countyName = :countyName")
    suspend fun clearCounty(countyName: String)

    @Query("DELETE FROM crop_brief_table")
    suspend fun clearAll()
}

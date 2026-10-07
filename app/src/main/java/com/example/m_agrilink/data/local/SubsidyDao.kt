package com.example.m_agrilink.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubsidyDao {

    @Upsert
    suspend fun saveAll(rows: List<SubsidyBaseline>)

    @Query("SELECT * FROM subsidy_baseline")
    suspend fun loadAll(): List<SubsidyBaseline>

    @Query("SELECT * FROM subsidy_baseline")
    fun observeAll(): Flow<List<SubsidyBaseline>>
}

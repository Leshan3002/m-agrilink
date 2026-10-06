package com.example.m_agrilink.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SubsidyDao {

    @Upsert
    suspend fun saveAll(rows: List<SubsidyBaseline>)

    @Query("SELECT * FROM subsidy_baseline")
    suspend fun loadAll(): List<SubsidyBaseline>
}

package com.example.m_agrilink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Layer 5 — image diagnostic history cache.
 * Append-only log of vision runs for offline recall.
 */
@Dao
interface ScanDiagnosticDao {

    @Insert
    suspend fun insert(entry: ScanDiagnostic): Long

    @Query("SELECT * FROM scan_diagnostic ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 20): List<ScanDiagnostic>

    @Query("SELECT * FROM scan_diagnostic ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<ScanDiagnostic>>

    @Query("SELECT * FROM scan_diagnostic WHERE cropName = :crop ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentForCrop(crop: String, limit: Int = 20): List<ScanDiagnostic>

    @Query("DELETE FROM scan_diagnostic WHERE id NOT IN (SELECT id FROM scan_diagnostic ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun prune(keep: Int = 100)

    @Query("DELETE FROM scan_diagnostic")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM scan_diagnostic")
    suspend fun count(): Int
}

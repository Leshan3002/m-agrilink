package com.example.m_agrilink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TransportDao {

    @Insert
    suspend fun registerLorry(profile: TransporterProfile): Long

    @Query("SELECT * FROM transporter_profile ORDER BY createdAt DESC")
    suspend fun allLorries(): List<TransporterProfile>

    @Query("UPDATE transporter_profile SET available = :available WHERE id = :id")
    suspend fun setAvailable(id: Long, available: Boolean)

    @Insert
    suspend fun createTask(task: TransportTask): Long

    @Query("SELECT * FROM transport_task ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentTasks(limit: Int = 10): List<TransportTask>

    @Query("UPDATE transport_task SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setTaskStatus(id: Long, status: String, now: Long = System.currentTimeMillis())
}

package com.reink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadLaterDao {

    @Query("SELECT * FROM read_later ORDER BY savedAt DESC")
    fun getAll(): Flow<List<ReadLaterEntity>>

    @Query("SELECT * FROM read_later WHERE id = :id")
    suspend fun getById(id: Long): ReadLaterEntity?

    @Query("SELECT * FROM read_later WHERE fetchStatus = :status")
    suspend fun getByStatus(status: String): List<ReadLaterEntity>

    @Insert
    suspend fun insert(item: ReadLaterEntity): Long

    @Query("UPDATE read_later SET fetchStatus = :status, title = :title, contentHtml = :contentHtml, sourceDomain = :sourceDomain WHERE id = :id")
    suspend fun updateContent(id: Long, status: String, title: String, contentHtml: String, sourceDomain: String? = null)

    @Query("UPDATE read_later SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM read_later WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM read_later WHERE url = :url")
    suspend fun countByUrl(url: String): Int

    @Query("UPDATE read_later SET fetchStatus = 'PENDING' WHERE fetchStatus = 'FAILED'")
    suspend fun resetFailed(): Int
}

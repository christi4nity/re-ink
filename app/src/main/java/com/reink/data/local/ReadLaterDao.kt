package com.reink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadLaterDao {

    @Query("SELECT * FROM read_later WHERE isArchived = 0 ORDER BY savedAt DESC")
    fun getAll(): Flow<List<ReadLaterEntity>>

    @Query("SELECT * FROM read_later WHERE id = :id")
    suspend fun getById(id: Long): ReadLaterEntity?

    @Query("SELECT * FROM read_later WHERE fetchStatus = :status")
    suspend fun getByStatus(status: String): List<ReadLaterEntity>

    @Insert
    suspend fun insert(item: ReadLaterEntity): Long

    @Query("UPDATE read_later SET fetchStatus = :status, title = :title, contentHtml = :contentHtml, sourceDomain = :sourceDomain, excerpt = :excerpt WHERE id = :id")
    suspend fun updateContent(id: Long, status: String, title: String, contentHtml: String, sourceDomain: String? = null, excerpt: String? = null)

    @Query("UPDATE read_later SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM read_later WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM read_later WHERE url = :url")
    suspend fun countByUrl(url: String): Int

    @Query("UPDATE read_later SET fetchStatus = 'PENDING' WHERE fetchStatus = 'FAILED'")
    suspend fun resetFailed(): Int

    @Query("UPDATE read_later SET isArchived = 1, archivedAt = :now WHERE id = :id")
    suspend fun archiveById(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE read_later SET isArchived = 0, archivedAt = NULL WHERE id = :id")
    suspend fun unarchiveById(id: Long)

    @Query("SELECT * FROM read_later WHERE isArchived = 1 ORDER BY archivedAt DESC")
    fun getArchived(): Flow<List<ReadLaterEntity>>
}

package com.reink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadLaterDao {

    @Query("SELECT * FROM read_later WHERE isArchived = 0 AND isDeleted = 0 ORDER BY savedAt DESC")
    fun getAll(): Flow<List<ReadLaterEntity>>

    @Query("SELECT * FROM read_later WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: Long): ReadLaterEntity?

    @Query("SELECT * FROM read_later WHERE fetchStatus = :status AND isDeleted = 0")
    suspend fun getByStatus(status: String): List<ReadLaterEntity>

    @Insert
    suspend fun insert(item: ReadLaterEntity): Long

    @Query("UPDATE read_later SET fetchStatus = :status, title = :title, contentHtml = :contentHtml, sourceDomain = :sourceDomain, excerpt = :excerpt WHERE id = :id AND isDeleted = 0")
    suspend fun updateContent(id: Long, status: String, title: String, contentHtml: String, sourceDomain: String? = null, excerpt: String? = null)

    @Query("UPDATE read_later SET isRead = 1, modifiedAt = :now WHERE id = :id AND isDeleted = 0")
    suspend fun markRead(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE read_later SET isDeleted = 1, deletedAt = :now, modifiedAt = :now WHERE id = :id")
    suspend fun deleteById(id: Long, now: Long = System.currentTimeMillis())

    // Keep deletedAt as the delete-state timestamp even when restoring;
    // SyncRepository sends it as isDeletedAt so undeletes can win cross-device.
    @Query("UPDATE read_later SET isDeleted = 0, deletedAt = :now, isArchived = 0, archivedAt = NULL, isRead = 0, savedAt = :now, modifiedAt = :now WHERE url = :url")
    suspend fun restoreByUrl(url: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE read_later SET fetchStatus = 'PENDING' WHERE fetchStatus = 'FAILED' AND isDeleted = 0")
    suspend fun resetFailed(): Int

    @Query("UPDATE read_later SET isArchived = 1, archivedAt = :now, modifiedAt = :now WHERE id = :id AND isDeleted = 0")
    suspend fun archiveById(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE read_later SET isArchived = 0, archivedAt = NULL, modifiedAt = :now WHERE id = :id AND isDeleted = 0")
    suspend fun unarchiveById(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM read_later WHERE isArchived = 0 AND isRead = 0 AND isDeleted = 0 ORDER BY savedAt DESC")
    fun getUnread(): Flow<List<ReadLaterEntity>>

    @Query("SELECT * FROM read_later WHERE isArchived = 1 AND isDeleted = 0 ORDER BY archivedAt DESC")
    fun getArchived(): Flow<List<ReadLaterEntity>>

    @Query("SELECT * FROM read_later WHERE modifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<ReadLaterEntity>

    @Query("SELECT * FROM read_later WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): ReadLaterEntity?

    @Query("UPDATE read_later SET isRead = :isRead, isArchived = :isArchived, archivedAt = :archivedAt, isDeleted = :isDeleted, deletedAt = :deletedAt, savedAt = :savedAt, modifiedAt = :modifiedAt WHERE url = :url")
    suspend fun updateStateByUrl(
        url: String,
        isRead: Boolean,
        isArchived: Boolean,
        archivedAt: Long?,
        isDeleted: Boolean,
        deletedAt: Long?,
        savedAt: Long,
        modifiedAt: Long,
    )
}

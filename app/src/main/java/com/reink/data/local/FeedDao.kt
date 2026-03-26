package com.reink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feeds WHERE isDeleted = 0 ORDER BY title ASC")
    fun getAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE isDeleted = 0 AND url NOT LIKE 'email://%' ORDER BY title ASC")
    fun getRssFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getById(id: Long): FeedEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: FeedEntity): Long

    @Query("SELECT * FROM feeds WHERE isDeleted = 0 ORDER BY title ASC")
    suspend fun getAllOnce(): List<FeedEntity>

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE feeds SET isDeleted = 1, modifiedAt = :now WHERE id = :id")
    suspend fun softDeleteById(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE feeds SET authToken = :authToken, substackSubdomain = :subdomain WHERE id = :id")
    suspend fun updateAuth(id: Long, authToken: String?, subdomain: String?)

    @Query("UPDATE feeds SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateImageUrl(id: Long, imageUrl: String?)

    @Query("SELECT substackSubdomain FROM feeds WHERE substackSubdomain IS NOT NULL AND isDeleted = 0")
    suspend fun getAllSubdomains(): List<String>

    @Query("UPDATE feeds SET enabledSectionSlugs = :enabledSectionSlugs, modifiedAt = :now WHERE id = :id")
    suspend fun updateSections(id: Long, enabledSectionSlugs: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE feeds SET emailSenderPattern = :pattern, modifiedAt = :now WHERE id = :id")
    suspend fun updateEmailSenderPattern(id: Long, pattern: String?, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM feeds WHERE emailSenderPattern IS NOT NULL AND isDeleted = 0")
    suspend fun getFeedsWithEmailPatterns(): List<FeedEntity>

    @Query("SELECT * FROM feeds WHERE modifiedAt > :since")
    suspend fun getModifiedSince(since: Long): List<FeedEntity>

    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): FeedEntity?

    @Query("UPDATE feeds SET title = :title, siteUrl = :siteUrl, requiresAuth = :requiresAuth, enabledSectionSlugs = :sections, emailSenderPattern = :emailPattern, isDeleted = :isDeleted, modifiedAt = :modifiedAt WHERE url = :url")
    suspend fun updateByUrl(url: String, title: String, siteUrl: String, requiresAuth: Boolean, sections: String?, emailPattern: String?, isDeleted: Boolean, modifiedAt: Long)
}

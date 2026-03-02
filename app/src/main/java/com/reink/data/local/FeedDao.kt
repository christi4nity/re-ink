package com.reink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun getAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE url NOT LIKE 'email://%' ORDER BY title ASC")
    fun getRssFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getById(id: Long): FeedEntity?

    @Insert
    suspend fun insert(feed: FeedEntity): Long

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    suspend fun getAllOnce(): List<FeedEntity>

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE feeds SET authToken = :authToken, substackSubdomain = :subdomain WHERE id = :id")
    suspend fun updateAuth(id: Long, authToken: String?, subdomain: String?)

    @Query("UPDATE feeds SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateImageUrl(id: Long, imageUrl: String?)

    @Query("SELECT substackSubdomain FROM feeds WHERE substackSubdomain IS NOT NULL")
    suspend fun getAllSubdomains(): List<String>

    @Query("UPDATE feeds SET enabledSectionSlugs = :enabledSectionSlugs WHERE id = :id")
    suspend fun updateSections(id: Long, enabledSectionSlugs: String?)

    @Query("UPDATE feeds SET emailSenderPattern = :pattern WHERE id = :id")
    suspend fun updateEmailSenderPattern(id: Long, pattern: String?)

    @Query("SELECT * FROM feeds WHERE emailSenderPattern IS NOT NULL")
    suspend fun getFeedsWithEmailPatterns(): List<FeedEntity>
}

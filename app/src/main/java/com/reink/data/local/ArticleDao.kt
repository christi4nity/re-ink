package com.reink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query(
        """SELECT * FROM articles
           WHERE isArchived = 0
           ORDER BY publishedAt DESC"""
    )
    fun getAll(): Flow<List<ArticleEntity>>

    @Query(
        """SELECT a.* FROM articles a
           INNER JOIN feeds f ON a.feedId = f.id
           WHERE f.url NOT LIKE 'email://%' AND a.isArchived = 0
           ORDER BY a.publishedAt DESC"""
    )
    fun getRssFeedArticles(): Flow<List<ArticleEntity>>

    @Query(
        """SELECT * FROM articles
           WHERE feedId = :feedId AND isArchived = 0
           ORDER BY publishedAt DESC"""
    )
    fun getByFeed(feedId: Long): Flow<List<ArticleEntity>>

    @Query(
        """SELECT a.* FROM articles a
           INNER JOIN feeds f ON a.feedId = f.id
           WHERE f.url NOT LIKE 'email://%' AND a.isRead = 0 AND a.isArchived = 0
           ORDER BY a.publishedAt DESC"""
    )
    fun getRssFeedUnread(): Flow<List<ArticleEntity>>

    @Query(
        """SELECT * FROM articles
           WHERE isRead = 0 AND isArchived = 0
           ORDER BY publishedAt DESC"""
    )
    fun getUnread(): Flow<List<ArticleEntity>>

    @Query(
        """SELECT * FROM articles
           WHERE feedId = :feedId AND isRead = 0 AND isArchived = 0
           ORDER BY publishedAt DESC"""
    )
    fun getUnreadByFeed(feedId: Long): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: Long): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllNew(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId AND publishedAt < :cutoff")
    suspend fun markReadBefore(feedId: Long, cutoff: Long)

    @Query("UPDATE articles SET isRead = 1 WHERE publishedAt < :cutoff")
    suspend fun markAllReadBefore(cutoff: Long)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: Long)

    @Query("DELETE FROM articles WHERE publishedAt < :cutoff AND isArchived = 0")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId")
    suspend fun countByFeed(feedId: Long): Int

    @Query("UPDATE articles SET contentHtml = :html, contentStatus = :status WHERE id = :id")
    suspend fun updateExtractedContent(id: Long, html: String, status: String)

    @Query("UPDATE articles SET contentStatus = 'truncated' WHERE feedId = :feedId AND contentStatus = 'full'")
    suspend fun markFeedArticlesTruncated(feedId: Long)

    @Query("UPDATE articles SET contentHtml = :html, contentStatus = :status, emailMessageId = :messageId WHERE url = :url")
    suspend fun updateContentByUrl(url: String, html: String, status: String, messageId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM articles WHERE emailMessageId = :messageId)")
    suspend fun existsByEmailMessageId(messageId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM articles WHERE url = :url)")
    suspend fun existsByUrl(url: String): Boolean

    @Query("UPDATE articles SET isArchived = 1, archivedAt = :now WHERE id = :id")
    suspend fun archiveById(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE articles SET isArchived = 0, archivedAt = NULL WHERE id = :id")
    suspend fun unarchiveById(id: Long)

    @Query("SELECT * FROM articles WHERE isArchived = 1 ORDER BY archivedAt DESC")
    fun getArchived(): Flow<List<ArticleEntity>>
}

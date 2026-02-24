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
           ORDER BY publishedAt DESC
           LIMIT :limit OFFSET :offset"""
    )
    fun getAll(limit: Int, offset: Int): Flow<List<ArticleEntity>>

    @Query(
        """SELECT * FROM articles
           WHERE feedId = :feedId
           ORDER BY publishedAt DESC
           LIMIT :limit OFFSET :offset"""
    )
    fun getByFeed(feedId: Long, limit: Int, offset: Int): Flow<List<ArticleEntity>>

    @Query(
        """SELECT * FROM articles
           WHERE isRead = 0
           ORDER BY publishedAt DESC
           LIMIT :limit OFFSET :offset"""
    )
    fun getUnread(limit: Int, offset: Int): Flow<List<ArticleEntity>>

    @Query(
        """SELECT * FROM articles
           WHERE feedId = :feedId AND isRead = 0
           ORDER BY publishedAt DESC
           LIMIT :limit OFFSET :offset"""
    )
    fun getUnreadByFeed(feedId: Long, limit: Int, offset: Int): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: Long): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllNew(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: Long)

    @Query("DELETE FROM articles WHERE publishedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId")
    suspend fun countByFeed(feedId: Long): Int
}

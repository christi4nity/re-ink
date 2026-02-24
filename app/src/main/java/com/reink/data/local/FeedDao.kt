package com.reink.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun getAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getById(id: Long): FeedEntity?

    @Insert
    suspend fun insert(feed: FeedEntity): Long

    @Query("SELECT * FROM feeds ORDER BY title ASC")
    suspend fun getAllOnce(): List<FeedEntity>

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteById(id: Long)
}

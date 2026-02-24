package com.reink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FeedEntity::class, ArticleEntity::class, ReadLaterEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ReInkDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun readLaterDao(): ReadLaterDao
}

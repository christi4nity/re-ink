package com.reink.data.repository

import com.reink.data.local.ArticleDao
import com.reink.data.local.FeedDao
import com.reink.data.local.FeedEntity
import com.reink.data.model.Feed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
) {
    fun observeAll(): Flow<List<Feed>> =
        feedDao.getAll().map { entities -> entities.map { it.toModel() } }

    suspend fun getById(id: Long): Feed? =
        feedDao.getById(id)?.toModel()

    suspend fun add(feed: Feed): Long =
        feedDao.insert(FeedEntity.fromModel(feed))

    suspend fun delete(id: Long) {
        articleDao.deleteByFeed(id)
        feedDao.deleteById(id)
    }
}

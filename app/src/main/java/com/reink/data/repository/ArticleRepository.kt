package com.reink.data.repository

import com.reink.data.local.ArticleDao
import com.reink.data.local.ArticleEntity
import com.reink.data.local.FeedDao
import com.reink.data.model.Article
import com.reink.data.remote.RssFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleRepository @Inject constructor(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssFetcher: RssFetcher,
) {
    companion object {
        const val PAGE_SIZE = 20
    }

    fun observe(
        feedId: Long?,
        unreadOnly: Boolean,
        page: Int,
    ): Flow<List<Article>> {
        val limit = PAGE_SIZE + 1
        val offset = page * PAGE_SIZE
        val flow = when {
            feedId != null && unreadOnly -> articleDao.getUnreadByFeed(feedId, limit, offset)
            feedId != null -> articleDao.getByFeed(feedId, limit, offset)
            unreadOnly -> articleDao.getUnread(limit, offset)
            else -> articleDao.getAll(limit, offset)
        }
        return flow.map { entities -> entities.map { it.toModel() } }
    }

    suspend fun getById(id: Long): Article? =
        articleDao.getById(id)?.toModel()

    suspend fun markRead(id: Long) =
        articleDao.markRead(id)

    suspend fun syncFeed(feedId: Long): Result<Int> {
        val feed = feedDao.getById(feedId)?.toModel()
            ?: return Result.failure(IllegalArgumentException("Feed not found: $feedId"))

        return rssFetcher.fetchFeed(feed.url, feedId).map { articles ->
            val entities = articles.map { ArticleEntity.fromModel(it) }
            articleDao.insertAllNew(entities)
            entities.size
        }
    }

    suspend fun syncAllFeeds(): Result<Int> {
        val allFeeds = feedDao.getAllOnce()
        var totalNew = 0
        var lastError: Throwable? = null

        for (feed in allFeeds) {
            rssFetcher.fetchFeed(feed.url, feed.id).fold(
                onSuccess = { articles ->
                    val entities = articles.map { ArticleEntity.fromModel(it) }
                    articleDao.insertAllNew(entities)
                    totalNew += entities.size
                },
                onFailure = { lastError = it },
            )
        }

        return if (lastError != null && totalNew == 0) {
            Result.failure(lastError!!)
        } else {
            Result.success(totalNew)
        }
    }

    suspend fun deleteOlderThan(cutoffMillis: Long) =
        articleDao.deleteOlderThan(cutoffMillis)
}

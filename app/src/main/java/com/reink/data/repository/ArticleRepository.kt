package com.reink.data.repository

import com.reink.data.local.ArticleDao
import com.reink.data.local.ArticleEntity
import com.reink.data.local.FeedDao
import com.reink.data.model.Article
import com.reink.data.model.Feed
import com.reink.data.remote.FetchResult
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
    fun observe(
        feedId: Long?,
        unreadOnly: Boolean,
    ): Flow<List<Article>> {
        val flow = when {
            feedId != null && unreadOnly -> articleDao.getUnreadByFeed(feedId)
            feedId != null -> articleDao.getByFeed(feedId)
            unreadOnly -> articleDao.getUnread()
            else -> articleDao.getAll()
        }
        return flow.map { entities -> entities.map { it.toModel() } }
    }

    suspend fun getById(id: Long): Article? =
        articleDao.getById(id)?.toModel()

    suspend fun markRead(id: Long) =
        articleDao.markRead(id)

    suspend fun markAllReadBefore(cutoff: Long) =
        articleDao.markAllReadBefore(cutoff)

    suspend fun syncFeed(feedId: Long): Result<Int> {
        val feed = feedDao.getById(feedId)?.toModel()
            ?: return Result.failure(IllegalArgumentException("Feed not found: $feedId"))

        val sectionUrls = feed.authenticatedSectionUrls()

        return if (sectionUrls.isNotEmpty()) {
            fetchSectionFeeds(feed, sectionUrls)
        } else {
            rssFetcher.fetchFeed(feed.authenticatedFeedUrl, feedId).map { result ->
                processResult(feed, result)
            }
        }
    }

    private suspend fun fetchSectionFeeds(feed: Feed, sectionUrls: List<String>): Result<Int> {
        val allArticles = mutableListOf<Article>()
        var imageUrl: String? = null
        var lastError: Throwable? = null

        for (url in sectionUrls) {
            rssFetcher.fetchFeed(url, feed.id).fold(
                onSuccess = { result ->
                    allArticles.addAll(result.articles)
                    if (imageUrl == null) imageUrl = result.imageUrl
                },
                onFailure = { lastError = it },
            )
        }

        if (allArticles.isEmpty() && lastError != null) {
            return Result.failure(lastError!!)
        }

        val entities = allArticles.map { ArticleEntity.fromModel(it) }
        articleDao.insertAllNew(entities)

        if (imageUrl != null) {
            feedDao.updateImageUrl(feed.id, imageUrl)
        }

        articleDao.markReadBefore(feed.id, feed.addedAt)

        return Result.success(entities.size)
    }

    private suspend fun processResult(feed: Feed, result: FetchResult): Int {
        val entities = result.articles.map { ArticleEntity.fromModel(it) }
        articleDao.insertAllNew(entities)

        if (result.imageUrl != null) {
            feedDao.updateImageUrl(feed.id, result.imageUrl)
        }

        articleDao.markReadBefore(feed.id, feed.addedAt)

        return entities.size
    }

    suspend fun syncAllFeeds(): Result<Int> {
        val allFeeds = feedDao.getAllOnce()
        var totalNew = 0
        var lastError: Throwable? = null

        for (feedEntity in allFeeds) {
            val feed = feedEntity.toModel()
            val sectionUrls = feed.authenticatedSectionUrls()

            if (sectionUrls.isNotEmpty()) {
                fetchSectionFeeds(feed, sectionUrls).fold(
                    onSuccess = { totalNew += it },
                    onFailure = { lastError = it },
                )
            } else {
                rssFetcher.fetchFeed(feed.authenticatedFeedUrl, feed.id).fold(
                    onSuccess = { result ->
                        totalNew += processResult(feed, result)
                    },
                    onFailure = { lastError = it },
                )
            }
        }

        return if (lastError != null && totalNew == 0) {
            Result.failure(lastError!!)
        } else {
            Result.success(totalNew)
        }
    }

    suspend fun updateExtractedContent(id: Long, html: String, status: String) =
        articleDao.updateExtractedContent(id, html, status)

    suspend fun markFeedArticlesTruncated(feedId: Long) =
        articleDao.markFeedArticlesTruncated(feedId)

    suspend fun deleteOlderThan(cutoffMillis: Long) =
        articleDao.deleteOlderThan(cutoffMillis)
}

package com.reink.data.repository

import com.reink.data.local.ReadLaterDao
import com.reink.data.local.ReadLaterEntity
import com.reink.data.model.FetchStatus
import com.reink.data.model.ReadLaterItem
import android.util.Log
import com.reink.data.remote.ArticleExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadLaterRepository @Inject constructor(
    private val readLaterDao: ReadLaterDao,
    private val articleExtractor: ArticleExtractor,
    private val webViewArticleExtractor: com.reink.data.remote.WebViewArticleExtractor,
) {
    fun observeAll(): Flow<List<ReadLaterItem>> =
        readLaterDao.getAll().map { entities -> entities.map { it.toModel() } }

    suspend fun getById(id: Long): ReadLaterItem? =
        readLaterDao.getById(id)?.toModel()

    suspend fun save(url: String, sourceArticleId: Long? = null): Long {
        val alreadyExists = readLaterDao.countByUrl(url) > 0
        if (alreadyExists) return -1

        val item = ReadLaterEntity(
            url = url,
            title = "",
            contentHtml = "",
            sourceArticleId = sourceArticleId,
            savedAt = System.currentTimeMillis(),
            fetchStatus = FetchStatus.PENDING.name,
            isRead = false,
        )
        return readLaterDao.insert(item)
    }

    suspend fun markRead(id: Long) =
        readLaterDao.markRead(id)

    suspend fun delete(id: Long) =
        readLaterDao.deleteById(id)

    suspend fun fetchPendingContent(): Int {
        readLaterDao.resetFailed()
        val pending = readLaterDao.getByStatus(FetchStatus.PENDING.name)
        var fetched = 0

        for (entity in pending) {
            readLaterDao.updateContent(
                id = entity.id,
                status = FetchStatus.FETCHING.name,
                title = entity.title,
                contentHtml = entity.contentHtml,
                sourceDomain = entity.sourceDomain,
            )

            // Try OkHttp + Readability4J first (fast), fall back to WebView + Readability.js
            val result = articleExtractor.extract(entity.url)
                .recoverCatching { firstError ->
                    Log.d("ReadLater", "OkHttp extraction failed, trying WebView: ${firstError.message}")
                    webViewArticleExtractor.extract(entity.url).getOrThrow()
                }

            result.fold(
                onSuccess = { extracted ->
                    readLaterDao.updateContent(
                        id = entity.id,
                        status = FetchStatus.FETCHED.name,
                        title = extracted.title,
                        contentHtml = extracted.contentHtml,
                        sourceDomain = extracted.sourceDomain,
                        excerpt = extracted.excerpt,
                    )
                    fetched++
                },
                onFailure = { error ->
                    Log.e("ReadLater", "All extraction methods failed: ${entity.url}", error)
                    readLaterDao.updateContent(
                        id = entity.id,
                        status = FetchStatus.FAILED.name,
                        title = entity.title,
                        contentHtml = entity.contentHtml,
                        sourceDomain = entity.sourceDomain,
                    )
                },
            )
        }

        return fetched
    }
}

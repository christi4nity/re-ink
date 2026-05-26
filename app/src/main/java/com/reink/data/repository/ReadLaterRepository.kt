package com.reink.data.repository

import com.reink.data.local.ReadLaterDao
import com.reink.data.local.ReadLaterEntity
import com.reink.data.model.FetchStatus
import com.reink.data.model.ReadLaterItem
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
        val existing = readLaterDao.getByUrl(url)
        if (existing != null) {
            if (existing.isDeleted) {
                readLaterDao.restoreByUrl(url)
                return existing.id
            }
            return -1
        }

        val now = System.currentTimeMillis()
        val item = ReadLaterEntity(
            url = url,
            title = "",
            contentHtml = "",
            sourceArticleId = sourceArticleId,
            savedAt = now,
            fetchStatus = FetchStatus.PENDING.name,
            isRead = false,
            modifiedAt = now,
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
                .recoverCatching {
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
                onFailure = {
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

    suspend fun archive(id: Long) =
        readLaterDao.archiveById(id)

    suspend fun unarchive(id: Long) =
        readLaterDao.unarchiveById(id)

    fun observeUnread(): Flow<List<ReadLaterItem>> =
        readLaterDao.getUnread().map { entities -> entities.map { it.toModel() } }

    fun observeArchived(): Flow<List<ReadLaterItem>> =
        readLaterDao.getArchived().map { entities -> entities.map { it.toModel() } }
}

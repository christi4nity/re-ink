package com.reink.data.repository

import com.reink.data.local.ArticleDao
import com.reink.data.local.FeedDao
import com.reink.data.local.FeedEntity
import com.reink.data.local.ReadLaterDao
import com.reink.data.local.ReadLaterEntity
import com.reink.data.model.FetchStatus
import com.reink.data.remote.ArticleStateSyncDto
import com.reink.data.remote.FeedSyncDto
import com.reink.data.remote.PreferencesSyncDto
import com.reink.data.remote.ReadLaterStateSyncDto
import com.reink.data.remote.SyncClient
import com.reink.data.remote.SyncRequest
import com.reink.data.remote.SyncResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ReadingPrefsJson(
    val fontFamily: String,
    val fontSize: Int,
    val lineHeight: Float,
    val marginHorizontal: Int,
    val marginVertical: Int,
    val textAlign: String,
    val paginationMode: String,
)

@Singleton
class SyncRepository @Inject constructor(
    private val syncClient: SyncClient,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val readLaterDao: ReadLaterDao,
    private val preferencesRepository: PreferencesRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sync(): Result<SyncResponse> {
        val config = preferencesRepository.getSyncConfig()
        if (!config.isConfigured) return Result.failure(IllegalStateException("Sync not configured"))

        val lastSyncedAt = preferencesRepository.getSyncLastSyncedAt()

        // Gather local changes
        val localFeeds = feedDao.getModifiedSince(lastSyncedAt).map { it.toFeedSyncDto() }
        val localArticles = articleDao.getModifiedSince(lastSyncedAt).map { it.toArticleSyncDto() }
        val localReadLater = readLaterDao.getModifiedSince(lastSyncedAt).map { it.toReadLaterSyncDto() }

        val prefs = preferencesRepository.getReadingPreferences()
        val prefsModifiedAt = preferencesRepository.getPreferencesModifiedAt()
        val prefsDto = if (prefsModifiedAt > lastSyncedAt) {
            val prefsJson = json.encodeToString(
                ReadingPrefsJson.serializer(),
                ReadingPrefsJson(
                    fontFamily = prefs.fontFamily,
                    fontSize = prefs.fontSize,
                    lineHeight = prefs.lineHeight,
                    marginHorizontal = prefs.marginHorizontal,
                    marginVertical = prefs.marginVertical,
                    textAlign = prefs.textAlign,
                    paginationMode = prefs.paginationMode,
                ),
            )
            PreferencesSyncDto(data = prefsJson, modifiedAt = prefsModifiedAt)
        } else {
            null
        }

        val request = SyncRequest(
            deviceId = config.deviceId,
            lastSyncedAt = lastSyncedAt,
            feeds = localFeeds,
            articles = localArticles,
            readLater = localReadLater,
            preferences = prefsDto,
        )

        return syncClient.sync(config.serverUrl, config.apiKey, request).map { response ->
            applyIncomingChanges(response)
            preferencesRepository.setSyncLastSyncedAt(response.syncedAt)
            response
        }
    }

    private suspend fun applyIncomingChanges(response: SyncResponse) {
        applyFeedChanges(response.feeds)
        applyArticleChanges(response.articles)
        applyReadLaterChanges(response.readLater)
        applyPreferencesChanges(response.preferences)
    }

    private suspend fun applyFeedChanges(feeds: List<FeedSyncDto>) {
        for (feed in feeds) {
            val existing = feedDao.getByUrl(feed.url)
            if (existing != null) {
                if (feed.modifiedAt > existing.modifiedAt) {
                    feedDao.updateByUrl(
                        url = feed.url,
                        title = feed.title,
                        siteUrl = feed.siteUrl,
                        requiresAuth = feed.requiresAuth,
                        sections = feed.enabledSectionSlugs.ifEmpty { null },
                        emailPattern = feed.emailSenderPattern.ifEmpty { null },
                        isDeleted = feed.isDeleted,
                        modifiedAt = feed.modifiedAt,
                    )
                    if (feed.isDeleted) {
                        articleDao.deleteByFeed(existing.id)
                    }
                }
            } else if (!feed.isDeleted) {
                feedDao.insert(
                    FeedEntity(
                        title = feed.title,
                        url = feed.url,
                        siteUrl = feed.siteUrl,
                        requiresAuth = feed.requiresAuth,
                        addedAt = System.currentTimeMillis(),
                        enabledSectionSlugs = feed.enabledSectionSlugs.ifEmpty { null },
                        emailSenderPattern = feed.emailSenderPattern.ifEmpty { null },
                        modifiedAt = feed.modifiedAt,
                    ),
                )
            }
        }
    }

    private suspend fun applyArticleChanges(articles: List<ArticleStateSyncDto>) {
        for (article in articles) {
            val existing = articleDao.getByUrl(article.url) ?: continue
            if (article.modifiedAt > existing.modifiedAt) {
                val existingDeleteStateAt = existing.deletedAt
                    ?: if (existing.isDeleted) existing.modifiedAt else 0L
                val incomingDeleteIsNewer = article.isDeletedAt > existingDeleteStateAt
                articleDao.updateStateByUrl(
                    url = article.url,
                    isRead = article.isRead,
                    isArchived = article.isArchived,
                    archivedAt = article.archivedAt,
                    isDeleted = if (incomingDeleteIsNewer) article.isDeleted else existing.isDeleted,
                    deletedAt = if (incomingDeleteIsNewer) article.deletedAt else existing.deletedAt,
                    modifiedAt = article.modifiedAt,
                )
            }
        }
    }

    private suspend fun applyReadLaterChanges(items: List<ReadLaterStateSyncDto>) {
        for (item in items) {
            val existing = readLaterDao.getByUrl(item.url)
            if (existing != null) {
                if (item.modifiedAt > existing.modifiedAt) {
                    val existingDeleteStateAt = existing.deletedAt
                        ?: if (existing.isDeleted) existing.modifiedAt else 0L
                    val incomingDeleteIsNewer = item.isDeletedAt > existingDeleteStateAt
                    readLaterDao.updateStateByUrl(
                        url = item.url,
                        isRead = item.isRead,
                        isArchived = item.isArchived,
                        archivedAt = item.archivedAt,
                        isDeleted = if (incomingDeleteIsNewer) item.isDeleted else existing.isDeleted,
                        deletedAt = if (incomingDeleteIsNewer) item.deletedAt else existing.deletedAt,
                        savedAt = item.savedAt,
                        modifiedAt = item.modifiedAt,
                    )
                }
            } else {
                readLaterDao.insert(
                    ReadLaterEntity(
                        url = item.url,
                        title = "",
                        contentHtml = "",
                        sourceArticleId = null,
                        savedAt = item.savedAt,
                        fetchStatus = FetchStatus.PENDING.name,
                        isRead = item.isRead,
                        isArchived = item.isArchived,
                        archivedAt = item.archivedAt,
                        isDeleted = item.isDeleted,
                        deletedAt = item.deletedAt,
                        modifiedAt = item.modifiedAt,
                    ),
                )
            }
        }
    }

    private suspend fun applyPreferencesChanges(prefs: PreferencesSyncDto?) {
        if (prefs == null) return
        val localModifiedAt = preferencesRepository.getPreferencesModifiedAt()
        if (prefs.modifiedAt > localModifiedAt) {
            val parsed = json.decodeFromString<ReadingPrefsJson>(prefs.data)
            preferencesRepository.updateReadingPreferences(
                com.reink.data.model.ReadingPreferences(
                    fontFamily = parsed.fontFamily,
                    fontSize = parsed.fontSize,
                    lineHeight = parsed.lineHeight,
                    marginHorizontal = parsed.marginHorizontal,
                    marginVertical = parsed.marginVertical,
                    textAlign = parsed.textAlign,
                    paginationMode = parsed.paginationMode,
                ),
            )
            preferencesRepository.setPreferencesModifiedAt(prefs.modifiedAt)
        }
    }

    private fun FeedEntity.toFeedSyncDto() = FeedSyncDto(
        url = url,
        title = title,
        siteUrl = siteUrl,
        requiresAuth = requiresAuth,
        enabledSectionSlugs = enabledSectionSlugs ?: "",
        emailSenderPattern = emailSenderPattern ?: "",
        isDeleted = isDeleted,
        modifiedAt = modifiedAt,
    )

    // deletedAt is also used as the delete-state timestamp when isDeleted=false
    // after an explicit restore, allowing undeletes to win during sync merge.
    private fun com.reink.data.local.ArticleEntity.toArticleSyncDto() = ArticleStateSyncDto(
        url = url,
        isRead = isRead,
        isReadAt = modifiedAt,
        isArchived = isArchived,
        isArchivedAt = modifiedAt,
        archivedAt = archivedAt,
        isDeleted = isDeleted,
        isDeletedAt = if (isDeleted || deletedAt != null) deletedAt ?: modifiedAt else 0L,
        deletedAt = deletedAt,
        modifiedAt = modifiedAt,
    )

    private fun ReadLaterEntity.toReadLaterSyncDto() = ReadLaterStateSyncDto(
        url = url,
        isRead = isRead,
        isReadAt = modifiedAt,
        isArchived = isArchived,
        isArchivedAt = modifiedAt,
        archivedAt = archivedAt,
        isDeleted = isDeleted,
        isDeletedAt = if (isDeleted || deletedAt != null) deletedAt ?: modifiedAt else 0L,
        deletedAt = deletedAt,
        savedAt = savedAt,
        modifiedAt = modifiedAt,
    )
}

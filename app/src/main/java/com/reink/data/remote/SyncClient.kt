package com.reink.data.remote

import com.reink.di.PlainHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SyncRequest(
    val deviceId: String,
    val lastSyncedAt: Long,
    val feeds: List<FeedSyncDto> = emptyList(),
    val articles: List<ArticleStateSyncDto> = emptyList(),
    val readLater: List<ReadLaterStateSyncDto> = emptyList(),
    val preferences: PreferencesSyncDto? = null,
)

@Serializable
data class SyncResponse(
    val syncedAt: Long,
    val feeds: List<FeedSyncDto> = emptyList(),
    val articles: List<ArticleStateSyncDto> = emptyList(),
    val readLater: List<ReadLaterStateSyncDto> = emptyList(),
    val preferences: PreferencesSyncDto? = null,
)

@Serializable
data class FeedSyncDto(
    val url: String,
    val title: String = "",
    val siteUrl: String = "",
    val requiresAuth: Boolean = false,
    val enabledSectionSlugs: String = "",
    val emailSenderPattern: String = "",
    val isDeleted: Boolean = false,
    val modifiedAt: Long = 0,
)

@Serializable
data class ArticleStateSyncDto(
    val url: String,
    val isRead: Boolean = false,
    val isReadAt: Long = 0,
    val isArchived: Boolean = false,
    val isArchivedAt: Long = 0,
    val archivedAt: Long? = null,
    val modifiedAt: Long = 0,
)

@Serializable
data class ReadLaterStateSyncDto(
    val url: String,
    val isRead: Boolean = false,
    val isReadAt: Long = 0,
    val isArchived: Boolean = false,
    val isArchivedAt: Long = 0,
    val archivedAt: Long? = null,
    val savedAt: Long = 0,
    val modifiedAt: Long = 0,
)

@Serializable
data class PreferencesSyncDto(
    val data: String,
    val modifiedAt: Long,
)

@Singleton
class SyncClient @Inject constructor(
    @PlainHttpClient private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun sync(
        serverUrl: String,
        apiKey: String,
        request: SyncRequest,
    ): Result<SyncResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(SyncRequest.serializer(), request)
            val httpRequest = Request.Builder()
                .url("$serverUrl/sync")
                .header("X-API-Key", apiKey)
                .post(body.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                check(response.isSuccessful) { "Sync failed: ${response.code}" }
                val responseBody = response.body?.string() ?: error("Empty response")
                json.decodeFromString<SyncResponse>(responseBody)
            }
        }
    }

    suspend fun healthCheck(serverUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$serverUrl/health")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Health check failed: ${response.code}" }
            }
        }
    }
}

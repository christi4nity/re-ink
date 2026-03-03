package com.reink.data.remote

import com.reink.di.PlainHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CloudQueueItem(
    val id: String,
    val url: String,
    val addedAt: String,
)

@Serializable
private data class CreateQueueResponse(val id: String)

@Serializable
private data class ListItemsResponse(val items: List<CloudQueueItem>)

@Serializable
private data class AckResponse(val acknowledged: Int)

@Serializable
private data class AckRequest(val ids: List<String>)

@Singleton
class CloudQueueClient @Inject constructor(
    @PlainHttpClient private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    companion object {
        const val DEFAULT_BASE_URL = "https://reink-relay.cv-b61.workers.dev"
    }

    suspend fun createQueue(baseUrl: String = DEFAULT_BASE_URL): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/q")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Create queue failed: ${response.code}" }
                    val body = response.body?.string() ?: error("Empty response")
                    json.decodeFromString<CreateQueueResponse>(body).id
                }
            }
        }

    suspend fun fetchItems(
        baseUrl: String,
        queueId: String,
    ): Result<List<CloudQueueItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/q/$queueId/items")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Fetch items failed: ${response.code}" }
                    val body = response.body?.string() ?: error("Empty response")
                    json.decodeFromString<ListItemsResponse>(body).items
                }
            }
        }

    suspend fun acknowledge(
        baseUrl: String,
        queueId: String,
        ids: List<String>,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestBody = json.encodeToString(AckRequest.serializer(), AckRequest(ids))
                val request = Request.Builder()
                    .url("$baseUrl/q/$queueId/ack")
                    .post(requestBody.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Ack failed: ${response.code}" }
                }
            }
        }
}

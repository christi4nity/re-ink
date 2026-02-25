package com.reink.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SubscriptionsResponse(
    val subscriptions: List<SubstackSubscription> = emptyList(),
    val publications: List<SubstackPublication> = emptyList(),
)

@Serializable
data class SubstackSubscription(
    val id: Long,
    @SerialName("publication_id") val publicationId: Long = 0,
    @SerialName("podcast_rss_token") val podcastRssToken: String? = null,
    @SerialName("email_settings") val emailSettings: Map<String, String>? = null,
)

@Serializable
data class SubstackSection(
    val id: Long,
    val name: String,
    val slug: String,
)

@Serializable
data class SubstackPublication(
    val id: Long = 0,
    val subdomain: String,
    @SerialName("custom_domain") val customDomain: String? = null,
    val name: String? = null,
    val sections: List<SubstackSection> = emptyList(),
)

data class ResolvedSubscription(
    val token: String,
    val publication: SubstackPublication,
    val emailSettings: Map<String, String>? = null,
)

data class TokenMatch(
    val subdomain: String,
    val token: String,
)

@Singleton
class SubstackApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetch subscriptions with publication details in a single API call.
     * Uses for_homepage=true which returns both subscriptions and publications arrays.
     */
    suspend fun fetchSubscriptions(sid: String): Result<List<ResolvedSubscription>> = runCatching {
        withContext(Dispatchers.IO) {
            val allSubscriptions = mutableListOf<SubstackSubscription>()
            val allPublications = mutableListOf<SubstackPublication>()
            var offset = 0
            val limit = 100

            while (true) {
                val request = Request.Builder()
                    .url(
                        "https://substack.com/api/v1/subscriptions/page" +
                            "?for_homepage=true&offset=$offset&limit=$limit",
                    )
                    .header("Cookie", "substack.sid=$sid")
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw RuntimeException("Subscriptions API returned ${response.code}")
                }

                val body = response.body?.string()
                    ?: throw RuntimeException("Empty response body")

                val page = json.decodeFromString<SubscriptionsResponse>(body)
                allSubscriptions.addAll(page.subscriptions)
                allPublications.addAll(page.publications)

                if (page.subscriptions.size < limit) break
                offset += limit
            }

            // Join subscriptions with their publications
            val pubById = allPublications.associateBy { it.id }
            allSubscriptions.mapNotNull { sub ->
                val token = sub.podcastRssToken ?: return@mapNotNull null
                val pub = pubById[sub.publicationId] ?: return@mapNotNull null
                ResolvedSubscription(
                    token = token,
                    publication = pub,
                    emailSettings = sub.emailSettings,
                )
            }
        }
    }

    /**
     * Match a feed URL to a subscription by comparing the feed's host against
     * each subscription's subdomain ({sub}.substack.com) and custom_domain.
     */
    fun findMatchForFeedUrl(
        feedUrl: String,
        subscriptions: List<ResolvedSubscription>,
    ): TokenMatch? {
        val feedHost = try {
            java.net.URI(feedUrl).host?.lowercase() ?: return null
        } catch (_: Exception) {
            return null
        }

        for (sub in subscriptions) {
            val pub = sub.publication

            if (feedHost == "${pub.subdomain}.substack.com") {
                return TokenMatch(subdomain = pub.subdomain, token = sub.token)
            }

            val customDomain = pub.customDomain?.lowercase()
            if (customDomain != null && feedHost == customDomain) {
                return TokenMatch(subdomain = pub.subdomain, token = sub.token)
            }
        }
        return null
    }
}

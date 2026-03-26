package com.reink.data.repository

import com.reink.data.local.ArticleDao
import com.reink.data.local.FeedDao
import com.reink.data.local.FeedEntity
import com.reink.data.model.Feed
import com.reink.data.remote.SubstackApiClient
import com.reink.data.remote.TokenMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val substackApiClient: SubstackApiClient,
) {
    fun observeAll(): Flow<List<Feed>> =
        feedDao.getAll().map { entities -> entities.map { it.toModel() } }

    fun observeRssFeeds(): Flow<List<Feed>> =
        feedDao.getRssFeeds().map { entities -> entities.map { it.toModel() } }

    suspend fun getById(id: Long): Feed? =
        feedDao.getById(id)?.toModel()

    suspend fun add(feed: Feed): Long {
        val existing = feedDao.getByUrl(feed.url)
        if (existing != null) {
            if (existing.isDeleted) {
                val now = System.currentTimeMillis()
                feedDao.updateByUrl(
                    url = existing.url,
                    title = feed.title,
                    siteUrl = feed.siteUrl.ifBlank { existing.siteUrl },
                    requiresAuth = feed.requiresAuth,
                    sections = existing.enabledSectionSlugs,
                    emailPattern = existing.emailSenderPattern,
                    isDeleted = false,
                    modifiedAt = now,
                )
            }
            return existing.id
        }

        val now = System.currentTimeMillis()
        val id = feedDao.insert(
            FeedEntity.fromModel(feed).copy(modifiedAt = now),
        )
        return if (id != -1L) id else (feedDao.getByUrl(feed.url)?.id ?: -1L)
    }

    suspend fun delete(id: Long) {
        feedDao.softDeleteById(id)
        articleDao.deleteByFeed(id)
    }

    /**
     * Fetch Substack subscriptions, import new ones as feeds,
     * and update all feeds with their auth tokens.
     * Returns the number of imported + matched feeds.
     */
    suspend fun syncSubscriptions(sid: String): Result<SyncResult> {
        return substackApiClient.fetchSubscriptions(sid).map { subscriptions ->
            val existingSubdomains = feedDao.getAllSubdomains().toSet()
            val existingHosts = feedDao.getAllOnce()
                .mapNotNull { runCatching { java.net.URI(it.url).host?.lowercase() }.getOrNull() }
                .toSet()
            var imported = 0

            // Import subscriptions that aren't already in the DB
            for (sub in subscriptions) {
                val pub = sub.publication

                if (pub.subdomain in existingSubdomains) continue

                // Also skip if we already have a feed matching by host
                val substackHost = "${pub.subdomain}.substack.com"
                val customHost = pub.customDomain?.lowercase()
                if (substackHost in existingHosts) continue
                if (customHost != null && customHost in existingHosts) continue

                val feedUrl = "https://${pub.subdomain}.substack.com/feed"
                val siteUrl = pub.customDomain
                    ?.let { "https://$it" }
                    ?: "https://${pub.subdomain}.substack.com"
                val existingByUrl = feedDao.getByUrl(feedUrl)
                if (existingByUrl != null) {
                    if (existingByUrl.isDeleted) {
                        val now = System.currentTimeMillis()
                        feedDao.updateByUrl(
                            url = existingByUrl.url,
                            title = pub.name ?: pub.subdomain,
                            siteUrl = siteUrl,
                            requiresAuth = true,
                            sections = existingByUrl.enabledSectionSlugs,
                            emailPattern = existingByUrl.emailSenderPattern,
                            isDeleted = false,
                            modifiedAt = now,
                        )
                        feedDao.updateAuth(existingByUrl.id, sub.token, pub.subdomain)
                        imported++
                    }
                    continue
                }

                val insertedId = feedDao.insert(
                    FeedEntity(
                        title = pub.name ?: pub.subdomain,
                        url = feedUrl,
                        siteUrl = siteUrl,
                        requiresAuth = true,
                        addedAt = System.currentTimeMillis(),
                        authToken = sub.token,
                        substackSubdomain = pub.subdomain,
                        modifiedAt = System.currentTimeMillis(),
                    ),
                )
                if (insertedId != -1L) {
                    imported++
                }
            }

            // Update tokens on all existing feeds (refresh tokens that may have changed)
            val feeds = feedDao.getAllOnce()
            var matched = 0

            for (feedEntity in feeds) {
                val match = substackApiClient.findMatchForFeedUrl(
                    feedEntity.url,
                    subscriptions,
                ) ?: if (feedEntity.substackSubdomain != null) {
                    // Already matched by subdomain from a previous sync — look up by subdomain
                    subscriptions.find { it.publication.subdomain == feedEntity.substackSubdomain }
                        ?.let { sub ->
                            TokenMatch(
                                subdomain = sub.publication.subdomain,
                                token = sub.token,
                            )
                        }
                } else {
                    null
                }

                if (match != null) {
                    feedDao.updateAuth(feedEntity.id, match.token, match.subdomain)
                    matched++
                }
            }

            // Update section preferences for all feeds
            val allFeeds = feedDao.getAllOnce()
            val subBySubdomain = subscriptions.associateBy { it.publication.subdomain }

            for (feedEntity in allFeeds) {
                val subdomain = feedEntity.substackSubdomain ?: continue
                val sub = subBySubdomain[subdomain] ?: continue
                val sections = sub.publication.sections
                if (sections.isEmpty()) {
                    // Publication has no sections — clear any stale value
                    if (feedEntity.enabledSectionSlugs != null) {
                        feedDao.updateSections(feedEntity.id, null)
                    }
                    continue
                }

                val emailSettings = sub.emailSettings ?: emptyMap()
                val enabledSlugs = sections
                    .filter { section ->
                        val setting = emailSettings[section.id.toString()]
                        setting != "disabled"
                    }
                    .map { it.slug }

                // If all sections are enabled, fetch main feed (no filtering needed)
                val slugsCsv = if (enabledSlugs.size == sections.size) {
                    null
                } else {
                    enabledSlugs.joinToString(",").ifEmpty { null }
                }
                if (feedEntity.enabledSectionSlugs != slugsCsv) {
                    feedDao.updateSections(feedEntity.id, slugsCsv)
                }
            }

            SyncResult(imported = imported, matched = matched)
        }
    }
}

data class SyncResult(
    val imported: Int,
    val matched: Int,
) {
    val total: Int get() = imported + matched
}

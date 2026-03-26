package com.reink.data.repository

import com.reink.data.email.EmailArticle
import com.reink.data.email.EmailContentSource
import com.reink.data.local.ArticleDao
import com.reink.data.local.ArticleEntity
import com.reink.data.local.FeedDao
import com.reink.data.local.FeedEntity
import com.reink.data.model.Article
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailSyncRepository @Inject constructor(
    private val emailContentSource: EmailContentSource,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend fun syncEmails(): Result<SyncResult> {
        val sinceTimestamp = preferencesRepository.getLastEmailSync()
            .takeIf { it > 0 }
            ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) // Default: last 30 days
        val syncStartedAt = System.currentTimeMillis()

        var upgraded = 0
        var inserted = 0
        var skipped = 0
        var error: Throwable? = null
        var newestProcessedReceivedAt = sinceTimestamp

        emailContentSource.streamNewArticles(sinceTimestamp)
            .onEach { email ->
                newestProcessedReceivedAt = maxOf(newestProcessedReceivedAt, email.receivedAt)
                when (processEmail(email)) {
                    EmailAction.UPGRADED -> upgraded++
                    EmailAction.INSERTED -> inserted++
                    EmailAction.SKIPPED -> skipped++
                }
            }
            .catch { e ->
                error = e
            }
            .collect()

        return if (error != null) {
            Result.failure(error!!)
        } else {
            val checkpoint = if (newestProcessedReceivedAt > sinceTimestamp) {
                newestProcessedReceivedAt
            } else {
                syncStartedAt
            }
            preferencesRepository.setLastEmailSync(checkpoint)
            Result.success(SyncResult(upgraded = upgraded, inserted = inserted, skipped = skipped))
        }
    }

    private suspend fun processEmail(email: EmailArticle): EmailAction {
        // 1. Dedup: skip if we already processed this email
        if (articleDao.existsByEmailMessageId(email.messageId)) {
            return EmailAction.SKIPPED
        }

        // 2. Upgrade: try matching view-online URL against existing articles
        val viewUrl = email.viewOnlineUrl
        if (viewUrl != null) {
            // Try exact URL match, then try slug-based match
            val slugMatch = extractSlug(viewUrl)
            val candidateUrls = listOfNotNull(viewUrl) +
                (if (slugMatch != null) findArticleUrlsBySlug(slugMatch) else emptyList())

            for (url in candidateUrls) {
                if (articleDao.existsByUrl(url)) {
                    articleDao.updateContentByUrl(
                        url = url,
                        html = email.contentHtml,
                        status = Article.CONTENT_EMAIL,
                        messageId = email.messageId,
                    )
                    autoLearnSenderPattern(email)
                    return EmailAction.UPGRADED
                }
            }
        }

        // 3. Match to a feed — subdomain from List-Id is the most reliable signal
        val matchedFeed = matchSenderToFeed(email)

        // 4. Insert: use matched feed, or create/reuse a feed for this sender
        val feed = matchedFeed ?: getOrCreateFeedForSender(email)

        val entity = ArticleEntity(
            feedId = feed.id,
            title = email.subject,
            author = email.senderName,
            url = viewUrl ?: "email://${email.messageId}",
            publishedAt = email.receivedAt,
            summary = email.subtitle,
            contentHtml = email.contentHtml,
            contentStatus = Article.CONTENT_EMAIL,
            isRead = false,
            emailMessageId = email.messageId,
        )
        articleDao.insertAllNew(listOf(entity))
        if (matchedFeed != null) autoLearnSenderPattern(email)
        return EmailAction.INSERTED
    }

    private fun extractSlug(url: String): String? {
        val match = Regex("""/p/([^/?#]+)""").find(url)
        return match?.groupValues?.get(1)
    }

    private suspend fun findArticleUrlsBySlug(slug: String): List<String> {
        // Can't do a LIKE query easily, so check all feeds' known URL patterns
        val allFeeds = feedDao.getAllOnce()
        return allFeeds.mapNotNull { feed ->
            val siteUrl = feed.siteUrl.trimEnd('/')
            if (siteUrl.isNotBlank()) "$siteUrl/p/$slug" else null
        }
    }

    private suspend fun matchSenderToFeed(email: EmailArticle): FeedEntity? {
        val allFeeds = feedDao.getAllOnce()

        // Strategy 1: Match by List-Id subdomain — most reliable, straight from email headers
        for (feed in allFeeds) {
            val feedSubdomain = feed.substackSubdomain ?: continue
            if (feedSubdomain.equals(email.substackSubdomain, ignoreCase = true)) {
                return feed
            }
        }

        // Strategy 2: Match by emailSenderPattern
        val feedsWithPatterns = feedDao.getFeedsWithEmailPatterns()
        for (feed in feedsWithPatterns) {
            val pattern = feed.emailSenderPattern ?: continue
            if (email.senderAddress.contains(pattern, ignoreCase = true)) {
                return feed
            }
        }

        // Strategy 3: Match by siteUrl host against view-online URL host
        val viewUrl = email.viewOnlineUrl
        if (viewUrl != null) {
            val viewHost = try { URI(viewUrl).host } catch (_: Exception) { null }
            if (viewHost != null) {
                for (feed in allFeeds) {
                    val siteHost = try { URI(feed.siteUrl).host } catch (_: Exception) { null }
                    if (siteHost != null && siteHost.equals(viewHost, ignoreCase = true)) {
                        return feed
                    }
                }
            }
        }

        // Strategy 4: Extract subdomain from open.substack.com/pub/{subdomain}/ URLs
        if (viewUrl != null) {
            val pubMatch = Regex("""open\.substack\.com/pub/([^/]+)""").find(viewUrl)
            val pubSubdomain = pubMatch?.groupValues?.get(1)
            if (pubSubdomain != null) {
                for (feed in allFeeds) {
                    val subdomain = feed.substackSubdomain ?: continue
                    if (subdomain.equals(pubSubdomain, ignoreCase = true)) {
                        return feed
                    }
                }
            }
        }

        return null
    }

    private suspend fun autoLearnSenderPattern(email: EmailArticle) {
        val feed = matchSenderToFeed(email) ?: return
        if (feed.emailSenderPattern != null) return

        // Use sender local part (e.g. "cubicanalytics"), not the domain ("substack.com")
        val pattern = email.senderAddress.substringBefore("@", "")
        if (pattern.isNotBlank()) {
            try {
                feedDao.updateEmailSenderPattern(feed.id, pattern)
            } catch (_: Exception) {
            }
        }
    }

    data class SyncResult(
        val upgraded: Int = 0,
        val inserted: Int = 0,
        val skipped: Int = 0,
    ) {
        val total: Int get() = upgraded + inserted
    }

    private suspend fun getOrCreateFeedForSender(email: EmailArticle): FeedEntity {
        val subdomain = email.substackSubdomain

        // Generic emails (no subdomain) — key on sender address local part
        if (subdomain.isBlank()) {
            val senderLocal = email.senderAddress.substringBefore("@", "")
            if (senderLocal.isNotBlank()) {
                val existing = feedDao.getFeedsWithEmailPatterns().find { feed ->
                    feed.emailSenderPattern?.equals(senderLocal, ignoreCase = true) == true
                }
                if (existing != null) return existing
            }

            val feedUrl = "email://${email.senderAddress}"
            val id = feedDao.insert(
                FeedEntity(
                    title = email.senderName.ifBlank { email.senderAddress },
                    url = feedUrl,
                    siteUrl = "",
                    substackSubdomain = null,
                    requiresAuth = false,
                    addedAt = System.currentTimeMillis(),
                    emailSenderPattern = senderLocal.ifBlank { null },
                    modifiedAt = System.currentTimeMillis(),
                ),
            )
            // OnConflictStrategy.IGNORE returns -1 if URL already exists
            return if (id != -1L) feedDao.getById(id)!! else feedDao.getByUrl(feedUrl)!!
        }

        // Substack emails — match by subdomain
        val allFeeds = feedDao.getAllOnce()
        val bySubdomain = allFeeds.find { feed ->
            feed.substackSubdomain?.equals(subdomain, ignoreCase = true) == true
        }
        if (bySubdomain != null) return bySubdomain

        // Create new Substack feed
        val title = email.senderName.ifBlank { subdomain }
        val siteUrl = email.viewOnlineUrl?.let { url ->
            try {
                val uri = URI(url)
                "${uri.scheme}://${uri.host}"
            } catch (_: Exception) { "" }
        } ?: ""

        val senderLocal = email.senderAddress.substringBefore("@", "")

        val feedUrl = "email://$subdomain"
        val id = feedDao.insert(
            FeedEntity(
                title = title,
                url = feedUrl,
                siteUrl = siteUrl,
                substackSubdomain = subdomain,
                requiresAuth = false,
                addedAt = System.currentTimeMillis(),
                emailSenderPattern = senderLocal.ifBlank { null },
                modifiedAt = System.currentTimeMillis(),
            ),
        )
        return if (id != -1L) feedDao.getById(id)!! else feedDao.getByUrl(feedUrl)!!
    }

    private enum class EmailAction { UPGRADED, INSERTED, SKIPPED }

}

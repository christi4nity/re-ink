package com.reink.data.repository

import android.util.Log
import com.reink.data.email.EmailArticle
import com.reink.data.email.EmailContentSource
import com.reink.data.local.ArticleDao
import com.reink.data.local.ArticleEntity
import com.reink.data.local.FeedDao
import com.reink.data.local.FeedEntity
import com.reink.data.model.Article
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

        Log.d(TAG, "Email sync starting, sinceTimestamp=$sinceTimestamp")
        return emailContentSource.fetchNewArticles(sinceTimestamp).map { emails ->
            Log.d(TAG, "Fetched ${emails.size} email articles from IMAP")
            var upgraded = 0
            var inserted = 0
            var skipped = 0

            for (email in emails) {
                when (processEmail(email)) {
                    EmailAction.UPGRADED -> upgraded++
                    EmailAction.INSERTED -> inserted++
                    EmailAction.SKIPPED -> skipped++
                }
            }

            Log.d(TAG, "Sync done: upgraded=$upgraded, inserted=$inserted, skipped=$skipped")
            preferencesRepository.setLastEmailSync(System.currentTimeMillis())
            SyncResult(upgraded = upgraded, inserted = inserted, skipped = skipped)
        }
    }

    private suspend fun processEmail(email: EmailArticle): EmailAction {
        Log.d(TAG, "Processing email: '${email.subject}' from ${email.senderAddress}")
        Log.d(TAG, "  viewOnlineUrl=${email.viewOnlineUrl}")

        // 1. Dedup: skip if we already processed this email
        if (articleDao.existsByEmailMessageId(email.messageId)) {
            Log.d(TAG, "  -> SKIP: already processed (messageId exists)")
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
                    Log.d(TAG, "  -> UPGRADE: matched existing article url=$url")
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

        // 3. Match sender or view-online URL to a feed
        val matchedFeed = matchSenderToFeed(email)
        Log.d(TAG, "  matchedFeed=${matchedFeed?.title ?: "NONE"}")

        // 4. Insert: use matched feed, or create/reuse a feed for this sender
        val feed = matchedFeed ?: getOrCreateFeedForSender(email)
        val feedId = feed.id
        val feedLabel = feed.title

        val entity = ArticleEntity(
            feedId = feedId,
            title = email.subject,
            author = email.senderName,
            url = viewUrl ?: "email://${email.messageId}",
            publishedAt = email.receivedAt,
            summary = "",
            contentHtml = email.contentHtml,
            contentStatus = Article.CONTENT_EMAIL,
            isRead = false,
            emailMessageId = email.messageId,
        )
        articleDao.insertAllNew(listOf(entity))
        if (matchedFeed != null) autoLearnSenderPattern(email)
        Log.d(TAG, "  -> INSERTED under feed '$feedLabel'")
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
        Log.d(TAG, "  Matching against ${allFeeds.size} feeds:")
        for (feed in allFeeds) {
            Log.d(TAG, "    - '${feed.title}' subdomain=${feed.substackSubdomain} siteUrl=${feed.siteUrl}")
        }

        // Strategy 1: Match by emailSenderPattern
        val feedsWithPatterns = feedDao.getFeedsWithEmailPatterns()
        for (feed in feedsWithPatterns) {
            val pattern = feed.emailSenderPattern ?: continue
            if (email.senderAddress.contains(pattern, ignoreCase = true)) {
                return feed
            }
        }

        // Strategy 2: Match by substackSubdomain against sender domain
        val senderDomain = email.senderAddress.substringAfter("@", "")

        for (feed in allFeeds) {
            val subdomain = feed.substackSubdomain ?: continue
            if (senderDomain.contains(subdomain, ignoreCase = true)) {
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

        val pattern = email.senderAddress.substringAfter("@", "")
        if (pattern.isNotBlank()) {
            try {
                feedDao.updateEmailSenderPattern(feed.id, pattern)
            } catch (e: Exception) {
                Log.w("EmailSync", "Failed to auto-learn sender pattern", e)
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
        val senderDomain = email.senderAddress.substringAfter("@", "")
        val allFeeds = feedDao.getAllOnce()

        // Reuse existing feed with this sender pattern
        val existing = allFeeds.find { feed ->
            feed.emailSenderPattern != null &&
                senderDomain.contains(feed.emailSenderPattern!!, ignoreCase = true)
        }
        if (existing != null) return existing

        // Create new feed from email sender
        val title = email.senderName.ifBlank { senderDomain }
        val siteUrl = email.viewOnlineUrl?.let { url ->
            try {
                val uri = URI(url)
                "${uri.scheme}://${uri.host}"
            } catch (_: Exception) { "" }
        } ?: ""

        Log.d(TAG, "  Creating new feed '$title' for sender $senderDomain")
        val id = feedDao.insert(
            FeedEntity(
                title = title,
                url = "email://$senderDomain",
                siteUrl = siteUrl,
                requiresAuth = false,
                addedAt = System.currentTimeMillis(),
                emailSenderPattern = senderDomain,
            ),
        )
        return feedDao.getById(id)!!
    }

    private enum class EmailAction { UPGRADED, INSERTED, SKIPPED }

    private companion object {
        const val TAG = "EmailSync"
    }
}

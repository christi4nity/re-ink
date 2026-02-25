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

        return emailContentSource.fetchNewArticles(sinceTimestamp).map { emails ->
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

            preferencesRepository.setLastEmailSync(System.currentTimeMillis())
            SyncResult(upgraded = upgraded, inserted = inserted, skipped = skipped)
        }
    }

    private suspend fun processEmail(email: EmailArticle): EmailAction {
        // 1. Dedup: skip if we already processed this email
        if (articleDao.existsByEmailMessageId(email.messageId)) {
            return EmailAction.SKIPPED
        }

        // 2. Upgrade: if view-online URL matches an existing article, update its content
        val viewUrl = email.viewOnlineUrl
        if (viewUrl != null && articleDao.existsByUrl(viewUrl)) {
            articleDao.updateContentByUrl(
                url = viewUrl,
                html = email.contentHtml,
                status = Article.CONTENT_EMAIL,
                messageId = email.messageId,
            )
            autoLearnSenderPattern(email)
            return EmailAction.UPGRADED
        }

        // 3. Match sender to feed
        val matchedFeed = matchSenderToFeed(email)

        if (matchedFeed != null) {
            // Insert new article under matched feed
            val entity = ArticleEntity(
                feedId = matchedFeed.id,
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
            autoLearnSenderPattern(email)
            return EmailAction.INSERTED
        }

        // No matching feed — skip (don't create orphan articles)
        return EmailAction.SKIPPED
    }

    private suspend fun matchSenderToFeed(email: EmailArticle): FeedEntity? {
        // Strategy 1: Match by emailSenderPattern
        val feedsWithPatterns = feedDao.getFeedsWithEmailPatterns()
        for (feed in feedsWithPatterns) {
            val pattern = feed.emailSenderPattern ?: continue
            if (email.senderAddress.contains(pattern, ignoreCase = true)) {
                return feed
            }
        }

        // Strategy 2: Match by substackSubdomain against sender domain
        val allFeeds = feedDao.getAllOnce()
        val senderDomain = email.senderAddress.substringAfter("@", "")

        for (feed in allFeeds) {
            val subdomain = feed.substackSubdomain ?: continue
            if (senderDomain.contains(subdomain, ignoreCase = true)) {
                return feed
            }
        }

        // Strategy 3: Match by siteUrl host against view-online URL host
        val viewUrl = email.viewOnlineUrl ?: return null
        val viewHost = try { URI(viewUrl).host } catch (_: Exception) { return null }
        if (viewHost == null) return null

        for (feed in allFeeds) {
            val siteHost = try { URI(feed.siteUrl).host } catch (_: Exception) { null }
            if (siteHost != null && siteHost.equals(viewHost, ignoreCase = true)) {
                return feed
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

    private enum class EmailAction { UPGRADED, INSERTED, SKIPPED }
}

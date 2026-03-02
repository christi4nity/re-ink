package com.reink.data.email

import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.InternetAddress
import net.dankito.readability4j.Readability4J
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubstackEmailParser @Inject constructor() {

    fun parse(message: Message): EmailArticle? {
        // List-Id is the reliable indicator of a genuine Substack newsletter email.
        // Forwards, verification codes, and non-article emails won't have it.
        val listId = message.getHeader("List-Id")?.firstOrNull() ?: return null
        val subdomain = extractSubdomain(listId) ?: return null

        // Skip non-article emails (e.g. Substack verification codes use <www.substack.com>)
        if (subdomain == "www") return null

        val htmlBody = extractHtmlBody(message) ?: return null
        val fromAddress = (message.from?.firstOrNull() as? InternetAddress) ?: return null
        val messageId = getMessageId(message) ?: return null

        // Prefer List-Post header for view-online URL (cleaner than scraping HTML)
        val viewOnlineUrl = extractListPostUrl(message)
            ?: extractViewOnlineUrl(htmlBody)

        val subtitle = extractSubtitle(htmlBody)

        val articleHtml = extractArticleContent(htmlBody, viewOnlineUrl)
            ?: return null

        return EmailArticle(
            subject = message.subject ?: "",
            subtitle = subtitle,
            senderAddress = fromAddress.address ?: "",
            senderName = fromAddress.personal ?: "",
            substackSubdomain = subdomain,
            receivedAt = (message.receivedDate ?: message.sentDate)?.time
                ?: System.currentTimeMillis(),
            contentHtml = articleHtml,
            viewOnlineUrl = viewOnlineUrl,
            messageId = messageId,
        )
    }

    /**
     * Extracts subdomain from List-Id header.
     * Format: `<subdomain.substack.com>` e.g. `<noahpinion.substack.com>`
     */
    private fun extractSubdomain(listId: String): String? {
        val match = Regex("""<(\w[\w-]*)\.substack\.com>""").find(listId)
        return match?.groupValues?.get(1)
    }

    /**
     * Extracts the article URL from the List-Post header.
     * Format: `<https://domain.com/p/article-slug>`
     */
    private fun extractListPostUrl(message: Message): String? {
        val listPost = message.getHeader("List-Post")?.firstOrNull() ?: return null
        val match = Regex("""<(https://[^>]+/p/[^>]+)>""").find(listPost)
        return match?.groupValues?.get(1)?.let(::stripUtmParams)
    }

    private fun extractHtmlBody(part: Part): String? {
        if (part.isMimeType("text/html")) {
            return part.content as? String
        }
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as? Multipart ?: return null
            for (i in 0 until multipart.count) {
                val result = extractHtmlBody(multipart.getBodyPart(i))
                if (result != null) return result
            }
        }
        return null
    }

    private fun getMessageId(message: Message): String? =
        message.getHeader("Message-ID")?.firstOrNull()
            ?.trim()
            ?.removePrefix("<")
            ?.removeSuffix(">")

    private fun extractViewOnlineUrl(html: String): String? {
        val pattern = Regex("""href="(https://[^"]*(?:substack\.com|[^"]+)/p/[^"]*)"""")
        val match = pattern.find(html) ?: return null
        return stripUtmParams(match.groupValues[1])
    }

    private fun stripUtmParams(url: String): String {
        return try {
            val uri = URI(url)
            val query = uri.query ?: return url
            val filtered = query.split("&")
                .filter { !it.startsWith("utm_") }
                .joinToString("&")
            val newQuery = filtered.ifEmpty { null }
            URI(uri.scheme, uri.authority, uri.path, newQuery, uri.fragment).toString()
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Extracts the post subtitle from `<h3 class="subtitle ...">text</h3>`.
     * Substack includes this in every newsletter email.
     */
    private fun extractSubtitle(html: String): String {
        val match = Regex("""<h3[^>]*class="subtitle[^"]*"[^>]*>(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)
            .find(html) ?: return ""
        return match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
    }

    private fun extractArticleContent(emailHtml: String, viewOnlineUrl: String?): String? {
        // Strip the Substack email header (title, subtitle, author/date metadata)
        // before Readability processing so we don't duplicate our own title card
        val stripped = stripEmailHeader(emailHtml)
        val sourceUrl = viewOnlineUrl ?: "https://substack.com"
        return try {
            val readability = Readability4J(sourceUrl, stripped)
            val article = readability.parse()
            val content = article.contentWithUtf8Encoding
            if (content.isNullOrBlank() || content.length < 100) null else content
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Removes the Substack email header elements: the h1 title, h3 subtitle,
     * and the post-meta table (author name + date) so they don't appear in the
     * extracted article body.
     */
    private fun stripEmailHeader(html: String): String {
        var result = html
        // Remove <h1 ...>title</h1> (the post title link)
        result = result.replace(Regex("""<h1[^>]*>.*?</h1>""", RegexOption.DOT_MATCHES_ALL), "")
        // Remove <h3 class="subtitle ...">...</h3>
        result = result.replace(Regex("""<h3[^>]*class="subtitle[^"]*"[^>]*>.*?</h3>""", RegexOption.DOT_MATCHES_ALL), "")
        // Remove <table class="post-meta" ...>...</table> (author + date row)
        result = result.replace(Regex("""<table[^>]*class="post-meta"[^>]*>.*?</table>""", RegexOption.DOT_MATCHES_ALL), "")
        return result
    }
}

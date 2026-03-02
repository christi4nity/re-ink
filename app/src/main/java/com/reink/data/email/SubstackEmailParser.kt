package com.reink.data.email

import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.InternetAddress
import org.jsoup.Jsoup
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

        val doc = Jsoup.parse(htmlBody)
        val subtitle = doc.selectFirst("h3.subtitle")?.text()?.trim() ?: ""
        val headerImageUrl = doc.selectFirst("img.header-image")?.attr("src")
            ?.takeIf { it.isNotBlank() }

        val articleHtml = extractArticleContent(doc) ?: return null

        val headerImageHtml = if (headerImageUrl != null) {
            """<img src="$headerImageUrl" style="width:100%;height:auto;margin-bottom:1em;">"""
        } else ""

        return EmailArticle(
            subject = message.subject ?: "",
            subtitle = subtitle,
            senderAddress = fromAddress.address ?: "",
            senderName = fromAddress.personal ?: "",
            substackSubdomain = subdomain,
            receivedAt = (message.receivedDate ?: message.sentDate)?.time
                ?: System.currentTimeMillis(),
            contentHtml = headerImageHtml + articleHtml,
            viewOnlineUrl = viewOnlineUrl,
            messageId = messageId,
        )
    }

    /**
     * Extracts article content directly from the Substack email DOM.
     * Uses `div.body.markup` for the article text, then collects any
     * sibling captioned-image containers that Substack places outside
     * the body markup in table wrappers.
     */
    private fun extractArticleContent(doc: org.jsoup.nodes.Document): String? {
        val bodyMarkup = doc.selectFirst("div.body.markup") ?: return null

        // Remove junk elements from body markup
        bodyMarkup.select(".subscription-widget-wrap, .subscribe-widget, .post-ufi, .footer-wrap, .share-dialog, .like-button-container, .button-wrapper").remove()

        // Collect images from captioned-image containers that are siblings
        // to body markup (Substack wraps them in tables outside the body div)
        val postDiv = doc.selectFirst("div.post.typography")
        val images = postDiv?.select(".captioned-image-container, .captioned-image-container-static")
            ?.mapNotNull { container ->
                val img = container.selectFirst("img:not(.icon):not(.email-button-text)") ?: return@mapNotNull null
                val src = img.attr("src")
                if (src.isBlank() || "w_36" in src) return@mapNotNull null
                val caption = container.selectFirst("figcaption")?.text()?.trim()
                if (caption.isNullOrBlank()) {
                    """<figure><img src="$src"></figure>"""
                } else {
                    """<figure><img src="$src"><figcaption>$caption</figcaption></figure>"""
                }
            } ?: emptyList()

        // Build final content: images that were before body markup go first
        val bodyHtml = bodyMarkup.html()
        if (bodyHtml.length < 100 && images.isEmpty()) return null

        // Insert images at the top if they were above the body markup in the email
        return images.joinToString("\n") + bodyHtml
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
}

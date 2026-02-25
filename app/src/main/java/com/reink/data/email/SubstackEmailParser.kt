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
        val htmlBody = extractHtmlBody(message) ?: return null

        val fromAddress = (message.from?.firstOrNull() as? InternetAddress) ?: return null
        val messageId = getMessageId(message) ?: return null

        val viewOnlineUrl = extractViewOnlineUrl(htmlBody)

        val articleHtml = extractArticleContent(htmlBody, viewOnlineUrl)
            ?: return null

        return EmailArticle(
            subject = message.subject ?: "",
            senderAddress = fromAddress.address ?: "",
            senderName = fromAddress.personal ?: "",
            receivedAt = (message.receivedDate ?: message.sentDate)?.time
                ?: System.currentTimeMillis(),
            contentHtml = articleHtml,
            viewOnlineUrl = viewOnlineUrl,
            messageId = messageId,
        )
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

    private fun extractArticleContent(emailHtml: String, viewOnlineUrl: String?): String? {
        val sourceUrl = viewOnlineUrl ?: "https://substack.com"
        return try {
            val readability = Readability4J(sourceUrl, emailHtml)
            val article = readability.parse()
            val content = article.contentWithUtf8Encoding
            if (content.isNullOrBlank() || content.length < 100) null else content
        } catch (_: Exception) {
            null
        }
    }
}

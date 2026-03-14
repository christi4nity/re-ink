package com.reink.data.email

import com.reink.data.repository.PreferencesRepository
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses emails from allowlisted sender domains. Performs minimal HTML cleanup —
 * strips head/style/script tags and tracking pixels, preserves all content as-is.
 *
 * The allowlist is cached in memory and refreshed once per sync pass via
 * [refresh], called by [EmailParserChain.refreshParsers].
 */
@Singleton
class GenericEmailParser @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : EmailParser, Refreshable {

    @Volatile
    private var cachedDomains: Set<String> = emptySet()

    override suspend fun refresh() {
        cachedDomains = preferencesRepository.getAllowedSenderDomains()
    }

    override fun canParse(message: Message): Boolean {
        if (cachedDomains.isEmpty()) return false
        val fromAddress = (message.from?.firstOrNull() as? InternetAddress)
            ?.address ?: return false
        val domain = fromAddress.substringAfter("@", "")
        return cachedDomains.any { domain.equals(it, ignoreCase = true) }
    }

    override fun parse(message: Message): EmailArticle? {
        val fromAddress = (message.from?.firstOrNull() as? InternetAddress)
            ?: return null
        val messageId = getMessageId(message) ?: return null
        val htmlBody = extractHtmlBody(message) ?: return null

        val cleanHtml = cleanEmailHtml(htmlBody)

        return EmailArticle(
            subject = message.subject ?: "",
            subtitle = "",
            senderAddress = fromAddress.address ?: "",
            senderName = fromAddress.personal ?: fromAddress.address ?: "",
            substackSubdomain = "",
            receivedAt = (message.receivedDate ?: message.sentDate)?.time
                ?: System.currentTimeMillis(),
            contentHtml = cleanHtml,
            viewOnlineUrl = null,
            messageId = messageId,
        )
    }
}

/**
 * Minimal HTML cleanup for generic emails:
 * - Strips head, style, script tags
 * - Removes tracking pixels (1x1 images)
 * - Preserves all other content as-is
 */
internal fun cleanEmailHtml(html: String): String {
    val doc = Jsoup.parse(html)
    doc.select("head, style, script").remove()
    doc.select("img").forEach { img ->
        val width = img.attr("width")
        val height = img.attr("height")
        if (width == "1" || height == "1") {
            img.remove()
        }
    }
    return doc.body()?.html() ?: html
}

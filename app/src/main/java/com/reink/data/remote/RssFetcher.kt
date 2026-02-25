package com.reink.data.remote

import com.prof18.rssparser.RssParser
import com.reink.data.model.Article
import javax.inject.Inject
import javax.inject.Singleton

data class FetchResult(
    val articles: List<Article>,
    val imageUrl: String? = null,
)

@Singleton
class RssFetcher @Inject constructor(
    private val rssParser: RssParser,
) {
    suspend fun fetchFeed(url: String, feedId: Long): Result<FetchResult> = runCatching {
        val channel = rssParser.getRssChannel(url)
        val articles = channel.items.map { item ->
            val rawContent = item.content ?: item.description ?: ""
            val isTruncated = detectTruncation(rawContent)
            Article(
                feedId = feedId,
                title = item.title ?: "",
                author = item.author ?: "",
                url = item.link ?: "",
                publishedAt = item.pubDate?.let { parseDate(it) } ?: System.currentTimeMillis(),
                summary = item.description ?: "",
                contentHtml = cleanContent(rawContent),
                contentStatus = if (isTruncated) Article.CONTENT_TRUNCATED else Article.CONTENT_FULL,
            )
        }
        FetchResult(
            articles = articles,
            imageUrl = channel.image?.url?.takeIf { it.isNotBlank() },
        )
    }

    private val readMorePattern = Regex(
        """<p>\s*<a\s+href=[^>]*>\s*Read more\s*</a>\s*</p>""",
        RegexOption.IGNORE_CASE,
    )

    private val paywallPattern = Regex(
        """class\s*=\s*["'][^"']*\bpaywall\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Detect if RSS content is truncated (paid article preview).
     * Must be called BEFORE [cleanContent] strips the "Read more" link.
     */
    private fun detectTruncation(html: String): Boolean =
        readMorePattern.containsMatchIn(html) || paywallPattern.containsMatchIn(html)

    /**
     * Strip trailing "Read more" links that some Substack publishers
     * include in their RSS feed. These are links back to the web version
     * and aren't useful in a dedicated reader.
     */
    private fun cleanContent(html: String): String =
        html.replace(readMorePattern, "").trimEnd()

    private fun parseDate(dateStr: String): Long {
        return try {
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                .parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}

package com.reink.data.remote

import com.prof18.rssparser.RssParser
import com.reink.data.model.Article
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssFetcher @Inject constructor(
    private val rssParser: RssParser,
) {
    suspend fun fetchFeed(url: String, feedId: Long): Result<List<Article>> = runCatching {
        val channel = rssParser.getRssChannel(url)
        channel.items.map { item ->
            Article(
                feedId = feedId,
                title = item.title ?: "",
                author = item.author ?: "",
                url = item.link ?: "",
                publishedAt = item.pubDate?.let { parseDate(it) } ?: System.currentTimeMillis(),
                summary = item.description ?: "",
                contentHtml = item.content ?: item.description ?: "",
            )
        }
    }

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

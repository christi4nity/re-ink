package com.reink.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedArticle(
    val title: String,
    val contentHtml: String,
)

@Singleton
class ArticleExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun extract(url: String): Result<ExtractedArticle> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            val html = response.body?.string()
                ?: throw IllegalStateException("Empty response from $url")

            val readability = Readability4J(finalUrl, html)
            val article = readability.parse()

            ExtractedArticle(
                title = article.title ?: "",
                contentHtml = article.contentWithUtf8Encoding ?: "",
            )
        }
    }
}

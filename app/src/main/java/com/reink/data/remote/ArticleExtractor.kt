package com.reink.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedArticle(
    val title: String,
    val contentHtml: String,
    val sourceDomain: String,
    val excerpt: String? = null,
)

@Singleton
class ArticleExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    // Clean client without auth interceptor — for following redirects from substack.com
    // to external sites without cookies interfering
    private val cleanClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun extract(url: String): Result<ExtractedArticle> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", ACCEPT_HEADER)
                .header("Accept-Language", ACCEPT_LANGUAGE_HEADER)
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            cleanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} while fetching $url")
                }

                val finalUrl = response.request.url.toString()
                Log.d(TAG, "Fetched $url -> $finalUrl (HTTP ${response.code})")
                val html = response.body?.string()
                    ?: throw IllegalStateException("Empty response from $url")
                Log.d(TAG, "Response body: ${html.length} chars")

                if (looksLikeAccessInterstitial(html)) {
                    Log.w(TAG, "Possible anti-bot interstitial at $finalUrl")
                }

                val readability = Readability4J(finalUrl, html)
                val article = readability.parse()

                val extractedTitle = article.title.orEmpty().trim()
                val extractedContent = article.contentWithUtf8Encoding.orEmpty().trim()
                // Strip HTML tags to measure actual text content
                val textLength = extractedContent.replace(Regex("<[^>]+>"), "").trim().length

                Log.d(TAG, "Readability result: title='${extractedTitle.take(60)}', content=${extractedContent.length} chars, text=$textLength chars")

                if (textLength < 200) {
                    throw IllegalStateException("Insufficient content extracted at $finalUrl ($textLength chars text)")
                }

                if (looksLikelyPaywalled(html, extractedContent)) {
                    Log.w(TAG, "Content may be paywalled at $finalUrl (${extractedContent.length} chars)")
                }

                // Use Readability title, fall back to <title> tag or og:title
                val title = extractedTitle.takeIf { it.isNotBlank() && !it.startsWith("http") }
                    ?: extractOgTitle(html)
                    ?: extractHtmlTitle(html)
                    ?: extractedTitle

                ExtractedArticle(
                    title = title,
                    contentHtml = extractedContent,
                    sourceDomain = extractSiteName(html) ?: extractDomain(finalUrl),
                    excerpt = article.excerpt?.trim()?.takeIf { it.isNotBlank() }
                        ?: extractOgDescription(html),
                )
            }
        }
    }

    companion object {
        private const val TAG = "ArticleExtractor"

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT_HEADER =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE_HEADER = "en-US,en;q=0.9"
        private const val MIN_LIKELY_FULL_CONTENT_LENGTH = 900

        private val interstitialMarkers = listOf(
            "__cf_chl_",
            "cf-browser-verification",
            "checking your browser",
            "verify you are human",
            "security check to access",
            "please enable javascript",
            "enable javascript to continue",
            "disable your ad blocker",
            "disable ad blocker",
            "pardon our interruption",
        )

        private val paywallMarkers = listOf(
            "class=\"paywall",
            "class='paywall",
            "subscriber-only",
            "subscribe to continue reading",
            "sign in to continue reading",
            "membership required",
            "already a subscriber",
        )

        private val OG_TITLE_REGEX =
            Regex("""<meta[^>]+property\s*=\s*["']og:title["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val OG_TITLE_REVERSE_REGEX =
            Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:title["']""", RegexOption.IGNORE_CASE)
        private val HTML_TITLE_REGEX =
            Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)

        fun extractOgTitle(html: String): String? {
            val match = OG_TITLE_REGEX.find(html) ?: OG_TITLE_REVERSE_REGEX.find(html)
            return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("http") }
        }

        fun extractHtmlTitle(html: String): String? {
            val match = HTML_TITLE_REGEX.find(html)
            return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("http") }
        }

        private val OG_DESCRIPTION_REGEX =
            Regex("""<meta[^>]+property\s*=\s*["']og:description["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val OG_DESCRIPTION_REVERSE_REGEX =
            Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:description["']""", RegexOption.IGNORE_CASE)

        fun extractOgDescription(html: String): String? {
            val match = OG_DESCRIPTION_REGEX.find(html) ?: OG_DESCRIPTION_REVERSE_REGEX.find(html)
            return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }

        private val OG_SITE_NAME_REGEX =
            Regex("""<meta[^>]+property\s*=\s*["']og:site_name["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val OG_SITE_NAME_REVERSE_REGEX =
            Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:site_name["']""", RegexOption.IGNORE_CASE)

        fun extractSiteName(html: String): String? {
            val match = OG_SITE_NAME_REGEX.find(html) ?: OG_SITE_NAME_REVERSE_REGEX.find(html)
            return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }

        fun extractDomain(url: String): String =
            runCatching { URI(url).host?.removePrefix("www.") ?: "" }.getOrDefault("")

        private fun looksLikeAccessInterstitial(html: String): Boolean {
            val normalized = html.lowercase(Locale.US)
            val hits = interstitialMarkers.count { marker -> normalized.contains(marker) }
            return hits >= 2 || normalized.contains("__cf_chl_")
        }

        private fun looksLikelyPaywalled(rawHtml: String, extractedContent: String): Boolean {
            if (extractedContent.length >= MIN_LIKELY_FULL_CONTENT_LENGTH) return false
            val normalizedRaw = rawHtml.lowercase(Locale.US)
            return paywallMarkers.any { marker -> normalizedRaw.contains(marker) }
        }
    }
}

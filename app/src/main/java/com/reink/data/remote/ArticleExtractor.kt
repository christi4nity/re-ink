package com.reink.data.remote

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
    private val cleanClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val noRedirectClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun extract(url: String): Result<ExtractedArticle> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedUrl = resolveRedirects(url)

            // Strategy 1: Browser UA (current behavior)
            val browserResult = fetchAndParse(resolvedUrl, resolvedUrl, BROWSER_USER_AGENT, "BrowserUA")
            if (browserResult != null) return@runCatching browserResult

            // Strategy 2: Googlebot UA
            val googlebotResult = fetchAndParse(resolvedUrl, resolvedUrl, GOOGLEBOT_USER_AGENT, "Googlebot")
            if (googlebotResult != null) return@runCatching googlebotResult

            // Strategy 3: Google Cache
            val cacheUrl = "$GOOGLE_CACHE_PREFIX${java.net.URLEncoder.encode(resolvedUrl, "UTF-8")}"
            val cacheResult = fetchAndParse(cacheUrl, resolvedUrl, BROWSER_USER_AGENT, "GoogleCache")
            if (cacheResult != null) return@runCatching cacheResult

            // Strategy 4: archive.org Wayback Machine
            val archiveUrl = "$ARCHIVE_ORG_PREFIX$resolvedUrl"
            val archiveResult = fetchAndParse(archiveUrl, resolvedUrl, BROWSER_USER_AGENT, "ArchiveOrg")
            if (archiveResult != null) return@runCatching archiveResult

            throw IllegalStateException("All extraction strategies failed for $resolvedUrl")
        }
    }

    private fun resolveRedirects(url: String): String {
        if (!url.contains("/redirect/") && !url.contains("link.")) return url
        var current = url
        repeat(MAX_REDIRECT_HOPS) {
            val next = try {
                val request = Request.Builder()
                    .url(current)
                    .head()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build()
                noRedirectClient.newCall(request).execute().use { response ->
                    val location = response.header("Location")
                    if (response.code in 301..308 && location != null) location else null
                }
            } catch (_: Exception) {
                null
            }
            if (next == null) return current
            current = next
        }
        return current
    }

    private fun fetchAndParse(
        fetchUrl: String,
        articleUrl: String,
        userAgent: String,
        label: String,
    ): ExtractedArticle? {
        return try {
            val request = Request.Builder()
                .url(fetchUrl)
                .header("User-Agent", userAgent)
                .header("Accept", ACCEPT_HEADER)
                .header("Accept-Language", ACCEPT_LANGUAGE_HEADER)
                .header("Upgrade-Insecure-Requests", "1")
                .build()

            cleanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }

                val finalUrl = response.request.url.toString()
                val html = response.body?.string()
                if (html.isNullOrBlank()) {
                    return null
                }

                if (looksLikeAccessInterstitial(html)) {
                    return null
                }

                val readability = Readability4J(articleUrl, html)
                val article = readability.parse()

                val extractedTitle = article.title.orEmpty().trim()
                val extractedContent = article.contentWithUtf8Encoding.orEmpty().trim()
                val textLength = extractedContent.replace(STRIP_TAGS_REGEX, "").trim().length

                if (textLength < MIN_TEXT_LENGTH) {
                    return null
                }

                if (looksLikelyPaywalled(html, extractedContent)) {
                    return null
                }

                val title = extractedTitle.takeIf { it.isNotBlank() && !it.startsWith("http") }
                    ?: extractOgTitle(html)
                    ?: extractHtmlTitle(html)
                    ?: extractedTitle

                ExtractedArticle(
                    title = title,
                    contentHtml = extractedContent,
                    sourceDomain = extractSiteName(html) ?: extractDomain(articleUrl),
                    excerpt = article.excerpt?.trim()?.takeIf { it.isNotBlank() }
                        ?: extractOgDescription(html),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Mobile Safari/537.36"
        private const val GOOGLEBOT_USER_AGENT =
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
        private const val ACCEPT_HEADER =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE_HEADER = "en-US,en;q=0.9"
        private const val MIN_TEXT_LENGTH = 200
        private const val MIN_LIKELY_FULL_CONTENT_LENGTH = 900
        private const val MAX_REDIRECT_HOPS = 5
        private const val GOOGLE_CACHE_PREFIX =
            "https://webcache.googleusercontent.com/search?q=cache:"
        private const val ARCHIVE_ORG_PREFIX =
            "https://web.archive.org/web/"
        private val STRIP_TAGS_REGEX = Regex("<[^>]+>")

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

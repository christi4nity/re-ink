package com.reink.data.remote

import android.util.Log
import android.webkit.CookieManager
import com.reink.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts full article content from paid Substack posts using server-side
 * HTTP requests (OkHttp) instead of WebView. Bypasses the Android CookieManager
 * limitation where cookies aren't sent on a cold WebView's initial request.
 *
 * Two-phase strategy:
 * 1. **Substack API** — `GET /api/v1/posts/{slug}` on subdomain → `body_html`
 * 2. **HTML scrape** — `GET {articleUrl}` with explicit Cookie header → parse `__NEXT_DATA__`
 */
@Singleton
class SubstackContentExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val preferencesRepository: PreferencesRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Attempts to extract full article HTML for a paid Substack post.
     * Returns the HTML body on success, or null on failure.
     */
    suspend fun extract(articleUrl: String, substackSubdomain: String?): String? =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "extract: url=$articleUrl subdomain=$substackSubdomain")
            val slug = extractSlug(articleUrl)
            if (slug == null) {
                Log.w(TAG, "extract: no slug found in URL")
                return@withContext null
            }
            Log.d(TAG, "extract: slug=$slug")

            // Try both phases and pick the longer result
            var apiResult: String? = null
            if (substackSubdomain != null) {
                apiResult = extractViaApi(substackSubdomain, slug)
                Log.d(TAG, "extract: Phase 1 (API) = ${apiResult?.length ?: 0} chars")
            }

            val scrapeResult = extractViaHtmlScrape(articleUrl)
            Log.d(TAG, "extract: Phase 2 (scrape) = ${scrapeResult?.length ?: 0} chars")

            // Use whichever returned more content
            val best = listOfNotNull(apiResult, scrapeResult).maxByOrNull { it.length }
            Log.d(TAG, "extract: best = ${best?.length ?: 0} chars (from ${if (best == apiResult) "API" else "scrape"})")
            best
        }

    /**
     * Phase 1: Fetch article content via Substack's REST API.
     * The shared OkHttpClient has SubstackAuthInterceptor which automatically
     * adds the SID cookie for *.substack.com domains.
     */
    private fun extractViaApi(subdomain: String, slug: String): String? {
        val url = "https://$subdomain.substack.com/api/v1/posts/$slug"
        Log.d(TAG, "Phase 1: GET $url")
        val cookies = getAllCookies("https://$subdomain.substack.com")
        Log.d(TAG, "Phase 1: cookies=${cookies?.length ?: 0} chars")
        val builder = Request.Builder().url(url)
        if (!cookies.isNullOrBlank()) {
            builder.header("Cookie", cookies)
        }
        val request = builder.build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            Log.d(TAG, "Phase 1: response code=${response.code}")
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            Log.d(TAG, "Phase 1: body length=${body.length}")
            // Log top-level keys to discover available fields
            try {
                val jsonObj = Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                val keys = jsonObj?.keys?.joinToString(", ")
                Log.d(TAG, "Phase 1: top-level keys=[$keys]")
                val audienceKey = jsonObj?.get("audience")
                Log.d(TAG, "Phase 1: audience=$audienceKey")
            } catch (_: Exception) {}
            val post = json.decodeFromString<SubstackPostResponse>(body)
            val html = post.bodyHtml
            Log.d(TAG, "Phase 1: body_html=${html?.length ?: 0} chars")
            if (html.isNullOrBlank() || html.length < 200) null else html
        } catch (e: Exception) {
            Log.e(TAG, "Phase 1: exception", e)
            null
        }
    }

    /**
     * Phase 2: Fetch the article page HTML and extract content from
     * Next.js `__NEXT_DATA__` script tag. Manually adds the Cookie header
     * to handle custom domains (e.g. thefp.com) that the interceptor doesn't cover.
     */
    private suspend fun extractViaHtmlScrape(articleUrl: String): String? {
        // Try full cookie jar from CookieManager first, fall back to just SID
        val cmCookies = getAllCookies(articleUrl)
        val sid = preferencesRepository.getSubstackSid()
        val cookies = if (!cmCookies.isNullOrBlank()) cmCookies else "substack.sid=$sid"
        Log.d(TAG, "Phase 2: GET $articleUrl cookieLen=${cookies.length} fromCM=${!cmCookies.isNullOrBlank()}")
        if (sid.isBlank() && cmCookies.isNullOrBlank()) {
            Log.w(TAG, "Phase 2: no cookies available")
            return null
        }

        val request = Request.Builder()
            .url(articleUrl)
            .header("Cookie", cookies)
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            Log.d(TAG, "Phase 2: response code=${response.code}")
            if (!response.isSuccessful) return null

            val pageHtml = response.body?.string() ?: return null
            Log.d(TAG, "Phase 2: page HTML length=${pageHtml.length}")
            val hasNextData = pageHtml.contains("__NEXT_DATA__")
            Log.d(TAG, "Phase 2: has __NEXT_DATA__=$hasNextData")

            // Log what selectors we can find in the HTML
            val hasBodyMarkup = pageHtml.contains("class=\"body markup\"") || pageHtml.contains("class='body markup'")
            val hasAvailContent = pageHtml.contains("available-content")
            val hasPaywall = pageHtml.contains("class=\"paywall\"") || pageHtml.contains("class=\"paywall-title\"")
            Log.d(TAG, "Phase 2: hasBodyMarkup=$hasBodyMarkup hasAvailContent=$hasAvailContent hasPaywall=$hasPaywall")

            // Log a snippet around "body markup" or "available-content" if found
            val bodyIdx = pageHtml.indexOf("body markup")
            if (bodyIdx >= 0) {
                val snippet = pageHtml.substring(maxOf(0, bodyIdx - 50), minOf(pageHtml.length, bodyIdx + 200))
                Log.d(TAG, "Phase 2: body markup context: $snippet")
            }

            // Try __NEXT_DATA__ first, then direct HTML parsing
            val nextDataResult = parseNextDataBodyHtml(pageHtml)
            if (nextDataResult != null) {
                Log.d(TAG, "Phase 2: __NEXT_DATA__ parse succeeded, ${nextDataResult.length} chars")
                nextDataResult
            } else {
                val htmlResult = parseHtmlBodyMarkup(pageHtml)
                Log.d(TAG, "Phase 2: HTML body markup parse = ${htmlResult?.length ?: 0} chars")
                htmlResult
            }
        } catch (e: Exception) {
            Log.e(TAG, "Phase 2: exception", e)
            null
        }
    }

    /**
     * Parses the `__NEXT_DATA__` JSON embedded in the page HTML to extract
     * `props.pageProps.post.body_html`.
     */
    private fun parseNextDataBodyHtml(pageHtml: String): String? {
        val startMarker = """<script id="__NEXT_DATA__" type="application/json">"""
        val startIdx = pageHtml.indexOf(startMarker)
        if (startIdx < 0) return null

        val jsonStart = startIdx + startMarker.length
        val jsonEnd = pageHtml.indexOf("</script>", jsonStart)
        if (jsonEnd < 0) return null

        val jsonStr = pageHtml.substring(jsonStart, jsonEnd)

        return try {
            val nextData = json.decodeFromString<NextData>(jsonStr)
            val html = nextData.props?.pageProps?.post?.bodyHtml
            if (html.isNullOrBlank() || html.length < 200) null else html
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads all cookies from Android's CookieManager for the given URL.
     * These include the full session from WebView sign-in (Cloudflare, CSRF, etc.).
     */
    private fun getAllCookies(url: String): String? {
        return try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses article body from raw HTML using the `div.body.markup` selector.
     * Falls back to `available-content` if body.markup isn't found.
     * Strips paywall and subscription widgets.
     */
    private fun parseHtmlBodyMarkup(pageHtml: String): String? {
        // Look for the body markup div — this is where Substack puts article content
        val bodyStart = pageHtml.indexOf("class=\"body markup\"")
        if (bodyStart < 0) return null

        // Find the opening <div that contains this class
        val divStart = pageHtml.lastIndexOf("<div", bodyStart)
        if (divStart < 0) return null

        // Find matching closing </div> by counting nesting
        var depth = 0
        var i = divStart
        var contentEnd = -1
        while (i < pageHtml.length) {
            if (pageHtml.startsWith("<div", i)) {
                depth++
                i += 4
            } else if (pageHtml.startsWith("</div>", i)) {
                depth--
                if (depth == 0) {
                    contentEnd = i + 6
                    break
                }
                i += 6
            } else {
                i++
            }
        }

        if (contentEnd < 0) return null

        val rawHtml = pageHtml.substring(divStart, contentEnd)
        Log.d(TAG, "parseHtmlBodyMarkup: raw=${rawHtml.length} chars")
        return if (rawHtml.length > 200) rawHtml else null
    }

    companion object {
        private const val TAG = "ReInk"

        /**
         * Extracts the article slug from a Substack URL.
         * Handles paths like `/p/my-article-slug` and `/p/my-article-slug?utm_source=...`
         */
        fun extractSlug(articleUrl: String): String? {
            val path = try {
                java.net.URI(articleUrl).path ?: return null
            } catch (_: Exception) {
                return null
            }
            val segments = path.trimEnd('/').split('/')
            val pIndex = segments.indexOf("p")
            return if (pIndex >= 0 && pIndex < segments.lastIndex) {
                segments[pIndex + 1]
            } else {
                null
            }
        }
    }
}

// --- Serialization models (private to this file) ---

@Serializable
private data class SubstackPostResponse(
    @SerialName("body_html") val bodyHtml: String? = null,
)

@Serializable
private data class NextData(
    val props: NextProps? = null,
)

@Serializable
private data class NextProps(
    val pageProps: PageProps? = null,
)

@Serializable
private data class PageProps(
    val post: NextPost? = null,
)

@Serializable
private data class NextPost(
    @SerialName("body_html") val bodyHtml: String? = null,
)

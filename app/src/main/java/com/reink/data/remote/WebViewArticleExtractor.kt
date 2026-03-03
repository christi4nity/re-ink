package com.reink.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Extracts article content by loading the URL in a WebView (handles JS-rendered pages),
 * then injecting Mozilla's Readability.js to extract the article.
 *
 * Used as a fallback when OkHttp + Readability4J fails.
 */
@Singleton
class WebViewArticleExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val readabilityJs: String by lazy {
        context.assets.open("js/Readability.js").bufferedReader().readText()
    }

    /**
     * Resolves redirect URLs (e.g. substack.com/redirect/...) to the final destination
     * so the WebView loads the actual page directly.
     */
    private suspend fun resolveRedirects(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head().build()
            okHttpClient.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    suspend fun extract(url: String): Result<ExtractedArticle> {
        val resolvedUrl = resolveRedirects(url)
        Log.d(TAG, "WebView extraction starting for $resolvedUrl (from $url)")

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                mainHandler.post {
                    @SuppressLint("SetJavaScriptEnabled")
                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = BROWSER_USER_AGENT
                    }

                    val bridge = ExtractionBridge { extracted ->
                        webView.destroy()
                        if (continuation.isActive) {
                            continuation.resume(extracted)
                        }
                    }

                    webView.addJavascriptInterface(bridge, "ReInkReadability")

                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            Log.d(TAG, "JS [${msg.messageLevel()}] ${msg.message()}")
                            return true
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        private var injected = false

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = false

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            view ?: return
                            if (pageUrl == "about:blank" || injected) return
                            injected = true

                            Log.d(TAG, "onPageFinished: $pageUrl, scheduling Readability.js injection")
                            // Wait for JS rendering, then inject Readability
                            mainHandler.postDelayed({
                                Log.d(TAG, "Injecting Readability.js (${readabilityJs.length} chars)")
                                view.evaluateJavascript(readabilityJs) {
                                    Log.d(TAG, "Readability.js injected, running extraction")
                                    view.evaluateJavascript(EXTRACTION_JS, null)
                                }
                            }, RENDER_DELAY_MS)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                webView.destroy()
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        mainHandler.post { webView.destroy() }
                    }

                    webView.loadUrl(resolvedUrl)
                }
            }
        }

        return if (result != null) {
            Result.success(result)
        } else {
            Result.failure(IllegalStateException("WebView extraction timed out for $url"))
        }
    }

    private class ExtractionBridge(
        private val onResult: (ExtractedArticle?) -> Unit,
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var reported = false

        @JavascriptInterface
        fun onResult(title: String, content: String, siteName: String, domain: String, excerpt: String) {
            if (reported) return
            reported = true
            Log.d(TAG, "Extraction result: title='${title.take(60)}', content=${content.length} chars")
            mainHandler.post {
                val textLength = content.replace(Regex("<[^>]+>"), "").trim().length
                if (textLength < 200) {
                    onResult(null)
                } else {
                    onResult(
                        ExtractedArticle(
                            title = title,
                            contentHtml = content,
                            sourceDomain = siteName.ifBlank { domain },
                            excerpt = excerpt.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }

        @JavascriptInterface
        fun onError(message: String) {
            if (reported) return
            reported = true
            Log.e(TAG, "Extraction error: $message")
            mainHandler.post { onResult(null) }
        }
    }

    companion object {
        private const val TAG = "WebViewExtract"
        private const val TIMEOUT_MS = 30_000L
        private const val RENDER_DELAY_MS = 2000L

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Mobile Safari/537.36"

        private const val EXTRACTION_JS = """
            (function() {
                try {
                    var clone = document.cloneNode(true);
                    var article = new Readability(clone).parse();
                    if (article && article.content) {
                        var siteName = '';
                        var meta = document.querySelector('meta[property="og:site_name"]');
                        if (meta) siteName = meta.getAttribute('content') || '';
                        var domain = location.hostname.replace(/^www\./, '');
                        var excerpt = article.excerpt || '';
                        if (!excerpt) {
                            var desc = document.querySelector('meta[property="og:description"]');
                            if (desc) excerpt = desc.getAttribute('content') || '';
                        }
                        ReInkReadability.onResult(
                            article.title || '',
                            article.content || '',
                            siteName,
                            domain,
                            excerpt
                        );
                    } else {
                        ReInkReadability.onError('Readability returned no content');
                    }
                } catch(e) {
                    ReInkReadability.onError(e.toString());
                }
            })();
        """
    }
}

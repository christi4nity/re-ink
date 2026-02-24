package com.reink.ui.reader

import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.reink.data.model.ReadingPreferences

@Composable
fun ArticleWebView(
    contentHtml: String,
    preferences: ReadingPreferences,
    onLinkTapped: (String) -> Unit,
    currentPage: Int = 0,
    onPageCountChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cssOverrides = buildCssOverrides(preferences)
    val wrappedHtml = wrapHtml(contentHtml, cssOverrides)
    val isPaginated = preferences.paginationMode == "paginated"

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false

                addJavascriptInterface(
                    PageBridge(onPageCount = onPageCountChanged),
                    "ReInk",
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        onLinkTapped(url)
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view ?: return
                        view.evaluateJavascript(
                            """
                            (function() {
                                var isPaginated = document.body.style.columnWidth !== '' ||
                                    getComputedStyle(document.body).columnWidth !== 'auto';
                                if (isPaginated) {
                                    var pageCount = Math.max(1, Math.ceil(document.body.scrollWidth / window.innerWidth));
                                    ReInk.reportPageCount(pageCount);
                                    var page = $currentPage;
                                    window.scrollTo(page * window.innerWidth, 0);
                                }
                            })();
                            """.trimIndent(),
                            null,
                        )
                    }
                }

                loadDataWithBaseURL(
                    "file:///android_asset/",
                    wrappedHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                wrappedHtml,
                "text/html",
                "UTF-8",
                null,
            )
            if (isPaginated) {
                webView.evaluateJavascript(
                    "window.scrollTo(${currentPage} * window.innerWidth, 0);",
                    null,
                )
            }
        },
        modifier = modifier,
    )
}

private class PageBridge(
    private val onPageCount: (Int) -> Unit,
) {
    @JavascriptInterface
    fun reportPageCount(count: Int) {
        onPageCount(count)
    }
}

private fun buildCssOverrides(prefs: ReadingPreferences): String {
    val rootVars = """
        :root {
            --font-family: '${prefs.fontFamily}', serif;
            --font-size: ${prefs.fontSize}px;
            --line-height: ${prefs.lineHeight};
            --margin-horizontal: ${prefs.marginHorizontal}px;
            --text-align: ${prefs.textAlign};
        }
    """.trimIndent()

    if (prefs.paginationMode != "paginated") return rootVars

    val paginationCss = """
        html, body {
            height: 100vh;
            overflow: hidden;
            margin: 0;
            padding: 0;
        }
        body {
            column-width: 100vw;
            column-gap: 0;
            column-fill: auto;
            padding: 12px var(--margin-horizontal);
            box-sizing: border-box;
        }
    """.trimIndent()

    return "$rootVars\n$paginationCss"
}

private fun wrapHtml(content: String, cssOverrides: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <link rel="stylesheet" href="css/article.css">
        <style>$cssOverrides</style>
    </head>
    <body>
        $content
    </body>
    </html>
""".trimIndent()

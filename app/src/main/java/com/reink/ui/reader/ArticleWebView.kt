package com.reink.ui.reader

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
    modifier: Modifier = Modifier,
) {
    val cssOverrides = buildCssOverrides(preferences)
    val wrappedHtml = wrapHtml(contentHtml, cssOverrides)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        onLinkTapped(url)
                        return true
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
        },
        modifier = modifier,
    )
}

private fun buildCssOverrides(prefs: ReadingPreferences): String = """
    :root {
        --font-family: '${prefs.fontFamily}', serif;
        --font-size: ${prefs.fontSize}px;
        --line-height: ${prefs.lineHeight};
        --margin-horizontal: ${prefs.marginHorizontal}px;
        --text-align: ${prefs.textAlign};
    }
""".trimIndent()

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

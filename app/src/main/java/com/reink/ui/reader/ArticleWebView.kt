package com.reink.ui.reader

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.reink.data.model.ReadingPreferences
import kotlin.math.abs

@Composable
fun ArticleWebView(
    contentHtml: String,
    preferences: ReadingPreferences,
    verticalInsetPx: Int = 56,
    onLinkTapped: (String) -> Unit,
    currentPage: Int = 0,
    onPageCountChanged: (Int) -> Unit = {},
    onPageTurn: (Int) -> Unit = {},
    onContentTapped: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseCss = remember {
        context.assets.open("css/article.css").bufferedReader().readText()
    }
    // Build HTML without the overlay height — that gets injected via JS
    val cssOverrides = buildCssOverrides(preferences)
    val wrappedHtml = wrapHtml(contentHtml, baseCss, cssOverrides)
    val isPaginated = preferences.paginationMode == "paginated"

    val currentOnLinkTapped by rememberUpdatedState(onLinkTapped)
    val currentOnPageCountChanged by rememberUpdatedState(onPageCountChanged)
    val currentPageState by rememberUpdatedState(currentPage)
    val currentIsPaginated by rememberUpdatedState(isPaginated)
    val currentOnPageTurn by rememberUpdatedState(onPageTurn)
    val currentOnContentTapped by rememberUpdatedState(onContentTapped)
    val currentVerticalInsetPx by rememberUpdatedState(verticalInsetPx)

    var lastLoadedHtml by remember { mutableStateOf("") }
    var lastInsetPx by remember { mutableStateOf(verticalInsetPx) }

    // Hold a reference to the WebView for imperative updates
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Push HTML changes to the WebView imperatively — AndroidView's update
    // block doesn't rerun when only rememberUpdatedState values change
    androidx.compose.runtime.LaunchedEffect(wrappedHtml) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (wrappedHtml != lastLoadedHtml) {
            wv.loadDataWithBaseURL("file:///android_asset/", wrappedHtml, "text/html", "UTF-8", null)
            lastLoadedHtml = wrappedHtml
        }
    }

    // Push page changes
    androidx.compose.runtime.LaunchedEffect(currentPage, isPaginated) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isPaginated && wrappedHtml == lastLoadedHtml) {
            wv.evaluateJavascript(
                "(function(){var c=document.getElementById('col-wrapper')||document.body;if(c)c.scrollLeft=${currentPage}*document.documentElement.clientWidth;})();",
                null,
            )
        }
    }

    // Push overlay height changes via JS (no reload needed)
    androidx.compose.runtime.LaunchedEffect(verticalInsetPx) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (verticalInsetPx != lastInsetPx) {
            lastInsetPx = verticalInsetPx
            wv.evaluateJavascript(
                "(function(){var h=document.documentElement;if(h)h.style.setProperty('--reader-overlay-height','${verticalInsetPx}px');})();",
                null,
            )
        }
    }

    AndroidView(
        factory = { context ->
            @SuppressLint("ClickableViewAccessibility")
            fun createWebView(): WebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false

                addJavascriptInterface(
                    PageBridge(
                        onPageCount = { count ->
                            currentOnPageCountChanged(count)
                        },
                        onContentTapped = {
                            currentOnContentTapped()
                        },
                    ),
                    "ReInk",
                )

                // Swipe detection directly on the WebView — avoids
                // Compose/AndroidView touch boundary issues.
                // Once movement exceeds SWIPE_SLOP, we send CANCEL to the
                // WebView to abort text selection / long-press, then detect
                // the full swipe on ACTION_UP.
                val swipeSlop = 20f
                var downX = 0f
                var downY = 0f
                var swiping = false
                setOnTouchListener { view, event ->
                    if (!currentIsPaginated) return@setOnTouchListener false

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            swiping = false
                            // Let WebView see the down event (for link detection)
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!swiping && (abs(event.x - downX) > swipeSlop ||
                                        abs(event.y - downY) > swipeSlop)) {
                                swiping = true
                                // Cancel WebView's touch tracking (text selection)
                                val cancel = MotionEvent.obtain(event)
                                cancel.action = MotionEvent.ACTION_CANCEL
                                view.onTouchEvent(cancel)
                                cancel.recycle()
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!swiping) {
                                // Small movement = tap — let WebView handle for links
                                false
                            } else {
                                val dx = event.x - downX
                                val dy = event.y - downY
                                if (abs(dx) > 80 || abs(dy) > 80) {
                                    if (abs(dx) > abs(dy)) {
                                        if (dx < 0) currentOnPageTurn(1) else currentOnPageTurn(-1)
                                    } else {
                                        if (dy < 0) currentOnPageTurn(1) else currentOnPageTurn(-1)
                                    }
                                }
                                true
                            }
                        }
                        else -> false
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        android.util.Log.d(
                            "ReInk",
                            "JS [${msg.messageLevel()}] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})",
                        )
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        currentOnLinkTapped(url)
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view ?: return
                        if (!currentIsPaginated) return
                        view.evaluateJavascript(PAGINATION_SETUP_JS, null)
                    }
                }

                loadDataWithBaseURL(
                    "file:///android_asset/",
                    wrappedHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
                lastLoadedHtml = wrappedHtml
            }
            createWebView().also { webViewRef = it }
        },
        update = { webView ->
            webViewRef = webView
        },
        modifier = modifier,
    )
}

/**
 * Pagination setup JS. Runs in onPageFinished.
 *
 * Column math:
 *   column-width = vw - 2*margin  (content per page)
 *   column-gap   = 2*margin       (right margin + left margin between pages)
 *   body padding = margin left/right + fitted top/bottom space
 *   Page stride  = column-width + gap = vw
 *
 * Key: body handles outer spacing; #col-wrapper is the column container.
 * We page by setting scrollLeft on #col-wrapper.
 */
private const val PAGINATION_SETUP_JS = """
(function() {
    var h = document.documentElement;
    h.style.height = '100%';
    h.style.overflow = 'hidden';
    h.style.margin = '0';
    h.style.padding = '0';
    h.style.backgroundColor = 'red';

    var b = document.body;
    var c = document.getElementById('col-wrapper');
    if (!b || !c) return;

    var vh = h.clientHeight;
    var vw = h.clientWidth;
    var progressBarHeight = 6;
    var measuredOverlayInset = parseFloat(
        getComputedStyle(h).getPropertyValue('--reader-overlay-height')
    ) || 56;
    var dpr = window.devicePixelRatio || 1;
    var extraVerticalInset = measuredOverlayInset / dpr;
    var basePad = Math.max(0, extraVerticalInset);
    var bottomSafetyInset = Math.max(0, progressBarHeight - basePad);
    var margin = parseInt(
        getComputedStyle(h).getPropertyValue('--margin-horizontal')
    ) || 16;
    var colWidth = vw - 2 * margin;
    var colGap = 2 * margin;
    var contentHeight = 0;

    b.style.margin = '0';
    b.style.setProperty('background-color', '#ffffff', 'important');
    b.style.position = 'fixed';
    b.style.top = '0';
    b.style.right = '0';
    b.style.bottom = '0';
    b.style.left = '0';
    b.style.width = 'auto';
    b.style.boxSizing = 'border-box';
    b.style.paddingRight = margin + 'px';
    b.style.paddingLeft = margin + 'px';
    b.style.overflow = 'hidden';

    c.style.width = '100%';
    c.style.margin = '0';
    c.style.padding = '0';
    c.style.columnFill = 'auto';
    c.style.webkitColumnFill = 'auto';
    c.style.overflow = 'hidden';

    var media = c.querySelectorAll('img, video, iframe, figure');
    for (var i = 0; i < media.length; i++) {
        media[i].style.breakInside = 'avoid';
    }

    function syncViewportMetrics() {
        vh = h.clientHeight;
        vw = h.clientWidth;
        colWidth = vw - 2 * margin;
        colGap = 2 * margin;
        c.style.columnWidth = colWidth + 'px';
        c.style.webkitColumnWidth = colWidth + 'px';
        c.style.columnGap = colGap + 'px';
        c.style.webkitColumnGap = colGap + 'px';
    }

    function applyVerticalPadding(topPad, bottomPad) {
        b.style.paddingTop = topPad + 'px';
        b.style.paddingBottom = bottomPad + 'px';
        var bodyHeight = b.clientHeight || vh;
        contentHeight = Math.max(0, bodyHeight - topPad - bottomPad);
        c.style.height = contentHeight + 'px';
        for (var i = 0; i < media.length; i++) {
            media[i].style.maxHeight = contentHeight + 'px';
        }
    }

    syncViewportMetrics();
    applyVerticalPadding(basePad, basePad + bottomSafetyInset);

    function measureFirstColumnGaps() {
        var cRect = c.getBoundingClientRect();
        var colLeft = cRect.left;
        var colRight = colLeft + colWidth;
        var colTop = cRect.top;
        var colBottom = colTop + contentHeight;
        var topMost = Infinity;
        var bottomMost = -Infinity;

        function considerRect(rect) {
            if (rect.width < 1 || rect.height < 1) return;
            if (rect.right <= colLeft + 0.5 || rect.left >= colRight - 0.5) return;

            var clippedTop = Math.max(rect.top, colTop);
            var clippedBottom = Math.min(rect.bottom, colBottom);
            if (clippedBottom - clippedTop < 1) return;

            if (clippedTop < topMost) topMost = clippedTop;
            if (clippedBottom > bottomMost) bottomMost = clippedBottom;
        }

        function consumeRects(rectList) {
            for (var i = 0; i < rectList.length; i++) {
                considerRect(rectList[i]);
            }
        }

        var tw = document.createTreeWalker(
            c,
            NodeFilter.SHOW_TEXT,
            {
                acceptNode: function(node) {
                    return node.nodeValue && /\S/.test(node.nodeValue)
                        ? NodeFilter.FILTER_ACCEPT
                        : NodeFilter.FILTER_REJECT;
                }
            }
        );
        var textNode;
        while ((textNode = tw.nextNode())) {
            var tr = document.createRange();
            tr.selectNodeContents(textNode);
            consumeRects(tr.getClientRects());
        }

        var mediaLike = c.querySelectorAll('img, video, iframe, figure, svg, table, pre, blockquote, hr');
        for (var m = 0; m < mediaLike.length; m++) {
            consumeRects(mediaLike[m].getClientRects());
        }

        if (!isFinite(topMost) || !isFinite(bottomMost)) return null;
        return {
            top: Math.max(0, topMost - colTop),
            bottom: Math.max(0, colBottom - bottomMost)
        };
    }

    function rebalanceVerticalPadding() {
        var gaps = measureFirstColumnGaps();
        if (!gaps) return;
        var delta = (gaps.bottom - gaps.top) / 2;
        if (Math.abs(delta) < 0.5) return;

        var minTopPad = basePad;
        var minBottomPad = basePad + bottomSafetyInset;
        var topPad = basePad + delta;
        var bottomPad = (basePad + bottomSafetyInset) - delta;
        if (topPad < 0) {
            bottomPad += topPad;
            topPad = 0;
        }
        if (bottomPad < minBottomPad) {
            topPad -= (minBottomPad - bottomPad);
            bottomPad = minBottomPad;
        }
        if (topPad < minTopPad) topPad = minTopPad;

        applyVerticalPadding(topPad, bottomPad);
    }

    var lastReportedPageCount = -1;
    function finalizePagination() {
        syncViewportMetrics();
        applyVerticalPadding(basePad, basePad + bottomSafetyInset);
        rebalanceVerticalPadding();
        var sw = c.scrollWidth;
        var pageCount = Math.max(1, Math.round(sw / vw));
        if (pageCount !== lastReportedPageCount) {
            lastReportedPageCount = pageCount;
            ReInk.reportPageCount(pageCount);
        }
    }

    requestAnimationFrame(function() {
        setTimeout(finalizePagination, 50);
        setTimeout(finalizePagination, 250);
    });
})();
"""

private class PageBridge(
    private val onPageCount: (Int) -> Unit,
    private val onContentTapped: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun reportPageCount(count: Int) {
        android.util.Log.d("ReInk", "PageBridge: reportPageCount=$count")
        mainHandler.post {
            onPageCount(count)
        }
    }

    @JavascriptInterface
    fun onContentTapped() {
        mainHandler.post {
            onContentTapped.invoke()
        }
    }

    @JavascriptInterface
    fun debug(msg: String) {
        android.util.Log.d("ReInk", "PageBridge: $msg")
    }
}

private fun buildCssOverrides(prefs: ReadingPreferences): String {
    val rootVars = """
        :root {
            --font-family: '${prefs.fontFamily}', serif;
            --font-size: ${prefs.fontSize}px;
            --line-height: ${prefs.lineHeight};
            --margin-horizontal: ${prefs.marginHorizontal}px;
            --margin-vertical: ${prefs.marginVertical}px;
            --text-align: ${prefs.textAlign};
            --reader-overlay-height: 56px;
        }
    """.trimIndent()

    if (prefs.paginationMode != "paginated") return rootVars

    val paginationCss = """
        html {
            margin: 0;
            padding: 0;
            height: 100%;
            overflow: hidden;
        }
        body {
            margin: 0;
            min-height: 100%;
            background: #ffffff !important;
            overflow: hidden;
        }
        #col-wrapper {
            margin: 0;
            padding: 0;
            -webkit-column-fill: auto;
            column-fill: auto;
        }
        #col-wrapper img, #col-wrapper video, #col-wrapper iframe, #col-wrapper figure {
            break-inside: avoid;
        }
    """.trimIndent()

    return "$rootVars\n$paginationCss"
}

private fun wrapHtml(content: String, baseCss: String, cssOverrides: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>$baseCss</style>
        <style>$cssOverrides</style>
    </head>
    <body>
        <div id="col-wrapper">
            $content
        </div>
        <script>
        document.addEventListener('click', function(e) {
            if (e.target.closest('a')) return;
            if (window.ReInk) ReInk.onContentTapped();
        });
        </script>
    </body>
    </html>
""".trimIndent()

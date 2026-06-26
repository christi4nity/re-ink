package com.reink.ui.reader

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
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
                "(function(){if(window.ReInkScrollToPage){window.ReInkScrollToPage(${currentPage});return;}var c=document.getElementById('col-wrapper')||document.body;if(c)c.scrollLeft=${currentPage}*document.documentElement.clientWidth;})();",
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
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                settings.textZoom = 100
                settings.defaultTextEncodingName = "UTF-8"
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

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
 *   body margin = user margin
 *   glyph safety inset = inner wrapper padding, so glyph overhangs don't clip
 *   column-width = vw - 2*body margin - 2*glyph safety inset  (content per page)
 *   column-gap   = 2*body margin + 2*glyph safety inset       (space between pages)
 *   Page stride  = actual wrapper content width + column gap
 *
 * Key: body handles visible page margins; #col-wrapper owns the column layout
 * and keeps a small per-page glyph safety inset inside its clipping edge.
 * We page by setting scrollLeft on #col-wrapper.
 */
private const val PAGINATION_SETUP_JS = """
(function() {
    var h = document.documentElement;
    h.style.height = '100%';
    h.style.overflow = 'hidden';
    h.style.margin = '0';
    h.style.padding = '0';
    h.style.backgroundColor = '#ffffff';

    var b = document.body;
    var c = document.getElementById('col-wrapper');
    if (!b || !c) return;

    function getViewportWidth() {
        var visualWidth = window.visualViewport && window.visualViewport.width;
        if (isFinite(visualWidth) && visualWidth > 0) return visualWidth;
        if (window.innerWidth && window.innerWidth > 0) return window.innerWidth;
        return h.clientWidth || 1;
    }

    var vh = h.clientHeight;
    var vw = getViewportWidth();
    var progressBarHeight = 6;
    var measuredOverlayInset = parseFloat(
        getComputedStyle(h).getPropertyValue('--reader-overlay-height')
    ) || 56;
    var dpr = window.devicePixelRatio || 1;
    var extraVerticalInset = measuredOverlayInset / dpr;
    var userVerticalMargin = parseInt(
        getComputedStyle(h).getPropertyValue('--margin-vertical')
    ) || 0;
    var basePad = Math.max(0, extraVerticalInset) + userVerticalMargin;
    var bottomSafetyInset = Math.max(0, progressBarHeight - basePad);
    var margin = parseFloat(
        getComputedStyle(h).getPropertyValue('--margin-horizontal')
    ) || 16;
    var glyphSafetyInset = parseFloat(
        getComputedStyle(h).getPropertyValue('--glyph-safe-inset')
    ) || 8;
    var colWidth = Math.max(1, vw - 2 * margin - 2 * glyphSafetyInset);
    var colGap = 2 * margin + 2 * glyphSafetyInset;
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
    c.style.boxSizing = 'border-box';
    c.style.margin = '0';
    c.style.paddingTop = '0';
    c.style.paddingBottom = '0';
    c.style.paddingLeft = glyphSafetyInset + 'px';
    c.style.paddingRight = glyphSafetyInset + 'px';
    c.style.columnFill = 'auto';
    c.style.webkitColumnFill = 'auto';
    c.style.overflow = 'hidden';

    var media = c.querySelectorAll('img, video, iframe, figure');
    for (var i = 0; i < media.length; i++) {
        media[i].style.breakInside = 'avoid';
    }

    function syncViewportMetrics() {
        vh = h.clientHeight;
        vw = getViewportWidth();
        colWidth = Math.max(1, vw - 2 * margin - 2 * glyphSafetyInset);
        colGap = 2 * margin + 2 * glyphSafetyInset;
        c.style.columnWidth = colWidth + 'px';
        c.style.webkitColumnWidth = colWidth + 'px';
        c.style.columnGap = colGap + 'px';
        c.style.webkitColumnGap = colGap + 'px';
    }

    function getWrapperPadding() {
        var cs = getComputedStyle(c);
        return {
            left: parseFloat(cs.paddingLeft) || 0,
            right: parseFloat(cs.paddingRight) || 0
        };
    }

    function getWrapperContentWidth() {
        var pads = getWrapperPadding();
        var measuredWidth = c.getBoundingClientRect().width - pads.left - pads.right;
        return isFinite(measuredWidth) && measuredWidth > 0 ? measuredWidth : colWidth;
    }

    function getColumnGap() {
        var cs = getComputedStyle(c);
        var measuredColumnGap = parseFloat(cs.columnGap || cs.webkitColumnGap);
        return isFinite(measuredColumnGap) && measuredColumnGap >= 0 ? measuredColumnGap : colGap;
    }

    function getPageStride() {
        return Math.max(1, getWrapperContentWidth() + getColumnGap());
    }

    var lastPage = 0;
    window.ReInkScrollToPage = function(page) {
        lastPage = page;
        var stride = getPageStride();
        var maxScroll = Math.max(0, c.scrollWidth - c.clientWidth);
        var target = Math.max(0, page * stride);
        c.scrollLeft = Math.min(target, maxScroll);
    };

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
        var pads = getWrapperPadding();
        var measuredColWidth = getWrapperContentWidth();
        var colLeft = cRect.left + pads.left;
        var colRight = colLeft + measuredColWidth;
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
        var stride = getPageStride();
        var maxScroll = Math.max(0, c.scrollWidth - c.clientWidth);
        var pageCount = Math.max(1, Math.round(maxScroll / stride) + 1);
        if (pageCount !== lastReportedPageCount) {
            lastReportedPageCount = pageCount;
            ReInk.reportPageCount(pageCount);
        }
    }

    // Re-paginate when the viewport changes size (e.g. device rotation or
    // multi-window resize) and restore the page the reader was on. Without
    // this the column layout keeps its old width and the reader appears to
    // jump to the start.
    var resizeTimer = null;
    function handleViewportResize() {
        if (resizeTimer) clearTimeout(resizeTimer);
        resizeTimer = setTimeout(function() {
            finalizePagination();
            window.ReInkScrollToPage(lastPage);
            // Second pass after the e-ink relayout settles.
            setTimeout(function() { window.ReInkScrollToPage(lastPage); }, 200);
        }, 100);
    }
    window.addEventListener('resize', handleViewportResize);
    if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', handleViewportResize);
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
        // No-op: kept for JS interface compatibility
    }
}

private fun buildCssOverrides(prefs: ReadingPreferences): String {
    val textAlignLast = if (prefs.textAlign == "justify") "left" else "auto"
    val rootVars = """
        :root {
            --font-family: '${prefs.fontFamily}', serif;
            --font-size: ${prefs.fontSize}px;
            --line-height: ${prefs.lineHeight};
            --margin-horizontal: ${prefs.marginHorizontal}px;
            --margin-vertical: ${prefs.marginVertical}px;
            --text-align: ${prefs.textAlign};
            --text-align-last: $textAlignLast;
            --glyph-safe-inset: 8px;
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
            box-sizing: border-box;
            margin: 0;
            padding: 0 var(--glyph-safe-inset);
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
    <html lang="en">
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

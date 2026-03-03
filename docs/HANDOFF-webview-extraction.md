# Handoff: WebView Paid Content Extraction

## What Was Built

A system to extract full paid Substack article content via WebView, bypassing the limitation that RSS feeds and HTTP APIs only return truncated previews for paid posts.

### Architecture

```
RSS Sync → detectTruncation() → contentStatus="truncated"
                                        ↓
Reader opens article → show truncated preview immediately
                                        ↓
Background 1dp WebView loads Substack URL with SID cookie
                                        ↓
JS extraction (5s delay for hydration) → extracts div.body.markup innerHTML
                                        ↓
onContentExtracted() → saves to DB as "extracted" → ArticleWebView re-renders with full content
```

### Files Changed

| File | Change |
|------|--------|
| `data/model/Article.kt` | Added `contentStatus` field with constants: `CONTENT_FULL`, `CONTENT_TRUNCATED`, `CONTENT_EXTRACTED`, `CONTENT_FAILED` |
| `data/local/ArticleEntity.kt` | Added `contentStatus` column + mapping |
| `data/local/ArticleDao.kt` | Added `updateExtractedContent(id, html, status)` and `markFeedArticlesTruncated(feedId)` |
| `data/local/ReInkDatabase.kt` | `MIGRATION_4_5`: adds column + paywall-class backfill. Version bumped to 5 |
| `data/remote/RssFetcher.kt` | `detectTruncation()` checks readMorePattern + paywall class BEFORE `cleanContent()` strips them |
| **`data/remote/WebViewCookieSync.kt`** | **New** — syncs SID from DataStore to CookieManager for `.substack.com` (with `SameSite=None; Domain=.substack.com`) + article's custom domain |
| `data/repository/ArticleRepository.kt` | Added `updateExtractedContent()` and `markFeedArticlesTruncated()` |
| `di/AppModule.kt` | Registered `MIGRATION_4_5` |
| `ui/reader/ArticleWebView.kt` | Added `SubstackWebView` composable + `ExtractionBridge` JS interface + `EXTRACTION_JS`. Has `setAcceptThirdPartyCookies(true)` and `LOAD_NO_CACHE`. |
| `ui/reader/ReaderViewModel.kt` | Added `articleUrl`/`isExtracting`/`extractionFailed` state flows, `onContentExtracted()`, `retryExtraction()`, branching in `loadContent()` |
| `ui/reader/ReaderScreen.kt` | Always shows ArticleWebView; invisible 1dp SubstackWebView runs behind it when extracting. Added `ExtractionFailedBanner`. |

### What Works

- **Truncation detection**: New articles from paid feeds correctly get `contentStatus="truncated"` during RSS sync
- **Extraction pipeline**: The invisible WebView loads the Substack URL, waits for JS hydration, runs extraction JS, finds `div.body.markup`, and returns content
- **Selectors confirmed**: Playwright testing on live Substack pages confirmed `div.body.markup` is the right selector (tested on thefp.com)
- **Background UX**: User sees truncated preview immediately, extraction runs invisibly behind a 1dp WebView
- **DB caching**: Extracted content is saved to DB; reopening skips extraction
- **Retry flow**: Failed extractions show a banner with retry button

## The Blocking Problem: Cookie Delivery Works, But Auth Still Fails

### What Was Proven (Feb 24, 2026)

Through systematic debugging with logcat diagnostics, we proved:

1. **Cookie IS in CookieManager**: `hasSid=true` for all domains (`.substack.com`, `substack.com`, custom domain)
2. **Cookie IS in document.cookie**: JavaScript can read `substack.sid` in the page context
3. **Substack recognizes the user**: The sign-in flow triggers — server generates a JWT with correct `user_id:5266966` and redirects to `/api/v1/sign-in/local/complete?token=...`
4. **Page still shows paywall**: After sign-in redirect, `paywall=true` persists, `div.body.markup` = 3133 chars (truncated preview)

### What Was Tried and Failed

| Attempt | Result |
|---------|--------|
| `setAcceptThirdPartyCookies(true)` | Cookie delivered, but auth still fails |
| `SameSite=None; Secure; Domain=.substack.com` on cookie | No change |
| `LOAD_NO_CACHE` on WebView | No change — ruled out caching |
| Extraction delay 2.5s → 5s | No change — ruled out timing |
| Token-authenticated RSS (`?token=podcastRssToken`) | Returns same truncated content as unauthenticated feed |
| Substack `/api/v1/posts/{slug}` API with SID | Returns truncated `body_html` (2929 chars) regardless of auth |
| Server-rendered HTML with SID cookie (curl) | Same 145KB HTML with/without SID — SSR doesn't include article body |

### Key Evidence (Logcat)

```
CookieSync: substack.com hasSid=true cookieLen=1190
CookieSync: substack.news-items.com hasSid=true cookieLen=1387

Extract onPageFinished url=.../p/something-easily-won hasSid=true cookieLen=1387

# Substack sign-in flow triggers (proves SID is recognized):
Extract onPageFinished url=.../api/v1/sign-in/local/complete?token=eyJ1c2VyX2lkIjo1MjY2OTY2...
Extract onPageFinished url=.../p/something-easily-won  (redirect back)

# But extraction still finds paywall:
Extract JS: hasSid=true paywall=true
Extract JS: "div.body.markup" → 3133 chars
```

### Root Cause Analysis

The WebView approach has a fundamental issue with Substack's custom-domain auth. The SID cookie is delivered, Substack even recognizes the user (sign-in JWT created), but the page never transitions from paywalled to authenticated rendering. Possible causes:

1. **Substack's client-side auth requires additional state** beyond the SID cookie (e.g., localStorage tokens, specific cookie attributes like `HttpOnly` that `setCookie` can't replicate, or a CSRF token)
2. **The 1dp WebView triggers bot/viewport detection** that prevents full rendering
3. **The sign-in redirect loop never completes properly** — the redirect back serves the same paywalled content

### Unexplored Approaches

1. **WebView sign-in flow**: Let the user sign into Substack once in a VISIBLE WebView (not 1dp). The server-set cookies would have proper attributes (`HttpOnly`, correct `Domain`, etc.). This is the most promising approach.

2. **shouldInterceptRequest header injection**: Use `WebViewClient.shouldInterceptRequest()` to manually add `Cookie: substack.sid=...` to every HTTP request header (like the OkHttp interceptor does). This bypasses CookieManager entirely.

3. **Full-size visible WebView**: Test with a normal-sized WebView to rule out viewport/bot detection. If it works at full size, the 1dp approach needs rethinking.

4. **Substack's Cloudflare protection**: The `/api/v1/user/self` endpoint returned 403 even with valid SID. Cloudflare's `__cf_bm` cookie (set with `SameSite=None`) may be required for API auth. The WebView might need to navigate to `substack.com` first to acquire Cloudflare cookies before loading the custom domain.

5. **Two-phase WebView**: First load `substack.com` (to establish proper Cloudflare + session cookies), then navigate to the article URL. This mimics how a real browser session works.

### Debug Logging Still Present

Diagnostic logging is still in both files — should be stripped once the issue is resolved:
- `WebViewCookieSync.kt`: `Log.d` calls for sync status, hasSid checks
- `ArticleWebView.kt`: `onPageFinished` cookie checks, `EXTRACTION_JS` diagnostic console.logs

### Test Procedure

1. Delete a paid feed, re-add it (to get fresh `contentStatus="truncated"` articles)
2. Open a truncated article
3. Check logcat: `adb logcat -s "ReInk:D" | grep -i "extract"`
4. Success = extracted char count >> 3133, `paywall=false`

# Reader Pagination Design

## Summary

Add paginated reading mode to the article reader using CSS multi-column layout. Users can switch between scroll and paginated modes in settings. Paginated mode supports swipe (horizontal and vertical) and volume button navigation.

## Approach: CSS Multi-Column Pagination

The WebView lays out content in horizontal columns, each exactly one viewport wide. "Turning a page" scrolls horizontally by one viewport width. No animations (e-ink optimized).

```css
body {
    column-width: 100vw;
    column-gap: 0;
    height: 100vh;
    overflow: hidden;
}
```

Page N = `scrollX = N * viewportWidth`. Total pages = `scrollWidth / clientWidth`.

## Changes

### 1. ReadingPreferences model
Add `paginationMode: String` field. Values: `"scroll"` (default) | `"paginated"`.

### 2. PreferencesRepository
Add DataStore key `KEY_PAGINATION_MODE` and wire it through `observeReadingPreferences()` / `updateReadingPreferences()`.

### 3. ArticleWebView — CSS injection
When `paginationMode == "paginated"`, `buildCssOverrides()` adds column-based CSS to constrain content to viewport-sized pages.

### 4. ArticleWebView — JavaScript bridge
Enable JavaScript minimally. Add `@JavascriptInterface` to receive page count after layout. Use `evaluateJavascript()` to compute `scrollWidth / clientWidth` and to scroll to a specific page via `scrollTo(pageIndex * clientWidth, 0)`.

### 5. ArticleWebView — gesture navigation
Overlay a Compose gesture detector on the WebView in paginated mode. Swipe left/right and up/down both map to next/prev page. In scroll mode, the WebView handles touch natively.

### 6. Volume key navigation in reader
Extend `volumeKeyEvents` SharedFlow to reach `ReaderScreen` via `ReInkNavGraph`. Volume down = next page, volume up = previous page (paginated mode only).

### 7. Settings UI
Add "Reading mode" picker to `ReadingPreferencesSection` — two-option picker (Scroll / Paginated) using the same pattern as AlignmentPicker.

## Not included
- No page indicator (page count display)
- No page turn animations

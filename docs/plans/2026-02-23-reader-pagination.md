# Reader Pagination Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add paginated reading mode using CSS multi-column layout with swipe and volume key navigation.

**Architecture:** CSS columns split article content into viewport-sized pages in the WebView. A Compose gesture overlay intercepts swipes. A minimal JS bridge computes page count and handles `scrollTo`. A new `paginationMode` preference toggles between scroll and paginated modes.

**Tech Stack:** WebView + CSS columns, `@JavascriptInterface`, Compose gesture detection, DataStore preferences.

---

### Task 1: Add `paginationMode` to ReadingPreferences model

**Files:**
- Modify: `app/src/main/java/com/reink/data/model/ReadingPreferences.kt`

**Step 1: Add field to data class**

```kotlin
data class ReadingPreferences(
    val fontFamily: String = "Literata",
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val marginHorizontal: Int = 16,
    val textAlign: String = "left",
    val paginationMode: String = "scroll",
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/reink/data/model/ReadingPreferences.kt
git commit -m "feat: add paginationMode field to ReadingPreferences"
```

---

### Task 2: Wire `paginationMode` through PreferencesRepository

**Files:**
- Modify: `app/src/main/java/com/reink/data/repository/PreferencesRepository.kt`

**Step 1: Add DataStore key and read/write logic**

In the companion object, add:
```kotlin
private val KEY_PAGINATION_MODE = stringPreferencesKey("pagination_mode")
```

In `observeReadingPreferences()`, add to the `ReadingPreferences(...)` constructor:
```kotlin
paginationMode = prefs[KEY_PAGINATION_MODE] ?: "scroll",
```

In `updateReadingPreferences()`, add to the `edit` block:
```kotlin
store[KEY_PAGINATION_MODE] = prefs.paginationMode
```

**Step 2: Build to verify**

Run: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/reink/data/repository/PreferencesRepository.kt
git commit -m "feat: persist paginationMode in DataStore"
```

---

### Task 3: Add pagination CSS injection to ArticleWebView

**Files:**
- Modify: `app/src/main/java/com/reink/ui/reader/ArticleWebView.kt`

**Step 1: Update `buildCssOverrides` to inject column CSS when paginated**

After the existing `:root { ... }` block, conditionally append:

```kotlin
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
```

**Step 2: Build to verify**

Run: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/reink/ui/reader/ArticleWebView.kt
git commit -m "feat: inject pagination CSS in column mode"
```

---

### Task 4: Add JavaScript bridge for page navigation

**Files:**
- Modify: `app/src/main/java/com/reink/ui/reader/ArticleWebView.kt`

**Step 1: Create a JavascriptInterface class and page state**

Add a `PageBridge` class that receives the total page count from JS:

```kotlin
import android.webkit.JavascriptInterface

private class PageBridge(
    private val onPageCount: (Int) -> Unit,
) {
    @JavascriptInterface
    fun reportPageCount(count: Int) {
        onPageCount(count)
    }
}
```

**Step 2: Enable JS and add the bridge when paginated**

In the `factory` lambda of `AndroidView`, when `preferences.paginationMode == "paginated"`:
- Set `settings.javaScriptEnabled = true`
- Add the JS interface: `addJavascriptInterface(PageBridge(onPageCount), "ReInk")`
- In `onPageFinished`, evaluate JS to compute page count and call `scrollTo` for the current page:

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    if (prefs.paginationMode == "paginated") {
        view?.evaluateJavascript(
            """
            (function() {
                var pageCount = Math.max(1, Math.ceil(document.body.scrollWidth / window.innerWidth));
                ReInk.reportPageCount(pageCount);
            })();
            """.trimIndent(),
            null,
        )
    }
}
```

**Step 3: Add `goToPage` helper**

A function that calls `evaluateJavascript("window.scrollTo(page * window.innerWidth, 0)", null)` on the WebView reference.

**Step 4: Expose callbacks from `ArticleWebView` composable**

Update the composable signature to accept:
- `onPageCountChanged: (Int) -> Unit = {}`
- `onPageRequested: SharedFlow<Int>` (or similar mechanism for the parent to request page changes)

Actually, simpler approach: have `ArticleWebView` accept `currentPage: Int` as a parameter. When it changes, call `evaluateJavascript` to scroll. Have `onPageCountChanged` callback to report total pages up.

```kotlin
@Composable
fun ArticleWebView(
    contentHtml: String,
    preferences: ReadingPreferences,
    onLinkTapped: (String) -> Unit,
    currentPage: Int = 0,
    onPageCountChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
)
```

In the `update` block, when paginated, call:
```kotlin
if (preferences.paginationMode == "paginated") {
    webView.evaluateJavascript(
        "window.scrollTo(${currentPage} * window.innerWidth, 0);",
        null,
    )
}
```

**Step 5: Build to verify**

Run: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/reink/ui/reader/ArticleWebView.kt
git commit -m "feat: add JS bridge for page count and navigation"
```

---

### Task 5: Add swipe gesture overlay to ReaderScreen

**Files:**
- Modify: `app/src/main/java/com/reink/ui/reader/ReaderScreen.kt`

**Step 1: Add page state management**

In `ReaderScreen`, add state for pagination:

```kotlin
var currentPage by remember { mutableIntStateOf(0) }
var totalPages by remember { mutableIntStateOf(1) }
```

**Step 2: Wrap the `ArticleWebView` with gesture detection when paginated**

Use `Modifier.pointerInput` to detect horizontal and vertical swipes. On swipe left or swipe up → next page. On swipe right or swipe down → previous page. Threshold: 50dp of drag distance.

```kotlin
val isPaginated = state.preferences.paginationMode == "paginated"

val gestureModifier = if (isPaginated) {
    Modifier.pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            val (dx, dy) = dragAmount
            // Determine dominant axis
            if (abs(dx) > abs(dy)) {
                // Horizontal swipe
                if (dx < -50f && currentPage < totalPages - 1) {
                    currentPage++
                } else if (dx > 50f && currentPage > 0) {
                    currentPage--
                }
            } else {
                // Vertical swipe
                if (dy < -50f && currentPage < totalPages - 1) {
                    currentPage++
                } else if (dy > 50f && currentPage > 0) {
                    currentPage--
                }
            }
        }
    }
} else {
    Modifier
}
```

Apply this modifier on a `Box` wrapping the `ArticleWebView`.

**Step 3: Pass page state to ArticleWebView**

```kotlin
ArticleWebView(
    contentHtml = state.contentHtml,
    preferences = state.preferences,
    onLinkTapped = { url -> pendingLinkUrl = url },
    currentPage = currentPage,
    onPageCountChanged = { totalPages = it },
    modifier = Modifier.fillMaxSize().padding(innerPadding),
)
```

**Step 4: Reset page on new article**

```kotlin
LaunchedEffect(state.contentHtml) {
    currentPage = 0
}
```

**Step 5: Build to verify**

Run: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/reink/ui/reader/ReaderScreen.kt
git commit -m "feat: add swipe gesture navigation for paginated mode"
```

---

### Task 6: Wire volume keys to ReaderScreen

**Files:**
- Modify: `app/src/main/java/com/reink/ui/navigation/ReInkNavGraph.kt`
- Modify: `app/src/main/java/com/reink/ui/reader/ReaderScreen.kt`

**Step 1: Pass `volumeKeyEvents` to ReaderScreen in nav graph**

In `ReInkNavGraph.kt`, update the Reader composable:

```kotlin
composable(
    route = Screen.Reader.route,
    arguments = listOf(
        navArgument("itemType") { type = NavType.StringType },
        navArgument("itemId") { type = NavType.LongType },
    ),
) {
    ReaderScreen(
        onBack = { navController.popBackStack() },
        volumeKeyEvents = volumeKeyEvents,
    )
}
```

**Step 2: Accept and handle volume keys in ReaderScreen**

Update `ReaderScreen` signature:
```kotlin
fun ReaderScreen(
    onBack: () -> Unit = {},
    volumeKeyEvents: SharedFlow<VolumeKey> = MutableSharedFlow(),
    viewModel: ReaderViewModel = hiltViewModel(),
)
```

Add a `LaunchedEffect` to collect volume events (only when paginated):
```kotlin
LaunchedEffect(isPaginated) {
    if (!isPaginated) return@LaunchedEffect
    volumeKeyEvents.collect { key ->
        when (key) {
            VolumeKey.DOWN -> if (currentPage < totalPages - 1) currentPage++
            VolumeKey.UP -> if (currentPage > 0) currentPage--
        }
    }
}
```

**Step 3: Build to verify**

Run: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/reink/ui/navigation/ReInkNavGraph.kt app/src/main/java/com/reink/ui/reader/ReaderScreen.kt
git commit -m "feat: wire volume keys to reader for paginated navigation"
```

---

### Task 7: Add reading mode picker to Settings UI

**Files:**
- Modify: `app/src/main/java/com/reink/ui/settings/ReadingPreferencesSection.kt`

**Step 1: Add a ReadingModePicker**

Add a two-option picker (same pattern as `AlignmentPicker`) with options "Scroll" and "Paginated":

```kotlin
private val readingModeOptions = listOf(
    "scroll" to "Scroll",
    "paginated" to "Paginated",
)
```

Create a `ReadingModePicker` composable using the same `Surface` + `Row` pattern as `AlignmentPicker`. Place it in the `ReadingPreferencesSection` column, just before the font dropdown.

**Step 2: Wire the picker**

```kotlin
ReadingModePicker(
    selected = preferences.paginationMode,
    onSelected = {
        onPreferencesChanged(preferences.copy(paginationMode = it))
    },
)
```

**Step 3: Build to verify**

Run: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Install on device and test**

Run: `JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew installDebug`

Manual test:
1. Open Settings, verify Scroll/Paginated picker appears
2. Select "Paginated", open an article — content should be in page columns
3. Swipe left/right, up/down — should turn pages
4. Press volume up/down — should turn pages
5. Switch back to "Scroll" — normal scrolling behavior returns

**Step 5: Commit**

```bash
git add app/src/main/java/com/reink/ui/settings/ReadingPreferencesSection.kt
git commit -m "feat: add reading mode picker to settings"
```

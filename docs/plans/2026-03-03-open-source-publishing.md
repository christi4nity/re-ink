# Open-Source Publishing Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Clean up re:ink and publish as a GPL v3 open-source project on GitHub.

**Architecture:** No structural changes — this is purely cleanup (remove dead code, strip debug logging) plus adding LICENSE, README, and hardened .gitignore. Then push to a new public GitHub repo.

**Tech Stack:** Kotlin/Android, git, gh CLI

---

### Task 1: Delete SubstackContentExtractor.kt

**Files:**
- Delete: `app/src/main/java/com/reink/data/remote/SubstackContentExtractor.kt`

**Step 1: Delete the file**

```bash
rm app/src/main/java/com/reink/data/remote/SubstackContentExtractor.kt
```

**Step 2: Build to confirm nothing depends on it**

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (file was confirmed dead code — never wired into ViewModel or DI)

**Step 3: Commit**

```bash
git add -A && git commit -m "refactor: remove dead SubstackContentExtractor"
```

---

### Task 2: Remove SubstackWebView extraction path from reader

This is the biggest surgery. SubstackWebView in ArticleWebView.kt is wired into ReaderScreen and ReaderViewModel for paid content extraction that doesn't work. Remove the entire code path.

**Files:**
- Modify: `app/src/main/java/com/reink/ui/reader/ArticleWebView.kt`
  - Delete: `SubstackWebView` composable (lines 35-125)
  - Delete: `ExtractionBridge` class (lines 234-265)
  - Delete: Constants `INITIAL_DELAY_MS`, `POLL_INTERVAL_MS`, `MAX_POLLS`, `EXTRACTION_TIMEOUT_MS` (lines 127-130)
  - Delete: `POLL_JS` constant (lines 136-165)
  - Delete: `EXTRACTION_JS` constant (lines 167-232)
- Modify: `app/src/main/java/com/reink/ui/reader/ReaderViewModel.kt`
  - Remove from `ReaderUiState`: `articleUrl`, `substackSid`, `isExtracting`, `extractionFailed`
  - Remove flows: `articleUrl`, `substackSid`, `isExtracting`, `extractionFailed`
  - Remove `titleCardHtml` field (only needed for extraction swap)
  - Remove method: `startWebViewExtraction()`
  - Remove method: `onContentExtracted()`
  - Remove method: `retryExtraction()`
  - Remove the `CONTENT_TRUNCATED`/`CONTENT_FAILED` branch in `loadContent()` (just show content as-is)
  - Remove `ExtractState` data class and `toExtractState()` helper
  - Simplify `uiState` combine to drop extraction state
- Modify: `app/src/main/java/com/reink/ui/reader/ReaderScreen.kt`
  - Remove `SubstackWebView` block (lines 122-133)
  - Remove `ExtractionFailedBanner` usage (lines 154-158)
  - Remove `ExtractionFailedBanner` composable (lines 312-346)
  - Remove unused imports

**Step 1: Remove SubstackWebView, ExtractionBridge, and all extraction JS from ArticleWebView.kt**

Delete everything from the `SubstackWebView` composable through the `ExtractionBridge` class, plus the extraction-related constants (`INITIAL_DELAY_MS` through `EXTRACTION_JS`). Keep `ArticleWebView`, `PageBridge`, `buildCssOverrides`, `wrapHtml`, and `PAGINATION_SETUP_JS`.

**Step 2: Simplify ReaderViewModel.kt**

Strip `ReaderUiState` to:
```kotlin
data class ReaderUiState(
    val title: String = "",
    val contentHtml: String = "",
    val preferences: ReadingPreferences = ReadingPreferences(),
    val isLoading: Boolean = true,
    val savedForLater: Boolean = false,
)
```

Remove `articleUrl`, `substackSid`, `isExtracting`, `extractionFailed` flows.
Remove `titleCardHtml` field. Remove `startWebViewExtraction()`, `onContentExtracted()`, `retryExtraction()`.
Remove `ExtractState` and `toExtractState()`.

Simplify `loadContent()` to always show content directly without the truncated/failed branch:
```kotlin
when (article.contentStatus) {
    Article.CONTENT_TRUNCATED,
    Article.CONTENT_FAILED -> {
        // Previously triggered WebView extraction — removed.
        // Content is shown as-is.
    }
}
```
Actually, just remove the `when` block entirely — the content is already set on the line above.

Simplify `uiState` combine:
```kotlin
val uiState: StateFlow<ReaderUiState> = combine(
    content,
    preferencesRepository.observeReadingPreferences(),
    savedForLater,
) { (title, html), prefs, saved ->
    ReaderUiState(
        title = title,
        contentHtml = html,
        preferences = prefs,
        isLoading = html.isEmpty(),
        savedForLater = saved,
    )
}.stateIn(...)
```

**Step 3: Clean up ReaderScreen.kt**

Remove SubstackWebView block, ExtractionFailedBanner usage, and the composable itself. Remove references to `state.articleUrl`, `state.substackSid`, `state.isExtracting`, `state.extractionFailed`.

**Step 4: Build**

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug
```

**Step 5: Commit**

```bash
git add -A && git commit -m "refactor: remove non-working SubstackWebView extraction path"
```

---

### Task 3: Strip debug logging

Remove all `Log.d`, `Log.w`, `Log.e` calls and unused `import android.util.Log` / `import Log` statements from these files:

**Files:**
- Modify: `app/src/main/java/com/reink/ui/reader/ArticleWebView.kt` — remove `Log.d` in `PageBridge` and `WebChromeClient`s
- Modify: `app/src/main/java/com/reink/data/remote/ArticleExtractor.kt` — remove ~12 Log calls, TAG constant, Log import
- Modify: `app/src/main/java/com/reink/data/remote/WebViewArticleExtractor.kt` — remove ~7 Log calls, TAG constant, Log import. Keep the `WebChromeClient` console bridge (it relays JS errors back to logcat — useful for debugging Readability.js failures)
- Modify: `app/src/main/java/com/reink/data/repository/ReadLaterRepository.kt` — remove 2 Log calls, Log import
- Modify: `app/src/main/java/com/reink/data/repository/EmailSyncRepository.kt` — remove ~15 Log calls, TAG constant, Log import
- Modify: `app/src/main/java/com/reink/data/email/ImapEmailContentSource.kt` — remove ~8 Log calls, TAG constant, Log import

**Approach:** For each file, remove every line containing `Log.d(`, `Log.w(`, `Log.e(`. Then remove the `import android.util.Log` or `import Log` if no Log calls remain. Remove `private const val TAG = "..."` constants that are now unused.

**Step 1: Strip logging from all 6 files**

**Step 2: Build**

```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add -A && git commit -m "refactor: strip debug logging from production code"
```

---

### Task 4: Harden .gitignore

**Files:**
- Modify: `.gitignore`

**Step 1: Add safety entries**

Append to `.gitignore`:
```
# Signing
*.jks
*.keystore

# Build outputs
*.apk
*.aab

# OS
.DS_Store

# Tooling artifacts
.playwright-mcp/
*.log

# Internal docs (plans, handoffs)
docs/
```

**Step 2: Commit**

```bash
git add .gitignore && git commit -m "chore: harden .gitignore for open-source publishing"
```

---

### Task 5: Add LICENSE (GPL v3)

**Files:**
- Create: `LICENSE`

**Step 1: Download GPL v3 text**

```bash
curl -sL https://www.gnu.org/licenses/gpl-3.0.txt > LICENSE
```

**Step 2: Commit**

```bash
git add LICENSE && git commit -m "chore: add GPL v3 license"
```

---

### Task 6: Add README.md

**Files:**
- Create: `README.md`

**Step 1: Write README**

Content should include:
- Project name and one-line description
- Feature list (RSS feeds with paid content via cookie auth, email-based ingestion, e-ink-optimized reader with customizable typography, paginated/scroll modes, read-later queue with Readability.js extraction)
- Build instructions (JDK 17, `./gradlew assembleDebug`, device install command)
- Brief architecture note pointing to CLAUDE.md
- License section

**Step 2: Commit**

```bash
git add README.md && git commit -m "docs: add README"
```

---

### Task 7: Create GitHub repo and push

**Step 1: Create public repo**

```bash
gh repo create re-ink --public --description "E-ink optimized Substack reader for Android" --source=. --push
```

**Step 2: Verify**

```bash
gh repo view --web
```

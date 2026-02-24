# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

re:ink is an Android app for reading Substack newsletters on e-ink devices (Boox NoteAir5C). It fetches RSS feeds (including paid content via cookie auth), renders articles in a clean WebView reader with customizable typography, and supports a read-later queue for linked articles.

## Build Commands

```bash
./gradlew assembleDebug      # Build debug APK
./gradlew assembleRelease    # Build release APK (ProGuard minified)
./gradlew installDebug       # Install debug APK on connected device
./gradlew build              # Full build
```

For Boox device testing:
```bash
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home" ./gradlew installDebug
```

No tests exist yet.

## Tech Stack

- **Language**: Kotlin, targeting JVM 17
- **UI**: Jetpack Compose with Material 3
- **DI**: Hilt (with KSP for annotation processing)
- **Database**: Room (articles, feeds, read-later queue)
- **Network**: OkHttp + RSS-Parser (com.prof18.rssparser)
- **Content Extraction**: Readability4J (for read-later links)
- **Preferences**: DataStore
- **Background Sync**: WorkManager (4-hour periodic, wifi-only)
- **Navigation**: Navigation Compose (3-tab bottom nav + reader detail)
- **Min SDK**: 28 / **Target SDK**: 35
- **Version catalog**: `gradle/libs.versions.toml`

## Architecture

Clean Architecture with MVVM. Single-module app (`app/`).

### Layer structure (all under `com.reink`):

- **`data/model/`** — Domain models: Feed, Article, ReadLaterItem, ReadingPreferences, FetchStatus
- **`data/local/`** — Room database, entities (FeedEntity, ArticleEntity, ReadLaterEntity), DAOs
- **`data/remote/`** — RssFetcher (RSS-Parser wrapper), ArticleExtractor (Readability4J), SubstackAuthInterceptor
- **`data/repository/`** — FeedRepository, ArticleRepository, ReadLaterRepository, PreferencesRepository
- **`di/`** — Hilt modules: AppModule (DB, DAOs, DataStore, WorkManager), NetworkModule (OkHttp, RssParser)
- **`sync/`** — FeedSyncWorker, ReadLaterSyncWorker, SyncScheduler (WorkManager periodic + immediate)
- **`ui/feed/`** — FeedScreen, FeedViewModel, ArticleListItem (article list with date grouping, filtering, pagination)
- **`ui/reader/`** — ReaderScreen, ReaderViewModel, ArticleWebView (WebView with CSS variable injection)
- **`ui/readlater/`** — ReadLaterScreen, ReadLaterViewModel, ReadLaterListItem
- **`ui/settings/`** — SettingsScreen, SettingsViewModel, AuthSection, FeedManagementSection, ReadingPreferencesSection
- **`ui/components/`** — EInkComponents, DateHeader, FilterBar (shared UI)
- **`ui/navigation/`** — ReInkNavGraph, Screen sealed class
- **`ui/theme/`** — E-ink optimized theme (grayscale, no ripples, bold typography)

### Key Design Decisions

- **E-ink first**: Grayscale colors, no ripple effects, bold fonts only, no animations
- **Substack auth**: OkHttp interceptor adds `substack.sid` cookie to `*.substack.com` requests
- **RSS content**: Paid Substack RSS feeds return full HTML in `content:encoded` when authenticated
- **Read-later**: Link taps in reader are intercepted and saved; Readability4J extracts clean content
- **WebView reader**: `loadDataWithBaseURL("file:///android_asset/", ...)` with CSS variable injection from DataStore preferences
- **Volume keys**: Mapped to page navigation on Feed screen (same pattern as CrosswordInk)
- **Pagination**: `LIMIT n+1` pattern — fetch one extra to detect if next page exists
- **Article cleanup**: Articles older than 90 days deleted on sync

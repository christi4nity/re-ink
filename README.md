# re:ink

An e-ink optimized Substack reader for Android.

Built for the Boox NoteAir5C. A personal project for an audience of one, published under GPL v3 for anyone who finds it useful.

## Features

**Content Sources**
- RSS feed ingestion for Substack publications (including section-level filtering)
- Email-based article ingestion (IMAP) for paid content that RSS can't deliver
- Cloud queue for saving articles from any device (share URL + QR code)

**Reader**
- Clean WebView reader with customizable typography (font family, size, line height, margins, alignment)
- Paginated and scroll reading modes
- Volume key navigation in paginated mode
- Reading progress bar

**Organization**
- Home tab: unified unread feed across all sources
- Feed tab: per-feed browsing with unread filtering
- Read-later queue with Readability4J extraction (WebView fallback for JS-heavy sites)
- Archive for finished articles
- Long-press actions: archive, delete, unarchive

**Sync**
- Background sync via WorkManager (4-hour periodic, wifi-only)
- Cross-device sync for read/archive state, feeds, and reading preferences
- Substack browser sign-in (WebView) with manual SID fallback

**UI**
- E-ink optimized: grayscale, no animations, bold typography, no ripple effects

## Build

Requires JDK 17, Android SDK, min SDK 28.

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device (JDK 17 required)
JAVA_HOME="/path/to/jdk17" ./gradlew installDebug
```

## Architecture

Single-module app, MVVM with Clean Architecture. See [CLAUDE.md](CLAUDE.md) for full architecture documentation including layer structure, key design decisions, and package layout.

## Tech Stack

Kotlin, Jetpack Compose, Material 3, Room, Hilt, OkHttp, WorkManager, Readability4J

## License

[GPL v3](LICENSE)

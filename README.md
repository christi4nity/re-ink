# re:ink

An e-ink optimized Substack reader for Android.

Built for Boox e-ink devices. A personal project published under GPL v3 for anyone who finds it useful.

## Install

Grab the latest APK from [GitHub Releases](https://github.com/christi4nity/re-ink/releases/latest) and sideload it. The app checks for updates automatically and will prompt you to install when a new version is available.

**Requirements:** Android 9+ (API 28), e-ink device recommended but not required.

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

**Sync & Updates**
- Background sync via WorkManager (4-hour periodic, wifi-only)
- Cross-device sync for read/archive state, feeds, and reading preferences
- Substack browser sign-in (WebView) with manual SID fallback
- Auto-update: checks GitHub Releases daily, downloads in background, notifies when ready

**UI**
- E-ink optimized: grayscale, no animations, bold typography, no ripple effects

## Build from source

Requires JDK 17, Android SDK, min SDK 28.

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
JAVA_HOME="/path/to/jdk17" ./gradlew installDebug
```

### Release builds

Release APKs are built automatically by GitHub Actions when a version tag is pushed:

```bash
git tag v1.1.0
git push origin v1.1.0
```

This triggers a workflow that builds a signed APK and publishes it as a GitHub Release.

## Architecture

Single-module app, MVVM with Clean Architecture. See [CLAUDE.md](CLAUDE.md) for full architecture documentation including layer structure, key design decisions, and package layout.

## Tech Stack

Kotlin, Jetpack Compose, Material 3, Room, Hilt, OkHttp, WorkManager, Readability4J

## License

[GPL v3](LICENSE)

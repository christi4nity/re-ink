# re:ink

An e-ink optimized Substack reader for Android.

Built for the Boox NoteAir5C. A personal project for an audience of one, published under GPL v3 for anyone who finds it useful.

## Features

- Fetches RSS feeds from Substack publications, including paid content via cookie auth
- Email-based article ingestion (IMAP) for paid content that RSS can't deliver
- Clean WebView reader with customizable typography (font, size, line height, margins, alignment)
- Paginated and scroll reading modes
- Read-later queue with Readability.js extraction for linked articles
- E-ink optimized UI: grayscale, no animations, bold typography
- Background sync via WorkManager (4-hour periodic, wifi-only)
- Volume key navigation in paginated mode

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

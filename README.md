# re:ink

An e-ink optimized Substack reader for Android.

Built for Boox e-ink devices. A personal project published under GPL v3 for anyone who finds it useful.

## Install

Grab the latest APK from [GitHub Releases](https://github.com/christi4nity/re-ink/releases/latest) and sideload it. The app checks for updates automatically and will prompt you when a new version is available.

**Requirements:** Android 9+ (API 28). Optimized for e-ink but works on any Android device.

## Getting started

### Adding feeds

1. Open **Settings** (bottom nav)
2. Under **Feeds**, tap **Add Feed**
3. Enter a title, RSS URL, and whether it requires Substack authentication
4. Tap **Add**

Articles appear in the **Home** tab (unified unread feed) and the **Feed** tab (per-feed browsing).

### Signing in to Substack (for paid content)

Free Substack RSS feeds work without authentication. For paid subscriptions, you need to sign in so the app can fetch full article content.

1. In **Settings**, find **Substack Authentication**
2. Tap **Sign in with browser**
3. Sign in with your Substack account in the WebView that opens
4. The app captures your session cookie automatically

**Advanced fallback:** If browser sign-in doesn't work, you can manually paste your `substack.sid` cookie value. Get it from Chrome DevTools: F12 > Application > Cookies > `substack.sid`.

> **Note:** Even with authentication, RSS feeds for paid posts may only return a preview. For guaranteed full content, use email ingestion (see below).

## Email ingestion (full paid content)

RSS feeds for paid Substack posts often return only a preview. Email ingestion solves this by fetching the full article from a dedicated email inbox.

### How it works

1. You set up a dedicated email address (e.g., a Gmail alias or Fastmail folder)
2. You subscribe to Substack publications using that email
3. The app connects via IMAP, fetches new emails, and extracts the full article HTML
4. If the article already exists from RSS, the truncated version is upgraded in place with the full email content

### Setup

1. **Create a dedicated email** (or use a folder/alias on an existing account)
2. **Subscribe** to your Substack publications with that email
3. In **Settings > Email Inbox**, tap **Set up email inbox**
4. Choose a preset (**Gmail** or **Fastmail**) or enter IMAP details manually:
   - IMAP server (e.g., `imap.gmail.com`)
   - Port (default: `993`, SSL)
   - Email address
   - App password (not your main password — see below)
   - Folder (default: `INBOX`)
5. Tap **Test & Save** — the app verifies the connection

**App passwords:**
- **Gmail:** [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) (requires 2FA enabled)
- **Fastmail:** Settings > Password & Security > App passwords

Credentials are stored locally using Android's EncryptedSharedPreferences (AES-256-GCM). They are never sent anywhere except directly to your IMAP server.

### Sync behavior

- Automatic every 4 hours on WiFi
- Manual sync available in Settings
- Only fetches emails received since the last sync
- Deduplicates by email message ID

## Cross-device sync

Sync read/archive state, feeds, and reading preferences across multiple devices running re:ink. Requires running your own sync server.

### What syncs

- Article read/unread and archived state
- Feed list (titles, URLs, auth settings)
- Read-later queue items and their read/archive state
- Reading preferences (font, size, margins, alignment, pagination mode)

### Sync server

The app connects to a self-hosted sync server. The server acts as a central merge point — each device pushes its changes since the last sync, the server merges them, and returns the combined state.

**Server endpoint:** The app expects two endpoints:
- `GET /health` — health check (returns 200)
- `POST /sync` — push/pull changes (authenticated via `X-API-Key` header)

The sync server project is separate from this repo. You need to deploy it yourself and configure an API key.

### Setup

1. Deploy the sync server on your local network or a VPS
2. In **Settings > Device Sync**, enter:
   - **Server URL** (e.g., `http://192.168.1.100:8073`)
   - **API Key** (from your server config)
3. Tap **Connect** — the app verifies the connection
4. Each device gets a unique device ID assigned automatically

Once connected:
- **Sync now** triggers an immediate sync
- Automatic sync runs every 4 hours on WiFi
- **Disconnect** removes the connection and device ID

### How conflicts are resolved

Changes are merged by modification timestamp. If two devices modify the same article, the most recent change wins. Reading preferences use the same last-writer-wins strategy.

## Cross-device sharing (cloud queue)

Save articles to your read-later queue from any device — your phone, laptop, or tablet — using a cloud relay.

### How it works

A lightweight Cloudflare Worker acts as a relay. When you share a URL from another device, it's pushed to a temporary queue. The app polls this queue every 4 hours and adds new items to your read-later list.

### Setup

1. In **Settings > Cross-Device Sharing**, tap **Set up cross-device sharing**
2. The app creates a queue and shows:
   - A **share URL** (tap to copy)
   - A **QR code** for iOS Shortcut setup
3. Scan the QR code on your phone to set up an iOS Shortcut, or use the share URL with any HTTP client

### Sharing from other devices

**iOS Shortcut:** Scan the QR code to open the setup page, which walks you through creating a Share Sheet shortcut. Once configured, you can share any URL to your re:ink queue from Safari or any app.

**Manual / scripted:**
```bash
curl -X POST "https://your-share-url/items" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/article"}'
```

### What happens to shared URLs

1. URL arrives in the cloud queue
2. App pulls it on next sync (or manual sync)
3. URL is saved to the read-later queue
4. Content is extracted using Readability4J (with fallback strategies for JS-heavy sites)
5. Queue item is acknowledged and removed from the relay

## Reader

The reader renders article HTML in a WebView with e-ink optimizations.

### Reading modes

- **Scroll:** Standard vertical scrolling
- **Paginated:** Content split into pages. Navigate with volume keys (Volume Down = next page, Volume Up = previous page). A progress bar shows your position.

### Typography

Tap the **Aa** icon in the reader overlay to customize:

| Setting | Options |
|---------|---------|
| Font | Literata, Source Serif 4, Atkinson Hyperlegible |
| Size | 14–36px |
| Line height | 1.2–2.2x |
| Side margins | 8–192dp |
| Vertical margins | 0–96dp |
| Alignment | Left, Center, Right, Justify |

Changes apply immediately with a live preview. Preferences persist across sessions and sync to other devices.

### Links in articles

Tapping a link in an article opens a bottom sheet with options:
- **Save for Later** — adds to your read-later queue
- **Open in Browser** — opens externally
- **Cancel**

This prevents accidental navigation away from the reader.

## Read-later queue

Save URLs for later reading. Content is extracted automatically in the background.

### How articles get saved

- Tap a link in the reader and choose **Save for Later**
- Share a URL via the cloud queue from another device

### Content extraction

The app extracts clean article text using multiple strategies (tried in order):

1. Standard HTTP fetch + Readability4J
2. Googlebot User-Agent (bypasses some paywalls)
3. Google Cache
4. Archive.org Wayback Machine

Extraction runs automatically every 4 hours. Failed items retry up to 3 times.

## Auto-update

The app checks for updates from GitHub Releases every 24 hours.

When an update is found:
1. The APK is downloaded automatically in the background
2. A notification appears: "Update ready"
3. A banner shows at the top of the Home screen: "v1.1.0 ready to install"
4. Tap the banner (or notification) to trigger the system install dialog

You can also check manually in **Settings > App Version > Check for updates**.

## Background sync schedule

| Task | Interval | Network |
|------|----------|---------|
| RSS feed sync | 4 hours | WiFi only |
| Email sync | 4 hours | WiFi only |
| Cloud queue sync | 4 hours | WiFi only |
| Device sync | 4 hours | WiFi only |
| Read-later extraction | After device sync | WiFi only |
| Update check | 24 hours | Any connection |

All syncs can be triggered manually from the Home screen ("Sync" button) or from individual sections in Settings.

## Privacy

- **No analytics or tracking.** The app connects only to services you configure.
- **Email credentials** are encrypted locally (AES-256-GCM via Android Keystore) and sent only to your IMAP server.
- **Sync data** goes only to your self-hosted sync server.
- **Cloud queue URLs** pass through the relay worker but are deleted after acknowledgment.
- **Update checks** hit the GitHub API (public, no authentication).

## Build from source

Requires JDK 17 and Android SDK.

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

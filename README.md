# re:ink

An e-ink optimized Substack reader for Android.

Built for Android e-ink devices (Boox, Bigme, Dasung, etc.). A personal project published under GPL v3 for anyone who finds it useful.

## Install

Grab the latest APK from [GitHub Releases](https://github.com/christi4nity/re-ink/releases/latest) and sideload it. The app checks for updates automatically and will prompt you when a new version is available.

**Requirements:** Android 9+ (API 28). Optimized for e-ink but works on any Android device.

## Getting started

### Adding feeds

1. Open **Settings** (gear icon in the top bar)
2. Under **Feeds**, tap **Add Feed**
3. Enter a title, RSS URL, and whether it requires Substack authentication
4. Tap **Add**

Articles appear in the **Home** tab (unified unread feed) and the **Feed** tab (per-feed browsing).

## Email ingestion (paid content)

The only way to get full paid Substack content into re:ink is through email. RSS feeds always return a truncated preview for paid posts.

### How it works

1. You set up a dedicated email address (e.g., a Gmail alias or Fastmail folder)
2. You subscribe to Substack publications using that email
3. The app connects via IMAP, fetches new emails, and extracts the full article HTML
4. If the article already exists from RSS, the truncated version is upgraded in place with the full email content

### Setup

1. **Create a dedicated email** (or use a folder/alias on an existing account)
2. **Subscribe** to your Substack publications with that email
3. In **Settings > Email Inbox**, tap **Set up email inbox**
4. Choose a preset (**Gmail**) or enter IMAP details manually:
   - IMAP server (e.g., `imap.gmail.com`)
   - Port (default: `993`, SSL)
   - Email address
   - App password (not your main password — see below)
   - Folder (default: `INBOX`)
5. Tap **Test & Save** — the app verifies the connection

**App passwords (Gmail):** [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) (requires 2FA enabled). Any IMAP provider works — just enter the server details manually.

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

The sync server is included in this repo at [`sync-server/`](sync-server/). It's a lightweight Go service backed by SQLite — no external database needed. Each device pushes its changes since the last sync, the server merges them using per-field timestamps, and returns the combined state.

### Deploying the sync server

The easiest way is Docker. You can run it on a Raspberry Pi, NAS, home server, or any VPS.

**1. Generate an API key:**

```bash
openssl rand -hex 16
```

**2. Start with Docker Compose:**

```bash
cd sync-server

# Create .env with your API key
echo "REINK_SYNC_API_KEY=your-generated-key" > .env

docker compose up -d
```

The server starts on port `8073` with data persisted in a Docker volume.

**Or run with plain Docker:**

```bash
cd sync-server
docker build -t reink-sync .
docker run -d \
  --name reink-sync \
  --restart unless-stopped \
  -p 8073:8073 \
  -v reink-sync-data:/data \
  -e REINK_SYNC_API_KEY=your-generated-key \
  reink-sync
```

**Or build and run natively** (requires Go 1.22+):

```bash
cd sync-server
go build -o reink-sync .
REINK_SYNC_API_KEY=your-key DATA_DIR=./data ./reink-sync
```

### Connecting the app

1. In **Settings > Device Sync**, enter:
   - **Server URL** — e.g., `http://192.168.1.100:8073` (use your server's LAN IP or Tailscale address)
   - **API Key** — the key you generated above
2. Tap **Connect** — the app verifies the connection via `/health`
3. Each device gets a unique device ID assigned automatically

Once connected:
- **Sync now** triggers an immediate sync
- Automatic sync runs every 4 hours on WiFi
- **Disconnect** removes the connection and device ID

### How conflicts are resolved

The server uses per-field timestamp merging. Each field (read, archived, etc.) has its own timestamp, and the most recent change wins independently. For example, if device A marks an article as read and device B archives it at the same time, both changes are preserved. Reading preferences use last-writer-wins on the entire preferences blob.

### Server details

- **Storage:** SQLite with WAL mode, stored at `/data/sync.db`
- **Auth:** Single API key via `X-API-Key` header
- **Endpoints:** `GET /health` and `POST /sync`
- **Resources:** ~5MB RAM, negligible CPU. Runs comfortably on a Raspberry Pi Zero

## Cross-device sharing (cloud queue)

Save articles to your read-later queue from your phone. You find an article in Safari, tap Share, pick "Save to re:ink", and it shows up on your e-ink device next time it syncs.

This works through a lightweight Cloudflare Worker ([`worker/`](worker/)) that acts as a relay queue. The app polls it every 4 hours and pulls new URLs into your read-later list.

### Setup

1. In **Settings > Cross-Device Sharing**, tap **Set up cross-device sharing**
2. The app creates a queue and displays a **QR code**
3. Scan the QR code on your iPhone — it opens a setup page

### iOS Shortcut (recommended)

The setup page walks you through adding an iOS Shortcut to your Share Sheet:

1. Tap **Copy your base URL** on the setup page
2. Tap **Add Shortcut to iPhone** — this opens a pre-built iCloud Shortcut
3. When prompted, paste the URL you copied
4. Done. Now in any app, tap **Share > Save to re:ink** to send a URL to your queue

The Shortcut works from Safari, Twitter/X, Threads, Mastodon, RSS readers — anything with a Share Sheet.

### Other platforms

The cloud queue is a simple HTTP endpoint. You can send URLs to it from anything:

**Browser bookmarklet:**
```javascript
javascript:void(fetch('YOUR_SHARE_URL'+encodeURIComponent(location.href)))
```

**Command line:**
```bash
# Using the simple GET endpoint (easiest)
curl "https://reink-relay.cv-b61.workers.dev/q/YOUR_QUEUE_ID/add?url=https://example.com/article"

# Or POST with JSON
curl -X POST "https://reink-relay.cv-b61.workers.dev/q/YOUR_QUEUE_ID/items" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/article"}'
```

**Android (Tasker/Automate):** Use the GET endpoint with a share intent handler — `https://reink-relay.cv-b61.workers.dev/q/YOUR_QUEUE_ID/add?url={shared_url}`

Your share URL is visible in Settings after setup (tap to copy).

### What happens to shared URLs

1. URL arrives in the cloud relay queue
2. App pulls it on next sync (every 4 hours, or tap Sync manually)
3. URL is saved to your read-later queue
4. Content is extracted using Readability4J (with fallback strategies for paywalled or JS-heavy sites)
5. Queue item is acknowledged and removed from the relay

### Hosting your own relay

The relay worker source is in [`worker/`](worker/). It's a Cloudflare Worker using KV for storage. To deploy your own:

```bash
cd worker
pnpm install
# Edit wrangler.toml with your Cloudflare account ID
# Create a KV namespace: wrangler kv namespace create REINK_QUEUE
wrangler deploy
```

Queue items auto-expire after 30 days. The worker is stateless and fits within Cloudflare's free tier.

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
3. A banner shows at the top of the Home screen: "Update ready to install"
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
git tag v1.x.x
git push origin v1.x.x
```

This triggers a workflow that builds a signed APK and publishes it as a GitHub Release.

## Architecture

Single-module app, MVVM with Clean Architecture. See [CLAUDE.md](CLAUDE.md) for full architecture documentation including layer structure, key design decisions, and package layout.

## Tech Stack

Kotlin, Jetpack Compose, Material 3, Room, Hilt, OkHttp, WorkManager, Readability4J

## License

[GPL v3](LICENSE)

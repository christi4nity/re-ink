# re:ink

A Substack reader built for e-ink. Full paid articles, clean typography, no distractions.

Works on any Android e-ink device (Boox, Bigme, Dasung, etc.). Published under GPL v3.

## Install

Grab the latest APK from [GitHub Releases](https://github.com/christi4nity/re-ink/releases/latest) and sideload it. The app checks for updates automatically and will prompt you when a new version is available.

**Requirements:** Android 9+ (API 28). Optimized for e-ink but works on any Android device.

## Getting started

### 1. Connect your email

Substack delivers full article content (including paid posts) via email. re:ink connects to your inbox via IMAP and pulls articles directly.

1. In **Settings** (gear icon in the top bar), go to **Email Inbox** and tap **Set up email inbox**
2. Choose the **Gmail** preset or enter IMAP details manually
3. Enter your email address and an **app password** (not your main password)
4. Tap **Test & Save**

**Gmail app passwords:** [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) (requires 2FA enabled). Any IMAP provider works — just enter the server details manually.

### 2. Subscribe to publications

Subscribe to Substack newsletters using the email address you connected. When the app syncs, it automatically creates a feed for each publication and pulls in full article content — including paid posts.

Sync runs automatically every 4 hours on WiFi, or tap **Sync** on the Home screen to trigger it immediately.

### 3. Read

Articles appear in the **Home** tab (unified unread feed) and the **Feed** tab (per-feed browsing). Tap an article to open the reader.

### RSS feeds (optional)

You can also add RSS feeds for non-Substack sources. In **Settings > Feeds**, tap **Add Feed** and enter a title and RSS URL. Note that RSS only provides free preview content for paid Substack posts — email is the way to get full articles.

## Features

- **E-ink optimized UI** — grayscale theme, no animations, bold typography designed for e-ink refresh rates
- **Full paid content** — email ingestion delivers complete articles, not truncated RSS previews
- **Customizable reader** — choose font, size, line height, margins, and alignment. Scroll or paginate with volume keys. [Details](guide/reader.md)
- **Read-later queue** — save links from articles for later. Content is extracted automatically using multiple fallback strategies
- **Cross-device sync** — sync read state, feeds, and preferences across devices via a self-hosted server. [Setup guide](guide/cross-device-sync.md)
- **Cloud queue** — send articles to your e-ink device from your phone via an iOS Shortcut or any HTTP client. [Setup guide](guide/cloud-queue.md)
- **Auto-update** — checks GitHub Releases daily and prompts when a new version is ready
- **Privacy-first** — no analytics, no tracking. Email credentials encrypted locally (AES-256-GCM). Data only goes to services you configure.

## Build from source

Requires JDK 17 and Android SDK.

```bash
./gradlew assembleDebug
```

Release APKs are built automatically by GitHub Actions when a version tag (`v*.*.*`) is pushed.

## Contributing

I built this for my own use and it does what I need, but I'd love for others to get value from it too. Bug reports, feature requests, and PRs are all welcome — especially if you want to improve areas like RSS feed support. [Open an issue](https://github.com/christi4nity/re-ink/issues) to get started.

## License

[GPL v3](LICENSE)

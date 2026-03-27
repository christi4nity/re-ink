# Cross-device sharing (cloud queue)

Save articles to your read-later queue from your phone. You find an article in Safari, tap Share, pick "Save to re:ink", and it shows up on your e-ink device next time it syncs.

This works through a lightweight Cloudflare Worker ([`worker/`](../worker/)) that acts as a relay queue. The app polls it every 4 hours and pulls new URLs into your read-later list.

## Setup

1. In **Settings > Cross-Device Sharing**, tap **Set up cross-device sharing**
2. The app creates a queue and displays a **QR code**
3. Scan the QR code on your iPhone — it opens a setup page

## iOS Shortcut (recommended)

The setup page walks you through adding an iOS Shortcut to your Share Sheet:

1. Tap **Copy your base URL** on the setup page
2. Tap **Add Shortcut to iPhone** — this opens a pre-built iCloud Shortcut
3. When prompted, paste the URL you copied
4. Done. Now in any app, tap **Share > Save to re:ink** to send a URL to your queue

The Shortcut works from Safari, Twitter/X, Threads, Mastodon, RSS readers — anything with a Share Sheet.

## Other platforms

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

## What happens to shared URLs

1. URL arrives in the cloud relay queue
2. App pulls it on next sync (every 4 hours, or tap Sync manually)
3. URL is saved to your read-later queue
4. Content is extracted using Readability4J (with fallback strategies for paywalled or JS-heavy sites)
5. Queue item is acknowledged and removed from the relay

## Hosting your own relay

The relay worker source is in [`worker/`](../worker/). It's a Cloudflare Worker using KV for storage. To deploy your own:

```bash
cd worker
pnpm install
# Edit wrangler.toml with your Cloudflare account ID
# Create a KV namespace: wrangler kv namespace create REINK_QUEUE
wrangler deploy
```

Queue items auto-expire after 30 days. The worker is stateless and fits within Cloudflare's free tier.

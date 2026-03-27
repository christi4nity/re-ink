# Cross-device sync

Sync read/archive state, feeds, and reading preferences across multiple devices running re:ink. Requires running your own sync server.

## What syncs

- Article read/unread and archived state
- Feed list (titles, URLs, auth settings)
- Read-later queue items and their read/archive state
- Reading preferences (font, size, margins, alignment, pagination mode)

## Sync server

The sync server is included in this repo at [`sync-server/`](../sync-server/). It's a lightweight Go service backed by SQLite — no external database needed. Each device pushes its changes since the last sync, the server merges them using per-field timestamps, and returns the combined state.

## Deploying the sync server

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

## Connecting the app

1. In **Settings > Device Sync**, enter:
   - **Server URL** — e.g., `http://192.168.1.100:8073` (use your server's LAN IP or Tailscale address)
   - **API Key** — the key you generated above
2. Tap **Connect** — the app verifies the connection via `/health`
3. Each device gets a unique device ID assigned automatically

Once connected:
- **Sync now** triggers an immediate sync
- Automatic sync runs every 4 hours on WiFi
- **Disconnect** removes the connection and device ID

## How conflicts are resolved

The server uses per-field timestamp merging. Each field (read, archived, etc.) has its own timestamp, and the most recent change wins independently. For example, if device A marks an article as read and device B archives it at the same time, both changes are preserved. Reading preferences use last-writer-wins on the entire preferences blob.

## Server details

- **Storage:** SQLite with WAL mode, stored at `/data/sync.db`
- **Auth:** Single API key via `X-API-Key` header
- **Endpoints:** `GET /health` and `POST /sync`
- **Resources:** ~5MB RAM, negligible CPU. Runs comfortably on a Raspberry Pi Zero

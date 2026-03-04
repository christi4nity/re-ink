interface Env {
  REINK_QUEUE: KVNamespace;
}

const TTL_SECONDS = 30 * 24 * 60 * 60; // 30 days
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const URL_RE = /^https?:\/\/.+/;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function error(message: string, status: number): Response {
  return json({ error: message }, status);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const method = request.method;
    const path = url.pathname;

    // POST /q — create queue
    if (method === "POST" && path === "/q") {
      return createQueue(env);
    }

    // Route: /q/:id/...
    const queueMatch = path.match(/^\/q\/([^/]+)(\/.*)?$/);
    if (!queueMatch) {
      return error("Not found", 404);
    }

    const queueId = queueMatch[1];
    const subPath = queueMatch[2] || "";

    if (!UUID_RE.test(queueId)) {
      return error("Invalid queue ID", 400);
    }

    // Verify queue exists
    const meta = await env.REINK_QUEUE.get(`${queueId}:meta`);
    if (!meta) {
      return error("Queue not found", 404);
    }

    // GET /q/:id/setup — setup page
    if (method === "GET" && subPath === "/setup") {
      return setupPage(request, queueId);
    }

    // POST /q/:id/items — add item
    if (method === "POST" && subPath === "/items") {
      return addItem(env, queueId, request);
    }

    // GET /q/:id/add?url=... — add item (simple, for iOS Shortcuts)
    if (method === "GET" && subPath === "/add") {
      const addUrl = url.searchParams.get("url");
      if (!addUrl || !URL_RE.test(addUrl)) {
        return error("Missing or invalid ?url= parameter", 400);
      }
      return addItemDirect(env, queueId, addUrl);
    }

    // GET /q/:id/debug — show last POST attempt (temporary)
    if (method === "GET" && subPath === "/debug") {
      const last = await env.REINK_QUEUE.get(`${queueId}:debug`);
      return json(last ? JSON.parse(last) : { message: "no debug data" });
    }

    // GET /q/:id/items — list items
    if (method === "GET" && subPath === "/items") {
      return listItems(env, queueId);
    }

    // POST /q/:id/ack — acknowledge items
    if (method === "POST" && subPath === "/ack") {
      return ackItems(env, queueId, request);
    }

    return error("Not found", 404);
  },
};

function setupPage(request: Request, queueId: string): Response {
  const baseUrl = new URL(request.url).origin;
  const addUrl = `${baseUrl}/q/${queueId}/add?url=`;
  const icloudShortcut = "https://www.icloud.com/shortcuts/5cf14ef5766442a1a4254d7eefaa2a10";

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>re:ink — Share to Read Later</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, system-ui, sans-serif; background: #fff; color: #111;
         max-width: 480px; margin: 0 auto; padding: 24px 16px; }
  h1 { font-size: 20px; margin-bottom: 4px; }
  .subtitle { color: #666; font-size: 14px; margin-bottom: 24px; }
  .section { margin-bottom: 24px; }
  .section-title { font-size: 13px; font-weight: 600; color: #666;
                   text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
  .url-box { background: #f5f5f5; border: 1px solid #ddd; border-radius: 8px;
             padding: 12px; font-family: monospace; font-size: 13px; word-break: break-all; }
  .btn { display: block; width: 100%; padding: 14px; border: 2px solid #111;
         background: #fff; color: #111; font-size: 16px; font-weight: 600;
         text-align: center; text-decoration: none; border-radius: 8px; cursor: pointer; }
  .btn:active { background: #111; color: #fff; }
  .btn-primary { background: #111; color: #fff; }
  .btn-primary:active { background: #333; }
  .copied { background: #111; color: #fff; }
  .note { font-size: 13px; color: #666; line-height: 1.4; margin-top: 8px; }
  .steps { padding-left: 0; list-style: none; counter-reset: step; margin-top: 12px; }
  .steps li { counter-increment: step; padding: 10px 0; padding-left: 32px; position: relative;
              font-size: 15px; line-height: 1.4; }
  .steps li::before { content: counter(step); position: absolute; left: 0; top: 10px;
                      width: 22px; height: 22px; background: #111; color: #fff;
                      border-radius: 50%; font-size: 12px; font-weight: 700;
                      display: flex; align-items: center; justify-content: center; }
  .steps li + li { border-top: 1px solid #eee; }
</style>
</head>
<body>

<h1>re:ink</h1>
<p class="subtitle">Share articles to your read-later queue</p>

<div class="section">
  <ol class="steps">
    <li><button class="btn btn-primary" id="copyBtn" onclick="copyUrl()">Copy your base URL</button></li>
    <li><a class="btn" href="${icloudShortcut}">Add Shortcut to iPhone</a>
      <p class="note">When prompted, paste the URL you just copied.</p></li>
    <li>Share any link from Safari → pick <strong>"Save to re:ink"</strong></li>
  </ol>
</div>

<div class="section">
  <div class="section-title">Your base URL</div>
  <div class="url-box" id="url">${addUrl}</div>
  <p class="note">The shortcut appends the shared article URL to the end and opens it.</p>
</div>

<script>
function copyUrl() {
  navigator.clipboard.writeText('${addUrl}').then(() => {
    const btn = document.getElementById('copyBtn');
    btn.textContent = 'Copied!';
    btn.classList.add('copied');
    setTimeout(() => { btn.textContent = 'Copy your base URL'; btn.classList.remove('copied'); }, 2000);
  });
}
</script>
</body>
</html>`;

  return new Response(html, {
    headers: { "Content-Type": "text/html;charset=utf-8" },
  });
}

async function createQueue(env: Env): Promise<Response> {
  const id = crypto.randomUUID();
  await env.REINK_QUEUE.put(
    `${id}:meta`,
    JSON.stringify({ createdAt: new Date().toISOString() }),
    { expirationTtl: TTL_SECONDS },
  );
  return json({ id }, 201);
}

async function addItem(
  env: Env,
  queueId: string,
  request: Request,
): Promise<Response> {
  const rawBody = await request.text();
  const contentType = request.headers.get("content-type") || "";
  const debugData = {
    timestamp: new Date().toISOString(),
    contentType,
    rawBody: rawBody.slice(0, 500),
    method: request.method,
    url: request.url,
  };
  await env.REINK_QUEUE.put(`${queueId}:debug`, JSON.stringify(debugData), {
    expirationTtl: 3600,
  });

  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(rawBody);
  } catch {
    return error("Invalid JSON", 400);
  }

  // Accept both "url" and "URL" (iOS Shortcuts sends uppercase keys)
  const rawUrl = parsed.url ?? parsed.URL;
  // Shortcuts may wrap the value in an array
  const urlValue = Array.isArray(rawUrl) ? rawUrl[0] : rawUrl;

  if (!urlValue || typeof urlValue !== "string" || !URL_RE.test(urlValue)) {
    return error("Invalid or missing url (must be http/https)", 400);
  }

  const itemId = crypto.randomUUID();
  const item = {
    id: itemId,
    url: urlValue,
    addedAt: new Date().toISOString(),
  };

  await env.REINK_QUEUE.put(`${queueId}:${itemId}`, JSON.stringify(item), {
    expirationTtl: TTL_SECONDS,
  });

  // Refresh meta TTL
  await env.REINK_QUEUE.put(
    `${queueId}:meta`,
    JSON.stringify({ createdAt: new Date().toISOString() }),
    { expirationTtl: TTL_SECONDS },
  );

  return json({ id: itemId }, 201);
}

async function addItemDirect(
  env: Env,
  queueId: string,
  itemUrl: string,
): Promise<Response> {
  const itemId = crypto.randomUUID();
  const item = {
    id: itemId,
    url: itemUrl,
    addedAt: new Date().toISOString(),
  };

  await env.REINK_QUEUE.put(`${queueId}:${itemId}`, JSON.stringify(item), {
    expirationTtl: TTL_SECONDS,
  });

  await env.REINK_QUEUE.put(
    `${queueId}:meta`,
    JSON.stringify({ createdAt: new Date().toISOString() }),
    { expirationTtl: TTL_SECONDS },
  );

  return json({ id: itemId }, 201);
}

async function listItems(env: Env, queueId: string): Promise<Response> {
  const prefix = `${queueId}:`;
  const listed = await env.REINK_QUEUE.list({ prefix });

  const items: unknown[] = [];
  for (const key of listed.keys) {
    if (key.name === `${queueId}:meta`) continue;
    const value = await env.REINK_QUEUE.get(key.name);
    if (value) {
      items.push(JSON.parse(value));
    }
  }

  return json({ items });
}

async function ackItems(
  env: Env,
  queueId: string,
  request: Request,
): Promise<Response> {
  let body: { ids?: string[] };
  try {
    body = await request.json();
  } catch {
    return error("Invalid JSON", 400);
  }

  if (!Array.isArray(body.ids) || body.ids.length === 0) {
    return error("Missing or empty ids array", 400);
  }

  for (const itemId of body.ids) {
    if (typeof itemId === "string") {
      await env.REINK_QUEUE.delete(`${queueId}:${itemId}`);
    }
  }

  return json({ acknowledged: body.ids.length });
}

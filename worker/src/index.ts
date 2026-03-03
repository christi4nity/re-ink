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

    // GET /q/:id/shortcut — downloadable .shortcut file
    if (method === "GET" && subPath === "/shortcut") {
      return shortcutFile(request, queueId);
    }

    // POST /q/:id/items — add item
    if (method === "POST" && subPath === "/items") {
      return addItem(env, queueId, request);
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
  const postUrl = `${baseUrl}/q/${queueId}/items`;
  const shortcutUrl = `${baseUrl}/q/${queueId}/shortcut`;
  const importUrl = `shortcuts://import-shortcut?url=${encodeURIComponent(shortcutUrl)}&name=${encodeURIComponent("Save to re:ink")}`;

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
             padding: 12px; font-family: monospace; font-size: 13px; word-break: break-all;
             margin-bottom: 8px; }
  .btn { display: block; width: 100%; padding: 14px; border: 2px solid #111;
         background: #fff; color: #111; font-size: 16px; font-weight: 600;
         text-align: center; text-decoration: none; border-radius: 8px; cursor: pointer;
         margin-bottom: 8px; }
  .btn:active { background: #111; color: #fff; }
  .btn-primary { background: #111; color: #fff; }
  .btn-primary:active { background: #333; }
  .copied { background: #111; color: #fff; }
  .note { font-size: 13px; color: #666; line-height: 1.4; }
  .divider { border: none; border-top: 1px solid #eee; margin: 24px 0; }
</style>
</head>
<body>

<h1>re:ink</h1>
<p class="subtitle">Share articles to your read-later queue</p>

<div class="section">
  <a class="btn btn-primary" href="${importUrl}">Add Shortcut to iPhone</a>
  <p class="note">Opens the Shortcuts app and installs "Save to re:ink". Then share any URL from Safari and pick the shortcut.</p>
</div>

<hr class="divider">

<div class="section">
  <div class="section-title">API endpoint</div>
  <div class="url-box" id="url">${postUrl}</div>
  <button class="btn" id="copyBtn" onclick="copyUrl()">Copy URL</button>
  <p class="note">POST <code>{"url":"..."}</code> from any HTTP client.</p>
</div>

<script>
function copyUrl() {
  navigator.clipboard.writeText('${postUrl}').then(() => {
    const btn = document.getElementById('copyBtn');
    btn.textContent = 'Copied!';
    btn.classList.add('copied');
    setTimeout(() => { btn.textContent = 'Copy URL'; btn.classList.remove('copied'); }, 2000);
  });
}
</script>
</body>
</html>`;

  return new Response(html, {
    headers: { "Content-Type": "text/html;charset=utf-8" },
  });
}

function shortcutFile(request: Request, queueId: string): Response {
  const baseUrl = new URL(request.url).origin;
  const postUrl = `${baseUrl}/q/${queueId}/items`;

  // U+FFFC (Object Replacement Character) is how Shortcuts represents
  // variable references inline. The attachmentsByRange dict maps the
  // character position to the variable type (ExtensionInput = Share Sheet).
  const plist = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>WFWorkflowActions</key>
  <array>
    <dict>
      <key>WFWorkflowActionIdentifier</key>
      <string>is.workflow.actions.downloadurl</string>
      <key>WFWorkflowActionParameters</key>
      <dict>
        <key>WFURL</key>
        <string>${postUrl}</string>
        <key>WFHTTPMethod</key>
        <string>POST</string>
        <key>WFHTTPBodyType</key>
        <string>Json</string>
        <key>WFJSONValues</key>
        <dict>
          <key>Value</key>
          <dict>
            <key>WFDictionaryFieldValueItems</key>
            <array>
              <dict>
                <key>WFItemType</key>
                <integer>0</integer>
                <key>WFKey</key>
                <dict>
                  <key>Value</key>
                  <dict>
                    <key>string</key>
                    <string>url</string>
                  </dict>
                  <key>WFSerializationType</key>
                  <string>WFTextTokenString</string>
                </dict>
                <key>WFValue</key>
                <dict>
                  <key>Value</key>
                  <dict>
                    <key>string</key>
                    <string>\uFFFC</string>
                    <key>attachmentsByRange</key>
                    <dict>
                      <key>{0, 1}</key>
                      <dict>
                        <key>Type</key>
                        <string>ExtensionInput</string>
                      </dict>
                    </dict>
                  </dict>
                  <key>WFSerializationType</key>
                  <string>WFTextTokenAttachment</string>
                </dict>
              </dict>
            </array>
          </dict>
          <key>WFSerializationType</key>
          <string>WFDictionaryFieldValue</string>
        </dict>
      </dict>
    </dict>
    <dict>
      <key>WFWorkflowActionIdentifier</key>
      <string>is.workflow.actions.notification</string>
      <key>WFWorkflowActionParameters</key>
      <dict>
        <key>WFNotificationActionBody</key>
        <string>Saved to re:ink</string>
        <key>WFNotificationActionTitle</key>
        <string>re:ink</string>
      </dict>
    </dict>
  </array>
  <key>WFWorkflowClientVersion</key>
  <string>2302.0.4</string>
  <key>WFWorkflowHasOutputFallback</key>
  <false/>
  <key>WFWorkflowHasShortcutInputVariables</key>
  <true/>
  <key>WFWorkflowIcon</key>
  <dict>
    <key>WFWorkflowIconGlyphNumber</key>
    <integer>59751</integer>
    <key>WFWorkflowIconStartColor</key>
    <integer>463140863</integer>
  </dict>
  <key>WFWorkflowImportQuestions</key>
  <array/>
  <key>WFWorkflowInputContentItemClasses</key>
  <array>
    <string>WFURLContentItem</string>
  </array>
  <key>WFWorkflowMinimumClientVersion</key>
  <integer>900</integer>
  <key>WFWorkflowMinimumClientVersionString</key>
  <string>900</string>
  <key>WFWorkflowTypes</key>
  <array>
    <string>ActionExtension</string>
  </array>
</dict>
</plist>`;

  return new Response(plist, {
    headers: {
      "Content-Type": "application/octet-stream",
      "Content-Disposition": 'attachment; filename="Save to re-ink.shortcut"',
    },
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
  let body: { url?: string };
  try {
    body = await request.json();
  } catch {
    return error("Invalid JSON", 400);
  }

  if (!body.url || typeof body.url !== "string" || !URL_RE.test(body.url)) {
    return error("Invalid or missing url (must be http/https)", 400);
  }

  const itemId = crypto.randomUUID();
  const item = {
    id: itemId,
    url: body.url,
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

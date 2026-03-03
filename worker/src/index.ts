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

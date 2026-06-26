import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { URL } from "node:url";
import { z } from "zod";
import { AppDatabase } from "./database.js";
import { EventBus } from "./event-bus.js";
import { loadEnv } from "./env.js";
import { JsonRpcClient } from "./json-rpc.js";
import { LoggingPushGateway } from "./push.js";
import { PokeSseClient } from "./poke-sse-client.js";

const DeviceRegistration = z.object({
  pokeUserId: z.string().min(1),
  deviceId: z.string().optional(),
  fcmToken: z.string().nullable().optional(),
  deviceName: z.string().nullable().optional()
});

const MessageSend = z.object({
  pokeUserId: z.string().min(1),
  text: z.string().min(1),
  correlationId: z.string().optional()
});

const HandlerEvent = z.object({
  eventId: z.string().optional(),
  pokeUserId: z.string().optional(),
  type: z.enum(["message", "action", "status", "log", "progress", "notification", "incoming_request"]).default("message"),
  payload: z.unknown(),
  correlationId: z.string().optional()
});

export function createApp(deps?: {
  db?: AppDatabase;
  bus?: EventBus;
  rpc?: JsonRpcClient;
}) {
  const env = loadEnv();
  const db = deps?.db ?? new AppDatabase(env.databasePath);
  const bus = deps?.bus ?? new EventBus();
  const rpc = deps?.rpc ?? new JsonRpcClient(env.pokeJsonRpcUrl, env.pokeJsonRpcBearerToken);
  const push = new LoggingPushGateway();

  async function route(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);
    try {
      if (req.method === "GET" && url.pathname === "/health") {
        return json(res, 200, { ok: true });
      }
      if (req.method === "POST" && url.pathname === "/api/devices/register") {
        const input = DeviceRegistration.parse(await readJson(req));
        const device = db.upsertDevice(input);
        return json(res, 200, { device });
      }
      if (req.method === "POST" && url.pathname === "/api/messages/send") {
        const input = MessageSend.parse(await readJson(req));
        const local = db.insertEvent({
          pokeUserId: input.pokeUserId,
          eventType: "message",
          payload: { direction: "outbound", text: input.text },
          correlationId: input.correlationId ?? null,
          deliveryState: rpc.configured ? "queued" : "delivered"
        });
        if (rpc.configured) {
          await rpc.call("tools/call", {
            name: "send_message",
            arguments: { text: input.text, correlationId: local.eventId }
          });
        }
        bus.publish(local);
        return json(res, 202, { event: local, rpcForwarded: rpc.configured });
      }
      if (req.method === "GET" && url.pathname === "/api/events/history") {
        const pokeUserId = requiredQuery(url, "pokeUserId");
        return json(res, 200, { events: db.listEvents(pokeUserId, url.searchParams.get("since") ?? undefined) });
      }
      if (req.method === "GET" && url.pathname === "/api/events/stream") {
        const pokeUserId = requiredQuery(url, "pokeUserId");
        return streamEvents(req, res, db, bus, pokeUserId, url.searchParams.get("since") ?? undefined);
      }
      if (req.method === "POST" && url.pathname === "/api/actions/complete") {
        const body = await readJson(req);
        return json(res, 202, { accepted: true, body });
      }
      if (req.method === "POST" && url.pathname === "/api/poke/handler") {
        const pokeUserId = String(req.headers["x-poke-user-id"] ?? "");
        const input = HandlerEvent.parse(await readJson(req));
        const event = db.insertEvent({
          eventId: input.eventId,
          pokeUserId: input.pokeUserId ?? pokeUserId,
          eventType: input.type,
          payload: input.payload,
          correlationId: input.correlationId ?? null
        });
        bus.publish(event);
        await push.send(db.listDevices(event.pokeUserId), event);
        return json(res, 202, { event });
      }
      if (req.method === "POST" && url.pathname.startsWith("/webhooks/")) {
        if (env.webhookSecret && req.headers.authorization !== `Bearer ${env.webhookSecret}`) {
          return json(res, 401, { error: "unauthorized" });
        }
        const source = decodeURIComponent(url.pathname.slice("/webhooks/".length));
        const payload = await readJson(req) as Record<string, unknown>;
        const id = db.insertLiveData({ source, payload, metadata: { userAgent: req.headers["user-agent"] } });
        let event = null;
        if (typeof payload.pokeUserId === "string" && payload.pokeUserId.trim().length > 0) {
          event = db.insertEvent({
            eventId: typeof payload.eventId === "string" ? payload.eventId : id,
            pokeUserId: payload.pokeUserId,
            eventType: typeof payload.type === "string" && isEventType(payload.type) ? payload.type : "notification",
            payload: {
              source,
              text: typeof payload.text === "string" ? payload.text : `Webhook received from ${source}`,
              summary: typeof payload.summary === "string" ? payload.summary : undefined,
              actions: Array.isArray(payload.actions) ? payload.actions : []
            },
            correlationId: typeof payload.correlationId === "string" ? payload.correlationId : null
          });
          bus.publish(event);
          await push.send(db.listDevices(event.pokeUserId), event);
        }
        return json(res, 202, { id, event });
      }
      if (req.method === "GET" && url.pathname === "/api/live-data/query") {
        return json(res, 200, {
          rows: db.queryLiveData(url.searchParams.get("source") ?? undefined, Number(url.searchParams.get("limit") ?? 50))
        });
      }
      if (req.method === "GET" && url.pathname === "/poke/sse") {
        return pokeIntegrationSse(req, res);
      }
      return json(res, 404, { error: "not_found" });
    } catch (error) {
      const message = error instanceof Error ? error.message : "unknown error";
      return json(res, message.includes("required") ? 400 : 500, { error: message });
    }
  }

  return { route, db, bus };
}

function streamEvents(
  req: IncomingMessage,
  res: ServerResponse,
  db: AppDatabase,
  bus: EventBus,
  pokeUserId: string,
  since?: string
): void {
  res.writeHead(200, {
    "content-type": "text/event-stream",
    "cache-control": "no-cache, no-transform",
    connection: "keep-alive"
  });
  for (const event of db.listEvents(pokeUserId, since)) {
    writeSse(res, "conversation_event", event.eventId, event);
  }
  const unsubscribe = bus.subscribe(pokeUserId, event => writeSse(res, "conversation_event", event.eventId, event));
  const heartbeat = setInterval(() => res.write(": heartbeat\n\n"), 25_000);
  req.on("close", () => {
    clearInterval(heartbeat);
    unsubscribe();
  });
}

function pokeIntegrationSse(req: IncomingMessage, res: ServerResponse): void {
  res.writeHead(200, {
    "content-type": "text/event-stream",
    "cache-control": "no-cache, no-transform",
    connection: "keep-alive"
  });
  writeSse(res, "ready", "ready", {
    name: "Poke Android Client",
    tools: ["deliver_message", "deliver_action", "deliver_status", "request_client_context"]
  });
  const heartbeat = setInterval(() => res.write(": heartbeat\n\n"), 25_000);
  req.on("close", () => clearInterval(heartbeat));
}

function writeSse(res: ServerResponse, event: string, id: string, data: unknown): void {
  res.write(`id: ${id}\n`);
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

async function readJson(req: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  const raw = Buffer.concat(chunks).toString("utf8");
  return raw.length ? JSON.parse(raw) : {};
}

function requiredQuery(url: URL, key: string): string {
  const value = url.searchParams.get(key);
  if (!value) throw new Error(`${key} is required`);
  return value;
}

function json(res: ServerResponse, status: number, body: unknown): void {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
}

function isEventType(value: string): value is "message" | "action" | "status" | "log" | "progress" | "notification" | "incoming_request" {
  return ["message", "action", "status", "log", "progress", "notification", "incoming_request"].includes(value);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const env = loadEnv();
  const app = createApp();
  const server = createServer((req, res) => void app.route(req, res));
  server.listen(env.port, () => console.info(`Poke Android backend listening on :${env.port}`));

  const sse = new PokeSseClient(
    {
      url: process.env.POKE_SSE_URL,
      pokeUserId: process.env.POKE_USER_ID
    },
    app.db,
    app.bus,
    new LoggingPushGateway()
  );
  void sse.start();
}

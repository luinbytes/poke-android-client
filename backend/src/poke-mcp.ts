import type { IncomingMessage, ServerResponse } from "node:http";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import type { RequestHandlerExtra } from "@modelcontextprotocol/sdk/shared/protocol.js";
import type { ServerNotification, ServerRequest } from "@modelcontextprotocol/sdk/types.js";
import { z } from "zod";
import type { AppDatabase } from "./database.js";
import type { EventBus } from "./event-bus.js";
import type { PushGateway } from "./push.js";
import type { ConversationEvent, EventType } from "./types.js";

type McpExtra = RequestHandlerExtra<ServerRequest, ServerNotification>;

type PokeMcpDeps = {
  db: AppDatabase;
  bus: EventBus;
  push: PushGateway;
  publicMcpBaseUrl?: string;
};

const ToolPayload = z.record(z.string(), z.any()).optional();
const ActionSchema = z.object({
  id: z.string().optional(),
  type: z.string().default("quick_reply"),
  label: z.string(),
  payload: z.record(z.string(), z.any()).optional()
});

export class PokeMcpBridge {
  private readonly transports = new Map<string, SSEServerTransport>();

  constructor(private readonly deps: PokeMcpDeps) {}

  async handleSse(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const endpoint = this.deps.publicMcpBaseUrl
      ? `${this.deps.publicMcpBaseUrl.replace(/\/$/, "")}/messages`
      : "/poke/messages";
    const transport = new SSEServerTransport(endpoint, res);
    this.transports.set(transport.sessionId, transport);
    transport.onclose = () => this.transports.delete(transport.sessionId);
    const server = this.buildServer();
    await server.connect(transport);
  }

  async handleMessage(req: IncomingMessage, res: ServerResponse): Promise<void> {
    const url = new URL(req.url ?? "/", "http://localhost");
    const sessionId = url.searchParams.get("sessionId");
    const transport = sessionId ? this.transports.get(sessionId) : undefined;
    if (!transport) {
      res.writeHead(404, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: "unknown_mcp_session" }));
      return;
    }
    await transport.handlePostMessage(req, res);
  }

  private buildServer(): McpServer {
    const server = new McpServer({
      name: "poke-android-client",
      version: "0.1.0"
    });

    server.registerTool(
      "deliver_message",
      {
        title: "Deliver Android-visible message",
        description: "Deliver a Poke message or notification into the Android client's event stream.",
        inputSchema: z.object({
          pokeUserId: z.string().optional(),
          text: z.string(),
          summary: z.string().optional(),
          eventId: z.string().optional(),
          correlationId: z.string().optional(),
          actions: z.array(ActionSchema).optional(),
          payload: ToolPayload
        })
      },
      async (input, extra) => this.storeToolEvent("message", input, extra)
    );

    server.registerTool(
      "deliver_action",
      {
        title: "Deliver Android action",
        description: "Deliver a Poke action card with quick replies or approval buttons.",
        inputSchema: z.object({
          pokeUserId: z.string().optional(),
          text: z.string().optional(),
          summary: z.string().optional(),
          eventId: z.string().optional(),
          correlationId: z.string().optional(),
          actions: z.array(ActionSchema).min(1),
          payload: ToolPayload
        })
      },
      async (input, extra) => this.storeToolEvent("action", input, extra)
    );

    server.registerTool(
      "deliver_status",
      {
        title: "Deliver Android status",
        description: "Deliver a progress, status, log, or notification row to Android.",
        inputSchema: z.object({
          pokeUserId: z.string().optional(),
          type: z.enum(["status", "progress", "log", "notification"]).default("status"),
          text: z.string(),
          summary: z.string().optional(),
          eventId: z.string().optional(),
          correlationId: z.string().optional(),
          payload: ToolPayload
        })
      },
      async (input, extra) => this.storeToolEvent(input.type, input, extra)
    );

    server.registerTool(
      "request_client_context",
      {
        title: "Read Android client context",
        description: "Read recent Android-visible conversation events and ingested live data.",
        inputSchema: z.object({
          pokeUserId: z.string().optional(),
          liveDataSource: z.string().optional(),
          eventLimit: z.number().int().min(1).max(50).default(20),
          liveDataLimit: z.number().int().min(1).max(50).default(20)
        })
      },
      async (input, extra) => {
        const pokeUserId = requirePokeUserId(input.pokeUserId, extra);
        const events = this.deps.db.listEvents(pokeUserId).slice(-input.eventLimit);
        const liveData = this.deps.db.queryLiveData(input.liveDataSource, input.liveDataLimit);
        return toolJson({
          ok: true,
          pokeUserId,
          events,
          liveData
        });
      }
    );

    return server;
  }

  private async storeToolEvent(
    eventType: EventType,
    input: {
      pokeUserId?: string;
      text?: string;
      summary?: string;
      eventId?: string;
      correlationId?: string;
      actions?: Array<{ id?: string; type: string; label: string; payload?: Record<string, unknown> }>;
      payload?: Record<string, unknown>;
    },
    extra: McpExtra
  ) {
    const pokeUserId = requirePokeUserId(input.pokeUserId, extra);
    const payload = {
      ...(input.payload ?? {}),
      text: input.text,
      summary: input.summary,
      actions: input.actions ?? []
    };
    const event = this.deps.db.insertEvent({
      eventId: input.eventId,
      pokeUserId,
      eventType,
      payload,
      correlationId: input.correlationId ?? null,
      deliveryState: "delivered"
    });
    this.deps.bus.publish(event);
    await this.deps.push.send(this.deps.db.listDevices(event.pokeUserId), event);
    return toolJson({
      ok: true,
      event: publicEvent(event)
    });
  }
}

function requirePokeUserId(inputUserId: string | undefined, extra: McpExtra): string {
  const fromHeader = headerValue(extra, "x-poke-user-id");
  const pokeUserId = inputUserId?.trim() || fromHeader?.trim();
  if (!pokeUserId) {
    throw new Error("pokeUserId or X-Poke-User-Id header is required");
  }
  return pokeUserId;
}

function headerValue(extra: McpExtra, name: string): string | undefined {
  const headers = extra.requestInfo?.headers ?? {};
  const value = headers[name] ?? headers[name.toLowerCase()] ?? headers[name.toUpperCase()];
  return Array.isArray(value) ? value[0] : value;
}

function publicEvent(event: ConversationEvent): ConversationEvent {
  return event;
}

function toolJson(data: unknown) {
  return {
    content: [
      {
        type: "text" as const,
        text: JSON.stringify(data)
      }
    ]
  };
}

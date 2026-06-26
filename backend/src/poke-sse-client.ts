import type { AppDatabase } from "./database.js";
import type { EventBus } from "./event-bus.js";
import type { PushGateway } from "./push.js";
import type { HandlerKind } from "./types.js";

export type PokeSseConfig = {
  url?: string;
  pokeUserId?: string;
  reconnectDelayMs?: number;
};

export class PokeSseClient {
  private stopped = false;
  private lastEventId: string | undefined;

  constructor(
    private readonly config: PokeSseConfig,
    private readonly db: AppDatabase,
    private readonly bus: EventBus,
    private readonly push: PushGateway
  ) {}

  async start(): Promise<void> {
    if (!this.config.url || !this.config.pokeUserId) {
      console.info("Poke SSE disabled; set POKE_SSE_URL and POKE_USER_ID to enable");
      return;
    }
    while (!this.stopped) {
      try {
        await this.connectOnce();
      } catch (error) {
        console.warn("Poke SSE disconnected", error instanceof Error ? error.message : error);
      }
      await delay(this.config.reconnectDelayMs ?? 2_000);
    }
  }

  stop(): void {
    this.stopped = true;
  }

  private async connectOnce(): Promise<void> {
    const response = await fetch(this.config.url!, {
      headers: {
        accept: "text/event-stream",
        ...(this.lastEventId ? { "last-event-id": this.lastEventId } : {})
      }
    });
    if (!response.ok || !response.body) {
      throw new Error(`SSE HTTP ${response.status}`);
    }
    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = "";
    while (!this.stopped) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;
      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        const raw = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        await this.handleSseFrame(raw);
        boundary = buffer.indexOf("\n\n");
      }
    }
  }

  private async handleSseFrame(raw: string): Promise<void> {
    const lines = raw.split("\n");
    let event = "message";
    let id: string | undefined;
    const data: string[] = [];
    for (const line of lines) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      if (line.startsWith("id:")) id = line.slice(3).trim();
      if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
    }
    if (id) this.lastEventId = id;
    const payload = safeJson(data.join("\n"));
    const handlerKind = mapEvent(event);
    const stored = this.db.insertEvent({
      eventId: id,
      pokeUserId: this.config.pokeUserId!,
      eventType: handlerKind,
      payload,
      correlationId: typeof payload === "object" && payload && "correlationId" in payload ? String((payload as any).correlationId) : null
    });
    this.bus.publish(stored);
    await this.push.send(this.db.listDevices(stored.pokeUserId), stored);
  }
}

function mapEvent(event: string): HandlerKind {
  if (event === "log") return "log";
  if (event === "progress") return "progress";
  if (event === "notification") return "notification";
  return "incoming_request";
}

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return { text: raw };
  }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { SSEClientTransport } from "@modelcontextprotocol/sdk/client/sse.js";
import { AppDatabase } from "../src/database.js";
import { createApp } from "../src/server.js";
import type { PokeMessageSender, PokeSendResult } from "../src/poke-api.js";

test("backend send fails clearly when POKE_API_KEY is missing", async () => {
  const db = testDb();
  const app = createApp({
    db,
    pokeApi: mockSender({ ok: false, message: "POKE_API_KEY is not configured on the backend" })
  });
  const server = createServer((req, res) => void app.route(req, res));
  await listen(server);

  const response = await fetch(`${baseUrl(server)}/api/messages/send`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ pokeUserId: "poke-user-1", text: "hello" })
  });
  const body = await response.json() as any;

  assert.equal(response.status, 503);
  assert.match(body.error, /POKE_API_KEY/);
  assert.equal(body.event.deliveryState, "failed");
  assert.equal(db.listEvents("poke-user-1")[0].deliveryState, "failed");
  server.close();
  db.close();
});

test("backend send updates queued event to delivered after Poke accepts it", async () => {
  const db = testDb();
  const app = createApp({
    db,
    pokeApi: mockSender({ ok: true, upstreamStatus: 202 })
  });
  const server = createServer((req, res) => void app.route(req, res));
  await listen(server);

  const response = await fetch(`${baseUrl(server)}/api/messages/send`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ pokeUserId: "poke-user-1", text: "hello" })
  });
  const body = await response.json() as any;

  assert.equal(response.status, 202);
  assert.equal(body.event.deliveryState, "delivered");
  assert.equal(db.listEvents("poke-user-1")[0].deliveryState, "delivered");
  server.close();
  db.close();
});

test("MCP deliver_message stores a normalized conversation event", async () => {
  const db = testDb();
  const app = createApp({ db, pokeApi: mockSender({ ok: true }) });
  const server = createServer((req, res) => void app.route(req, res));
  await listen(server);

  const client = new Client({ name: "test-poke-client", version: "0.0.0" });
  const transport = new SSEClientTransport(new URL(`${baseUrl(server)}/poke/sse`));
  await client.connect(transport);
  await client.callTool({
    name: "deliver_message",
    arguments: {
      pokeUserId: "poke-user-1",
      text: "Inbound from Poke",
      actions: [{ id: "approve", type: "confirm", label: "Approve" }]
    }
  });

  const events = db.listEvents("poke-user-1");
  assert.equal(events.length, 1);
  assert.equal(events[0].eventType, "message");
  assert.equal((events[0].payload as any).text, "Inbound from Poke");
  assert.equal((events[0].payload as any).actions[0].label, "Approve");

  await client.close();
  server.close();
  db.close();
});

function testDb(): AppDatabase {
  return new AppDatabase(join(mkdtempSync(join(tmpdir(), "poke-server-")), "test.db"));
}

function mockSender(result: PokeSendResult): PokeMessageSender {
  return {
    configured: result.ok,
    async sendMessage() {
      return result;
    }
  };
}

async function listen(server: ReturnType<typeof createServer>): Promise<void> {
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
}

function baseUrl(server: ReturnType<typeof createServer>): string {
  const address = server.address();
  assert(address && typeof address === "object");
  return `http://127.0.0.1:${address.port}`;
}

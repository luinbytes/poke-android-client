import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { AppDatabase } from "../src/database.js";

test("registers devices and stores conversation events idempotently", () => {
  const db = new AppDatabase(join(mkdtempSync(join(tmpdir(), "poke-db-")), "test.db"));
  const device = db.upsertDevice({ pokeUserId: "poke-user-1", fcmToken: "token", deviceName: "Pixel" });
  assert.equal(device.pokeUserId, "poke-user-1");
  assert.equal(db.listDevices("poke-user-1").length, 1);

  const first = db.insertEvent({
    eventId: "event-1",
    pokeUserId: "poke-user-1",
    eventType: "notification",
    payload: { text: "hello" }
  });
  const duplicate = db.insertEvent({
    eventId: "event-1",
    pokeUserId: "poke-user-1",
    eventType: "notification",
    payload: { text: "ignored" }
  });

  assert.deepEqual(duplicate.payload, first.payload);
  assert.equal(db.listEvents("poke-user-1").length, 1);
  db.close();
});

test("stores and queries live data", () => {
  const db = new AppDatabase(join(mkdtempSync(join(tmpdir(), "poke-db-")), "test.db"));
  db.insertLiveData({ source: "github", payload: { action: "opened" } });
  assert.equal((db.queryLiveData("github")[0] as any).payload.action, "opened");
  db.close();
});

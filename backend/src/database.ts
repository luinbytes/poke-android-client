import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { randomUUID } from "node:crypto";
import type { ConversationEvent, DeliveryState, Device, EventType } from "./types.js";

export class AppDatabase {
  private readonly db: DatabaseSync;

  constructor(path: string) {
    mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec("PRAGMA journal_mode = WAL");
    this.db.exec("PRAGMA foreign_keys = ON");
    this.migrate();
  }

  close(): void {
    this.db.close();
  }

  upsertDevice(input: {
    pokeUserId: string;
    deviceId?: string;
    fcmToken?: string | null;
    deviceName?: string | null;
  }): Device {
    const now = new Date().toISOString();
    const deviceId = input.deviceId ?? randomUUID();
    this.db.prepare(
      `INSERT INTO users (poke_user_id, created_at, last_seen_at)
       VALUES (?, ?, ?)
       ON CONFLICT(poke_user_id) DO UPDATE SET last_seen_at = excluded.last_seen_at`
    ).run(input.pokeUserId, now, now);
    this.db.prepare(
      `INSERT INTO devices (device_id, poke_user_id, fcm_token, device_name, active, last_seen_at)
       VALUES (?, ?, ?, ?, 1, ?)
       ON CONFLICT(device_id) DO UPDATE SET
         poke_user_id = excluded.poke_user_id,
         fcm_token = excluded.fcm_token,
         device_name = excluded.device_name,
         active = 1,
         last_seen_at = excluded.last_seen_at`
    ).run(deviceId, input.pokeUserId, input.fcmToken ?? null, input.deviceName ?? null, now);
    return {
      deviceId,
      pokeUserId: input.pokeUserId,
      fcmToken: input.fcmToken ?? null,
      deviceName: input.deviceName ?? null,
      active: true,
      lastSeenAt: now
    };
  }

  listDevices(pokeUserId: string): Device[] {
    return this.db.prepare(
      `SELECT device_id, poke_user_id, fcm_token, device_name, active, last_seen_at
       FROM devices WHERE poke_user_id = ? AND active = 1`
    ).all(pokeUserId).map(rowToDevice);
  }

  insertEvent(input: {
    eventId?: string;
    pokeUserId: string;
    deviceId?: string | null;
    eventType: EventType;
    payload: unknown;
    correlationId?: string | null;
    deliveryState?: DeliveryState;
  }): ConversationEvent {
    const createdAt = new Date().toISOString();
    const eventId = input.eventId ?? randomUUID();
    const payload = JSON.stringify(input.payload ?? {});
    const deliveryState = input.deliveryState ?? "queued";
    this.db.prepare(
      `INSERT OR IGNORE INTO conversation_events
       (event_id, poke_user_id, device_id, event_type, payload_json, correlation_id, created_at, delivery_state)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
    ).run(
      eventId,
      input.pokeUserId,
      input.deviceId ?? null,
      input.eventType,
      payload,
      input.correlationId ?? null,
      createdAt,
      deliveryState
    );
    return this.getEvent(eventId) ?? {
      eventId,
      pokeUserId: input.pokeUserId,
      deviceId: input.deviceId ?? null,
      eventType: input.eventType,
      payload: input.payload,
      correlationId: input.correlationId ?? null,
      createdAt,
      deliveryState
    };
  }

  getEvent(eventId: string): ConversationEvent | null {
    const row = this.db.prepare(
      `SELECT event_id, poke_user_id, device_id, event_type, payload_json, correlation_id, created_at, delivery_state
       FROM conversation_events WHERE event_id = ?`
    ).get(eventId);
    return row ? rowToEvent(row) : null;
  }

  listEvents(pokeUserId: string, sinceEventId?: string): ConversationEvent[] {
    if (!sinceEventId) {
      return this.db.prepare(
        `SELECT event_id, poke_user_id, device_id, event_type, payload_json, correlation_id, created_at, delivery_state
         FROM conversation_events WHERE poke_user_id = ? ORDER BY created_at ASC LIMIT 250`
      ).all(pokeUserId).map(rowToEvent);
    }
    const since = this.db.prepare(
      `SELECT created_at FROM conversation_events WHERE event_id = ? AND poke_user_id = ?`
    ).get(sinceEventId, pokeUserId) as { created_at: string } | undefined;
    return this.db.prepare(
      `SELECT event_id, poke_user_id, device_id, event_type, payload_json, correlation_id, created_at, delivery_state
       FROM conversation_events
       WHERE poke_user_id = ? AND created_at > ?
       ORDER BY created_at ASC LIMIT 250`
    ).all(pokeUserId, since?.created_at ?? "1970-01-01T00:00:00.000Z").map(rowToEvent);
  }

  insertLiveData(input: { source: string; payload: unknown; metadata?: unknown }): string {
    const id = randomUUID();
    this.db.prepare(
      `INSERT INTO live_data (id, source, payload_json, metadata_json, created_at)
       VALUES (?, ?, ?, ?, ?)`
    ).run(id, input.source, JSON.stringify(input.payload), JSON.stringify(input.metadata ?? {}), new Date().toISOString());
    return id;
  }

  queryLiveData(source?: string, limit = 50): unknown[] {
    const boundedLimit = Math.max(1, Math.min(limit, 250));
    const rows = source
      ? this.db.prepare(
          `SELECT id, source, payload_json, metadata_json, created_at FROM live_data
           WHERE source = ? ORDER BY created_at DESC LIMIT ?`
        ).all(source, boundedLimit)
      : this.db.prepare(
          `SELECT id, source, payload_json, metadata_json, created_at FROM live_data
           ORDER BY created_at DESC LIMIT ?`
        ).all(boundedLimit);
    return rows.map((row: any) => ({
      id: row.id,
      source: row.source,
      payload: JSON.parse(row.payload_json),
      metadata: JSON.parse(row.metadata_json),
      createdAt: row.created_at
    }));
  }

  private migrate(): void {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS users (
        poke_user_id TEXT PRIMARY KEY,
        created_at TEXT NOT NULL,
        last_seen_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS devices (
        device_id TEXT PRIMARY KEY,
        poke_user_id TEXT NOT NULL REFERENCES users(poke_user_id) ON DELETE CASCADE,
        fcm_token TEXT,
        device_name TEXT,
        active INTEGER NOT NULL,
        last_seen_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS conversation_events (
        event_id TEXT PRIMARY KEY,
        poke_user_id TEXT NOT NULL,
        device_id TEXT,
        event_type TEXT NOT NULL,
        payload_json TEXT NOT NULL,
        correlation_id TEXT,
        created_at TEXT NOT NULL,
        delivery_state TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS live_data (
        id TEXT PRIMARY KEY,
        source TEXT NOT NULL,
        payload_json TEXT NOT NULL,
        metadata_json TEXT NOT NULL,
        created_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS outbound_operations (
        id TEXT PRIMARY KEY,
        method TEXT NOT NULL,
        params_json TEXT NOT NULL,
        correlation_id TEXT,
        retry_state TEXT NOT NULL,
        result_json TEXT,
        error_json TEXT,
        created_at TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
    `);
  }
}

function rowToDevice(row: any): Device {
  return {
    deviceId: row.device_id,
    pokeUserId: row.poke_user_id,
    fcmToken: row.fcm_token,
    deviceName: row.device_name,
    active: Boolean(row.active),
    lastSeenAt: row.last_seen_at
  };
}

function rowToEvent(row: any): ConversationEvent {
  return {
    eventId: row.event_id,
    pokeUserId: row.poke_user_id,
    deviceId: row.device_id,
    eventType: row.event_type,
    payload: JSON.parse(row.payload_json),
    correlationId: row.correlation_id,
    createdAt: row.created_at,
    deliveryState: row.delivery_state
  };
}

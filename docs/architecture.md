# Architecture

The app gives Poke users a dedicated Android path when RCS feels slower or less predictable than Apple Messages, while preserving bidirectional Poke interaction.

```text
Android APK -> companion backend -> Poke inbound API
Poke MCP/SSE tool calls -> companion backend -> app SSE/history -> Android APK
```

## Transport

The companion backend owns the Poke-facing MCP/SSE integration. Phones are not reliable places for long-lived receive sessions because Android background work can be paused, killed, or delayed. Poke connects to `/poke/sse`, then posts MCP messages back to `/poke/messages`.

## Reading

The backend normalizes incoming MCP tool calls into durable `conversation_events` rows:

- incoming requests
- logs
- progress events
- notification events

Foreground Android sessions subscribe to `/api/events/stream`. Background delivery is represented by the `PushGateway` abstraction, which can be backed by Firebase Cloud Messaging in production.

## Writing

The Android app sends to the backend. The backend forwards messages to Poke with:

```http
POST https://poke.com/api/v1/inbound/api-message
Authorization: Bearer <POKE_API_KEY>
Content-Type: application/json
```

`POKE_API_KEY` is a backend environment variable. The Android app does not need the key in normal mode.

## Ingest

External systems can post arbitrary JSON to `/webhooks/:source`. The backend validates webhook auth when `WEBHOOK_SECRET` is set, stores raw payloads in `live_data`, and exposes `/api/live-data/query` for scoped SQL-like search over recent rows.

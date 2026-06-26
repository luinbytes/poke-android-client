# Architecture

The app gives Poke users a dedicated Android path when RCS feels slower or less predictable than Apple Messages, while preserving bidirectional Poke interaction.

```text
Android APK -> companion backend -> Poke JSON-RPC/API
Poke SSE message handler -> companion backend -> FCM/SSE -> Android APK
```

## Transport

The companion backend owns the long-lived Poke-facing SSE session. Phones are not reliable places for this session because Android background work can be paused, killed, or delayed. The backend reconnect loop tracks the last event cursor and is structured for SEP-1699-style auto-reconnect behavior across load balancer timeouts.

## Reading

The backend normalizes incoming handler callbacks into durable `conversation_events` rows:

- incoming requests
- logs
- progress events
- notification events

Foreground Android sessions subscribe to `/api/events/stream`. Background delivery is represented by the `PushGateway` abstraction, which can be backed by Firebase Cloud Messaging in production.

## Writing

The Android app supports direct Poke sends with:

```http
POST https://poke.com/api/v1/inbound/api-message
Authorization: Bearer <POKE_API_KEY>
Content-Type: application/json
```

The backend also exposes `/api/messages/send` and a `JsonRpcClient` for tool/template/resource calls once account/session auth is available.

## Ingest

External systems can post arbitrary JSON to `/webhooks/:source`. The backend validates webhook auth when `WEBHOOK_SECRET` is set, stores raw payloads in `live_data`, and exposes `/api/live-data/query` for scoped SQL-like search over recent rows.

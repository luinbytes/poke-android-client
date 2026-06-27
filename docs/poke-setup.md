# Poke Setup

## Backend Key

The backend owns the Poke API key for this phase. Do not put real keys in Git.

```bash
cd backend
cp .env.example .env
$EDITOR .env
set -a
source .env
set +a
npm run dev
```

Required value:

```bash
POKE_API_KEY=replace_with_your_poke_api_key
```

If a key was pasted into chat, rotate it in Poke after the first proof run if key rotation is available.

## Android Setup

Install the debug APK, then enter:

- Backend URL, for local Samsung QA: `http://127.0.0.1:8787`
- Poke user ID, matching the Poke account/session this bridge should represent

When testing over USB:

```bash
adb reverse tcp:8787 tcp:8787
```

## Local Poke MCP Tunnel

Start the backend, expose it with Poke's tunnel flow, then register the MCP/SSE URL:

```bash
npx poke@latest login
npx poke@latest tunnel http://localhost:8787/poke/sse -n "Android Poke Client"
```

When using `tunnel.poke.com`, restart the backend with the tunnel base so the MCP
SSE endpoint advertises the matching tunneled message URL:

```bash
PUBLIC_MCP_BASE_URL=https://tunnel.poke.com/YOUR_CONNECTION_ID/poke npm run dev
```

The backend exposes these Poke-callable MCP tools:

- `deliver_message`
- `deliver_action`
- `deliver_status`
- `request_client_context`

Poke should pass `X-Poke-User-Id` on tool calls. Each tool also accepts `pokeUserId` in its arguments for local testing.

## Live Poke QA

1. Start the backend with `POKE_API_KEY` exported.
2. Install and launch the Android APK on the Samsung device.
3. Save backend URL and Poke user ID in the app.
4. Send a message from Android and verify the backend forwards it to Poke.
5. Trigger a Poke MCP tool call through the local tunnel.
6. Verify the inbound event appears in the foreground stream.
7. Relaunch the app and verify the same event appears from history sync.

Firebase Cloud Messaging is intentionally deferred. Background proof for this phase is history sync after relaunch.

# Poke Setup

## Direct Send

1. Open Poke Kitchen.
2. Create a V2 API key.
3. Paste the key into the Android app settings.
4. Send a test message from the app.

## Backend Integration

Add the backend as an MCP/SSE integration:

```bash
npx poke@latest mcp add https://YOUR_BACKEND_HOST/poke/sse -n "Android Poke Client"
```

The backend exposes delivery tools conceptually named:

- `deliver_message`
- `deliver_action`
- `deliver_status`
- `request_client_context`

When Poke invokes those handlers, the backend persists the event and forwards it to Android.

## Recipe Path

After the backend URL is stable, package the MCP integration in Kitchen as a Poke Recipe so users can install it from a share link instead of running CLI commands.

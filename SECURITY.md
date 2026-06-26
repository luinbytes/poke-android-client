# Security

Please do not open public issues with credentials, API keys, tokens, private message content, or vulnerable deployment details.

For now, report security concerns privately to the repository owner.

## Sensitive Data

- The Android app stores Poke API keys locally using Android encrypted storage.
- The backend should not receive Poke API keys unless backend-mediated sending is explicitly enabled.
- Backend logs should include event IDs and statuses, not message bodies.
- Webhook ingest should be protected with `WEBHOOK_SECRET` in public deployments.

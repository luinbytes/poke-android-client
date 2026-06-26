# Samsung QA Notes

Test device:

- Model: Samsung SM-S906E
- Serial: RZCT81C29ND
- Android: 16, API 36

Test setup:

- Backend: local Node service on `127.0.0.1:8787`
- Device routing: `adb reverse tcp:8787 tcp:8787`
- App mode: debug-only local QA backend shortcut
- Network: debug manifest permits cleartext localhost traffic for ADB-reversed backend testing
- QA user ID: `qa-samsung`

Verified:

- Debug APK installs and launches on the Samsung.
- Setup screen can enter local QA mode without stored credentials.
- Normal setup save with `http://127.0.0.1:8787` and `persist-qa` succeeds without ANR and relaunches into the connected chat path from encrypted settings.
- App registers/listens against the local backend through ADB reverse.
- Sending from chat without a backend or Poke API key fails cleanly with `Add a backend URL or Poke API key before sending`.
- Failed outbound messages render a `Retry` chip.
- Webhook-created events posted to `/webhooks/samsung-qa` appear in the app through `/api/events/stream`.
- Rich action chips render from webhook payloads.
- Tapping a rich action adds an outbound action acknowledgement in the conversation.
- Backend-mediated sends from the composer create `conversation_events` rows.
- App survives background/return.
- App remains in chat after rotation during a local QA session.

Notes:

- Real direct Poke sends require a valid Poke API key from Poke Kitchen.
- Real Poke receive requires connecting the backend to Poke's handler/SSE flow.
- Background FCM delivery is represented by the backend `PushGateway` abstraction, but Firebase credentials are not wired in this local QA run.

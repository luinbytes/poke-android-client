# Contributing

Thanks for helping improve Poke Android Client.

## Development

- Keep the Android app usable as a sideloaded APK.
- Keep the backend deployable as a small public HTTPS service.
- Do not commit API keys, FCM service account files, `.env`, keystores, or message logs.
- Prefer official Poke APIs and documented SSE/JSON-RPC behavior.
- Avoid scraping private services or relying on RCS/SMS/notification access for core functionality.

## Checks

```bash
cd backend
npm test
npm run build
```

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug
```

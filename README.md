# Universal Music (Phase 1 + 2)

Client-only Android app (Kotlin, Compose, Hilt, Media3) that searches Audius live
and streams results. No backend.

## Getting the APK

### Option A — GitHub Actions (no local setup)
1. Push this folder to a GitHub repo (branch `main`).
2. Open **Actions → Build APK** (runs on every push, or run it manually).
3. Download the `UniversalMusic-debug-apk` artifact → `app-debug.apk`.

### Option B — Android Studio
1. Open the folder, let Gradle sync (JDK 17).
2. **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

### Option C — command line
```
gradle wrapper --gradle-version 8.10.2   # once, generates gradlew + wrapper jar
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Optional config
`local.properties` (git-ignored) or CI env vars / secrets:
```
AUDIUS_APP_NAME=YourAppName
AUDIUS_API_KEY=
```
The debug APK is signed with the debug key; configure a release keystore separately for production.

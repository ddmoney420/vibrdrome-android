# Vibrdrome Android

Music player for Navidrome/Subsonic servers.

## Build
```
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
```

## Install on device
```
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug
```

## ADB
```
"C:/Users/rdpuser.BASEMENT/AppData/Local/Android/Sdk/platform-tools/adb.exe"
```

## Architecture
- Kotlin + Jetpack Compose + Material 3
- Media3 ExoPlayer for playback
- Ktor for networking (Subsonic API)
- Room for persistence
- Koin for DI
- C++/JNI for projectM visualizer
- No ViewModels — state in manager singletons via StateFlow

## Key Directories
- `app/src/main/java/com/vibrdrome/app/audio/` — playback, EQ, haptics, jukebox
- `app/src/main/java/com/vibrdrome/app/network/` — Subsonic API client, models, endpoints
- `app/src/main/java/com/vibrdrome/app/ui/` — Compose screens
- `app/src/main/java/com/vibrdrome/app/persistence/` — Room DB, offline queue
- `app/src/main/java/com/vibrdrome/app/cast/` — Chromecast
- `app/src/main/java/com/vibrdrome/app/downloads/` — download manager, cache
- `app/src/main/cpp/` — projectM JNI bridge

## Branch Strategy
- `main` — releases only, protected (PR + CI required)
- `develop` — integration branch, all work branches from here

## Versioning
- Semantic versioning with pre-release tags for test builds
- `X.Y.Z-alpha.N` → testing, `X.Y.Z-rc.N` → release candidate, `X.Y.Z` → release
- Bump both versionCode and versionName in app/build.gradle.kts

## Important
- NEVER commit/push without user testing and approval first
- After code changes: build → install on phone → STOP and wait for user to test
- Version in Settings reads from BuildConfig.VERSION_NAME (dynamic)
- Signing key: vibrdrome-release.jks, passwords in local.properties
- Privacy policy: https://vibrdrome.io/privacy-policy.html
- Package name: com.vibrdrome.app

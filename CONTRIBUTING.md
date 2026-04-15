# Contributing to Vibrdrome (Android)

Thanks for your interest in contributing! Whether it's a bug fix, new feature, or documentation improvement — all contributions are welcome.

## Getting Started

### Prerequisites
- Android Studio (Arctic Fox or later)
- JDK 17
- Android SDK 35
- NDK 27.2.12479018

### Build
```bash
git clone https://github.com/ddmoney420/vibrdrome-android.git
cd vibrdrome-android
./gradlew :app:assembleDebug
```

### Run
Connect a device or emulator, then:
```bash
./gradlew :app:installDebug
```

## Branch Strategy

- **`main`** — stable releases only, protected (requires PR + CI pass)
- **`develop`** — integration branch for all new work

### Workflow
1. Fork the repo
2. Branch from `develop`: `git checkout -b feature/my-feature develop`
3. Make your changes
4. Run lint before committing: `./gradlew :app:lintDebug`
5. Test on a real device or emulator
6. Open a PR targeting **`develop`** with a description of what you changed and why
7. PRs to `main` happen only for releases

## Architecture

| Layer | Tech | Location |
|-------|------|----------|
| UI | Jetpack Compose, Material 3 | `ui/` |
| Navigation | Type-safe routes (kotlinx.serialization) | `ui/navigation/` |
| Playback | Media3 ExoPlayer, MediaLibraryService | `audio/` |
| Networking | Ktor client, Subsonic API | `network/` |
| Persistence | Room, SharedPreferences, EncryptedSharedPreferences | `persistence/` |
| DI | Koin | `di/AppModule.kt` |
| Native | C++/JNI (projectM visualizer) | `src/main/cpp/` |
| Casting | Google Cast SDK | `cast/` |
| Downloads | Custom download manager | `downloads/` |

All source under `app/src/main/java/com/vibrdrome/app/`.

## Code Style

- Kotlin with Compose idioms
- State via `StateFlow` + `collectAsState()` in composables
- Async via `CoroutineScope` + `launch`, no ViewModels
- DI via `koinInject()` in composables, constructor injection in managers

## Reporting Bugs

- Use the [Bug Report](https://github.com/ddmoney420/vibrdrome-android/issues/new?template=bug_report.yml) template
- Include device model, Android version, and steps to reproduce

## Requesting Features

- Use the [Feature Request](https://github.com/ddmoney420/vibrdrome-android/issues/new?template=feature_request.yml) template
- Check existing issues first to avoid duplicates

## Security

Report vulnerabilities privately — see [SECURITY.md](SECURITY.md). Do not open public issues for security concerns.

## Community

- **Discord:** [Join the server](https://discord.gg/9q5uw3CfN)
- **Website:** [vibrdrome.io](https://vibrdrome.io)

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

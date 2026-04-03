# Contributing to Vibrdrome (Android)

Thanks for your interest in contributing! Whether it's a bug fix, new feature, or documentation improvement — all contributions are welcome.

## Getting Started

```bash
# Prerequisites: Android Studio, JDK 17+

git clone https://github.com/ddmoney420/vibrdrome-android.git
cd vibrdrome-android
./gradlew assembleDebug
```

Open the project in Android Studio for the full IDE experience.

## Development Workflow

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Make your changes
4. Test on a real device or emulator
5. Open a PR with a description of what you changed and why

## Project Structure

```
app/src/main/
  java/com/vibrdrome/
    api/          Subsonic API client and models
    audio/        Media3/ExoPlayer playback engine
    data/         Room database, repositories
    ui/           Jetpack Compose screens
    visualizer/   projectM Milkdrop integration
```

## Reporting Bugs

- Use the [Bug Report](https://github.com/ddmoney420/vibrdrome-android/issues/new?template=bug_report.md) template
- Include device model, Android version, and steps to reproduce

## Requesting Features

- Use the [Feature Request](https://github.com/ddmoney420/vibrdrome-android/issues/new?template=feature_request.md) template
- Check existing issues first to avoid duplicates

## Community

- **Discord:** [Join the server](https://discord.gg/9q5uw3CfN)
- **Website:** [vibrdrome.io](https://vibrdrome.io)

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).

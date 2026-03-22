# Vibrdrome Android — Play Store Submission Guide

## Step 1: Generate a New Signing Key

The current keystore password was visible during development. Generate a fresh one:

```bash
# Run this from the project root
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkey -v ^
  -keystore vibrdrome-release.jks ^
  -keyalg RSA -keysize 2048 -validity 10000 ^
  -alias vibrdrome
```

It will prompt you for:
- **Keystore password**: choose something strong, write it down
- **Key password**: can be the same as keystore password
- **Name, Organization, etc.**: fill in or press Enter to skip

**CRITICAL: Back up this .jks file and password somewhere safe (USB drive, password manager). If you lose it, you can NEVER update the app on Play Store.**

## Step 2: Update local.properties

Open `C:\My-Workspace\Vibrdrome-android\local.properties` and update:

```properties
RELEASE_STORE_FILE=../vibrdrome-release.jks
RELEASE_STORE_PASSWORD=your_new_password_here
RELEASE_KEY_ALIAS=vibrdrome
RELEASE_KEY_PASSWORD=your_new_password_here
```

## Step 3: Build the Release Bundle

```bash
cd C:\My-Workspace\Vibrdrome-android

# Set environment variables (use your new password)
set RELEASE_STORE_PASSWORD=your_new_password_here
set RELEASE_KEY_PASSWORD=your_new_password_here

# Build
gradlew.bat bundleRelease
```

Output file:
```
app\build\outputs\bundle\release\app-release.aab
```

## Step 4: Create Google Play Developer Account

1. Go to https://play.google.com/console
2. Sign in with your Google account
3. Pay the one-time $25 registration fee
4. Complete identity verification (takes 1-2 days)

## Step 5: Create the App Listing

In Play Console, click **"Create app"** and fill in:

### App Details
- **App name**: Vibrdrome
- **Default language**: English (United States)
- **App or game**: App
- **Free or paid**: Free

### Store Listing

**Short description** (80 chars max):
```
Stream your Navidrome library with Milkdrop visualizer and 10-band EQ
```

**Full description** (4000 chars max):
```
Vibrdrome is a powerful music player for your self-hosted Subsonic or Navidrome server. Browse your entire library by artists, albums, genres, playlists, folders, and decades. Play any track with gapless playback, queue management, and lock screen controls.

VISUALIZER
Real Milkdrop visualization engine (projectM) with 50 curated presets from the legendary Cream of the Crop collection. Plus 6 custom GLSL shaders — Plasma, Kaleidoscope, Tunnel, Fractal Pulse, Nebula, and Warp Speed. All driven by real-time audio FFT analysis. Swipe to cycle presets, double-tap for random.

AUDIO
10-band parametric equalizer with real-time biquad DSP processing. 10 built-in presets: Flat, Rock, Pop, Jazz, Classical, Hip Hop, Electronic, Vocal, Bass Boost, Treble Boost. Drag individual bands for your custom curve. ReplayGain support for consistent volume across tracks.

PLAYBACK
Gapless playback with crossfade. Adjustable playback speed (0.5x - 2.0x). Sleep timer with volume fade. Queue management with swipe-to-delete. Shuffle and repeat modes. Scrobbling support.

LIBRARY
14 browsing categories: Artists, Albums, Genres, Playlists, Songs, Favorites, Folders, Downloads, Radio, Recently Added, Recently Played, Generations, Random Mix, Random Album. Full-text search across your collection.

LYRICS
Synced lyrics with auto-scroll via OpenSubsonic. Tap any line to seek to that moment. Plain text fallback for non-synced lyrics.

RADIO
Stream internet radio stations from your server. Search radio-browser.info for thousands of stations worldwide. Add custom station URLs. Artist and song radio with smart deduplication.

ANDROID AUTO
Full browse tree: Artists, Albums, Playlists, Recently Added. Playback controls on your car's dashboard.

OFFLINE
Download songs for offline playback. Automatic local file resolution — downloaded songs play from storage instead of streaming.

PRIVACY
No data collection, no ads, no tracking. Your credentials are encrypted on-device. Music streams directly from your own server. Open source.

Requires a Subsonic-compatible music server (Navidrome, Airsonic, Gonic, etc.)
```

### Graphics

**App icon**: Use `AppIcon-512.png` (512x512, already in the project root)

**Feature graphic** (1024x500): Create one — dark background with the Vibrdrome waveform logo and tagline. You can use Canva or any design tool.

**Screenshots** (minimum 4): Take these from your S10e:
1. Library home screen with pills and album carousels
2. Now Playing screen with album art and controls
3. Equalizer screen with sliders
4. Visualizer (Milkdrop or GLSL shader)
5. Album detail with track list
6. Lyrics screen
7. Search results
8. Radio stations

To take screenshots via adb:
```bash
adb shell screencap /sdcard/screen.png
adb pull /sdcard/screen.png screenshot_1.png
```

### Content Rating

Go to **Policy > App content > Content rating** and complete the questionnaire:
- Violence: No
- Sexual content: No
- Language: No
- Controlled substances: No
- You'll get rated **Everyone**

### Privacy Policy

Enter: `https://vibrdrome.io/privacy-policy.html`

### App Category

- **Category**: Music & Audio
- **Tags**: music player, navidrome, subsonic, visualizer, equalizer

## Step 6: Upload the Bundle

1. Go to **Release > Production**
2. Click **"Create new release"**
3. Upload `app-release.aab`
4. Add release notes:
```
Initial release of Vibrdrome for Android.

- Stream your Navidrome/Subsonic music library
- Milkdrop visualizer with 50 presets
- 10-band parametric EQ with real-time DSP
- Synced lyrics with tap-to-seek
- Internet radio with PLS/M3U support
- Android Auto integration
- Sleep timer, crossfade, ReplayGain
- Offline downloads
```
5. Click **"Review release"** then **"Start rollout to Production"**

## Step 7: Wait for Review

Google typically reviews within a few hours to 3 days. You'll get an email when approved.

## Post-Launch

### Updating the App
1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`
2. Rebuild the bundle
3. Upload new `.aab` to Play Console
4. Add release notes
5. Roll out

### Responding to Crashes
Play Console shows crash reports via Android Vitals. Check regularly.

## File Locations Reference

| File | Path |
|------|------|
| Release bundle | `app/build/outputs/bundle/release/app-release.aab` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| Signing keystore | `vibrdrome-release.jks` (project root) |
| Keystore backup | `C:\My-Workspace\Vibrdrome-android-keystore-backup\` |
| App icon (512px) | `AppIcon-512.png` (project root) |
| Privacy policy | `https://vibrdrome.io/privacy-policy.html` |
| Source code | `https://github.com/ddmoney420/vibrdrome-android` |

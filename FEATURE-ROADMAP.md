# Vibrdrome Android — Feature Roadmap

> Comprehensive implementation plan for reaching and exceeding feature parity with Symfonium.
> Organized into 6 phases, batched for release. Each task is granular and trackable.
> Cross-platform notes (Web, iOS) included where applicable.

---

## Phase 1: Table Stakes (Chromecast + Widgets + Auto Complete)

These are the features users expect from any serious music player. Ship these first to eliminate churn reasons.

### 1.1 Chromecast Support
- [ ] Add `androidx.media3:media3-cast` dependency
- [ ] Create `CastPlayer` wrapper that implements the same playback interface as local ExoPlayer
- [ ] Implement `CastContext` initialization in Application class
- [ ] Add `MediaRouteButton` to NowPlaying screen toolbar
- [ ] Implement session listener for Cast connect/disconnect events
- [ ] Handle queue transfer: local → Cast (send current queue + position to Cast device)
- [ ] Handle queue transfer: Cast → local (resume locally when Cast disconnects)
- [ ] Implement `CastMediaItemConverter` to map Subsonic stream URLs to Cast-compatible MediaInfo
- [ ] Add transcoding format parameter for Cast streams (Cast devices prefer AAC/MP3)
- [ ] Handle cover art URL forwarding to Cast device (display on TV)
- [ ] Implement Cast mini-controller overlay (bottom bar when casting)
- [ ] Implement expanded Cast controller screen
- [ ] Handle volume control routing (device volume vs Cast volume)
- [ ] Test gapless behavior on Cast (likely not supported — handle gracefully)
- [ ] Test crossfade behavior on Cast (disable — not applicable)
- [ ] Handle Cast error states (device unavailable, network drop, session timeout)
- [ ] Add "Casting to [device name]" indicator in mini player
- [ ] Persist Cast preference (auto-reconnect to last Cast device)

> **Cross-platform**: Web app should add Chromecast via Google Cast SDK for Chrome. iOS via Google Cast iOS SDK.

### 1.2 Home Screen Widgets
- [ ] Add `androidx.glance:glance-appwidget` dependency
- [ ] Create "Now Playing" widget layout (album art, title, artist, play/pause, next, previous)
- [ ] Implement `GlanceAppWidget` subclass with Compose-based widget UI
- [ ] Create `GlanceAppWidgetReceiver` and register in AndroidManifest
- [ ] Wire widget buttons to `MediaController` transport controls via `PendingIntent`
- [ ] Implement widget state updates on track change (observe `MediaController` state)
- [ ] Support Material You dynamic theming in widget (Android 12+)
- [ ] Create compact widget variant (small: art + play/pause only)
- [ ] Create wide widget variant (full controls + progress bar)
- [ ] Handle "not playing" state in widget (show last played or app icon)
- [ ] Add widget preview images for the widget picker
- [ ] Test widget on various grid sizes (2x1, 3x2, 4x2)
- [ ] Handle widget tap → open app at NowPlaying screen

> **Cross-platform**: Not applicable to web. iOS can add Lock Screen / Live Activity widget.

### 1.3 Android Auto — Complete Browse Tree
- [ ] Add "Albums" root node to MediaLibraryService browse tree
- [ ] Populate Albums node with `getAlbumList2(type=alphabeticalByName)` paginated
- [ ] Add "Radio" root node to browse tree
- [ ] Populate Radio node with `getInternetRadioStations`
- [ ] Handle radio station playback from Auto (resolve PLS/M3U, start stream)
- [ ] Add album art to all browse tree items (currently may be missing on some nodes)
- [ ] Test voice search: "Play [artist name]" → search3 → play top result
- [ ] Test voice search: "Play [album name]" → search3 → play album
- [ ] Handle large libraries gracefully (pagination in browse tree, don't load 1000+ albums at once)

> **Cross-platform**: iOS has CarPlay support already. Web not applicable.

---

## Phase 2: Audio Excellence (Crossfade + AutoEQ + ReplayGain + Normalization)

Audiophile features that differentiate from casual players.

### 2.1 True Dual-Player Crossfade
- [ ] Create second ExoPlayer instance in PlaybackService
- [ ] Implement `DualPlayerManager` that coordinates two players
- [ ] Pre-load next track on secondary player when primary track reaches `(duration - crossfadeDuration)`
- [ ] Implement simultaneous playback: primary volume ramps down while secondary ramps up
- [ ] Use sine/cosine crossfade curve (equal power) instead of linear for smoother transitions
- [ ] Handle edge cases: skip during crossfade, seek during crossfade, queue change during crossfade
- [ ] Handle repeat-one mode (disable crossfade, use gapless)
- [ ] Swap player roles after crossfade completes (secondary becomes primary)
- [ ] Ensure only one player holds audio focus at a time
- [ ] Release secondary player resources when crossfade is disabled
- [ ] Add crossfade curve selection in Settings (linear, equal power, S-curve)
- [ ] Add "crossfade only on shuffle" toggle (gapless for albums, crossfade for shuffle)

> **Cross-platform**: Web can implement via dual Web Audio API sources. iOS via dual AVPlayer instances.

### 2.2 AutoEQ Import
- [ ] Create AutoEQ CSV parser (frequency, gain columns)
- [ ] Create APO config file parser (`Filter: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.41`)
- [ ] Map parsed frequency/gain/Q points to BiquadCoefficients parametric filters
- [ ] Interpolate/resample parsed bands to fit 10-band PEQ (or extend to variable band count)
- [ ] Add "Import EQ Profile" button in EQ settings screen
- [ ] Implement file picker for `.csv` and `.txt` AutoEQ/APO files
- [ ] Bundle 20 popular headphone corrections from AutoEQ project (with attribution)
- [ ] Add headphone model search/selector UI (searchable list of bundled profiles)
- [ ] Preview EQ curve visualization before applying
- [ ] Save imported profiles as named presets alongside built-in presets
- [ ] Add "Export EQ Profile" option (share as APO-format text)

> **Cross-platform**: Web can implement via Web Audio API BiquadFilterNodes. iOS via AVAudioUnitEQ.

### 2.3 Album-Level ReplayGain
- [ ] Parse `replayGain.albumGain` and `replayGain.albumPeak` from Song model (already modeled)
- [ ] Add ReplayGain mode toggle in Settings: Off / Track / Album / Auto
- [ ] "Auto" mode: use album gain when playing an album in order, track gain when shuffled
- [ ] Detect "playing album in order" by checking if queue matches an album's track list sequentially
- [ ] Apply album gain factor in the volume multiplication chain
- [ ] Respect peak values to prevent clipping (attenuate if gain + peak > 1.0)
- [ ] Add pre-amp adjustment slider (-6dB to +6dB) for overall ReplayGain offset
- [ ] Display current ReplayGain value on NowPlaying screen (subtle indicator)

> **Cross-platform**: Apply same logic in web (gain node) and iOS (AVAudioUnitEQ gain).

### 2.4 Audio Normalization Without ReplayGain
- [ ] Implement RMS level meter in the audio processing pipeline (after biquad, before output)
- [ ] Calculate running RMS over first 3 seconds of each track
- [ ] Compare to target loudness level (-18 LUFS reference)
- [ ] Apply gentle gain adjustment (max ±6dB, ramp over 500ms to avoid clicks)
- [ ] Only activate when ReplayGain tags are missing for the current track
- [ ] Add "Auto Normalize" toggle in Settings (default: on)
- [ ] Store calculated normalization values in local cache (Room DB) keyed by song ID
- [ ] Skip re-analysis on subsequent plays of the same track

> **Cross-platform**: Web via AnalyserNode + GainNode. iOS via AVAudioEngine tap + mixer gain.

### 2.5 EQ Per Output Device
- [ ] Detect current audio output device via `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`
- [ ] Identify device by type + name (e.g., "Bluetooth: AirPods Pro", "Speaker", "Wired Headphones")
- [ ] Create `EQProfile` Room entity: id, deviceName, deviceType, bands JSON, enabled
- [ ] Auto-switch EQ profile when audio output changes (register `AudioDeviceCallback`)
- [ ] Add "Manage Device Profiles" screen in Settings
- [ ] Show current output device name on EQ screen
- [ ] "Save to this device" button on EQ screen to snapshot current settings to device profile
- [ ] Handle unknown/new devices (prompt: "New audio device detected. Apply an EQ profile?")
- [ ] Fallback to default profile when no device-specific profile exists

> **Cross-platform**: Web via `navigator.mediaDevices.enumerateDevices()`. iOS via `AVAudioSession.currentRoute`.

---

## Phase 3: Smart Features (Stats + Smart Downloads + Queue Sync)

Features that make the app feel intelligent and connected.

### 3.1 Listening Stats — Hybrid Approach
Local detailed tracking + Navidrome API for cross-device basics.

#### Local Tracking
- [ ] Create `ListeningSession` Room entity: songId, artistName, albumName, genre, startedAt, durationMs, completed (bool), skipped (bool)
- [ ] Create `ListeningStatsDao` with aggregate queries (top artists, genres, hours by day/week/month)
- [ ] Record session start on play, update duration on pause/stop/track-change
- [ ] Mark `completed = true` if >80% of track played, `skipped = true` if <20%
- [ ] Calculate skip rate per artist/album (skips / total plays)

#### Stats Dashboard Screen
- [ ] "This Week" / "This Month" / "All Time" tab selector
- [ ] Top 10 Artists (with play counts and hours)
- [ ] Top 10 Albums (with play counts)
- [ ] Top 5 Genres (pie chart or bar)
- [ ] Total listening hours (with daily average)
- [ ] Listening activity heatmap (hour of day × day of week)
- [ ] "Most skipped" tracks list (tracks you always skip — maybe remove from playlists?)
- [ ] "Discovery rate" — percentage of unique tracks played vs library size
- [ ] Streak counter ("You've listened every day for 12 days")

#### Cross-Device Stats (via Navidrome)
- [ ] Query `getAlbumList2(type=frequent)` for most-played albums across all clients
- [ ] Query `getAlbumList2(type=recent)` for recently played across all clients
- [ ] Show "Played on other devices" indicator when local stats diverge from server play counts
- [ ] Use server play counts as baseline, overlay local session detail

> **Cross-platform**: Share the stats dashboard design. Web can query same Navidrome endpoints + track sessions via localStorage. iOS same Room-equivalent (SwiftData) + Subsonic API.

### 3.2 Smart Download Suggestions
- [ ] Analyze local `ListeningSession` data to identify top-played artists/albums on cellular/offline
- [ ] Identify starred songs not yet downloaded
- [ ] Identify frequently played songs not yet downloaded
- [ ] Create "Suggested Downloads" section on Downloads screen
- [ ] Show estimated storage per suggestion (file size from Subsonic song metadata)
- [ ] "Download all suggestions" one-tap button
- [ ] Weekly notification: "You played [artist] 15 times this week. Download their latest album?"
  - [ ] Add notification toggle in Settings (default: on)
- [ ] Auto-download starred songs option in Settings (default: off)
- [ ] Batch download support (download entire album/playlist, not just individual songs)
  - [ ] Add "Download Album" button on album detail screen
  - [ ] Add "Download Playlist" button on playlist detail screen
  - [ ] Show batch download progress (X/Y songs)
- [ ] Download priority queue (suggested downloads at lower priority than manual downloads)
- [ ] WorkManager integration for reliable background downloads with notification

> **Cross-platform**: Web can suggest but browser downloads are different. iOS same logic with URLSession background downloads.

### 3.3 Queue Sync Across Devices
- [ ] Implement `savePlayQueue` API call on track change / queue modification (debounced, max every 30s)
- [ ] Implement `getPlayQueue` API call on app launch
- [ ] Show prompt on launch if server queue differs from local: "Resume [song] from [device]?"
- [ ] Sync queue position (current track index + seek position in ms)
- [ ] Handle conflict: local queue has unsaved changes + server queue is different
- [ ] Add "Sync Queue" toggle in Settings (default: on)
- [ ] Add "Handoff" button in NowPlaying: explicitly push current queue to server for pickup on another device
- [ ] Display "Last played on [device]" in the resume prompt

> **Cross-platform**: Web and iOS implement the same `savePlayQueue`/`getPlayQueue` protocol. This is the Vibrdrome ecosystem killer feature.

---

## Phase 4: Immersive Mode (Haptics + Visualizer Cast + Branded Experience)

Premium experience features that no competitor offers.

### 4.1 Haptic Feedback on Beat
- [ ] Add `VIBRATE` permission to AndroidManifest
- [ ] Tap into existing visualizer's bass energy detection (`uBass` uniform value)
- [ ] Define beat threshold: trigger haptic when bass energy exceeds running average by 1.5x
- [ ] Use `VibrationEffect.createOneShot(duration=20ms, amplitude=80)` for subtle pulse
- [ ] Rate-limit haptic pulses (minimum 150ms between pulses to avoid buzzing)
- [ ] Scale haptic intensity with bass energy (louder bass = stronger pulse, amplitude 40–200)
- [ ] Add "Haptic Feedback" toggle in Settings (default: off)
- [ ] Add haptic intensity slider in Settings (subtle / medium / strong)
- [ ] Disable haptics when Bluetooth audio is active (latency mismatch makes it feel wrong)
- [ ] Disable haptics when casting (not applicable)
- [ ] Test on various devices (haptic motor quality varies wildly)

> **Cross-platform**: iOS via Core Haptics (CHHapticEngine). Web via Vibration API (limited).

### 4.2 Immersive Mode Toggle
- [ ] Create "Immersive Mode" master toggle in Settings
- [ ] When enabled, auto-activates: visualizer, haptics (if enabled), crossfade
- [ ] When disabled, returns each feature to its individual setting
- [ ] Add quick-toggle for Immersive Mode on NowPlaying screen (single icon)
- [ ] Animate transition into Immersive Mode (screen dims, visualizer fades in)
- [ ] In Immersive Mode, hide non-essential UI (only show album art, track info, basic controls over visualizer)

> **Cross-platform**: Web can do fullscreen visualizer + crossfade. iOS same concept.

### 4.3 Visualizer on Chromecast / TV Output
- [ ] Investigate Cast Custom Receiver for rendering visualizer on TV
- [ ] Alternative: render GLSL visualizer locally, encode as low-res video stream, cast via Cast SDK
- [ ] Simpler alternative: cast audio only, show "Now Playing" card with album art + track info on TV (standard Cast behavior)
- [ ] If full visualizer cast is too complex, implement the simple "Now Playing card" first
- [ ] Add option: "Show visualizer on Cast device" (experimental) vs "Album art only"

> **Cross-platform**: Web can fullscreen visualizer on a connected display. iOS AirPlay screen mirroring.

---

## Phase 5: Platform Polish (Offline + Scrobble + Bookmarks)

Close the remaining gaps and polish rough edges.

### 5.1 Complete Offline Support
- [ ] Wire `OfflineActionQueue` into scrobble code path (enqueue on network failure instead of swallowing error)
- [ ] Add visual indicator for queued offline actions ("3 actions pending sync")
- [ ] Process offline action queue on network restore (register `ConnectivityManager.NetworkCallback`)
- [ ] Add "Offline Mode" toggle: when enabled, only show downloaded content in library
- [ ] Filter library screens (artists, albums, songs) to downloaded content in offline mode
- [ ] Auto-detect offline state and suggest switching to offline mode
- [ ] Show download status icon on songs/albums throughout the app (downloaded ✓, downloading ↓, not downloaded)
- [ ] Download notification with progress bar (WorkManager foreground service)
- [ ] Resume interrupted downloads on network restore

> **Cross-platform**: Web via Service Worker cache. iOS via URLSession background transfer.

### 5.2 Scrobble Hardening
- [ ] Enqueue failed scrobbles to `OfflineActionQueue` instead of swallowing errors
- [ ] Add scrobble toggle in Settings (default: on)
- [ ] Show "Scrobbled" indicator briefly on NowPlaying after successful scrobble
- [ ] Track scrobble history locally (last 50 scrobbles with timestamps) for debugging
- [ ] Handle duplicate scrobble prevention (don't re-scrobble same track if seek back and replay)

> **Cross-platform**: Same logic across all platforms.

### 5.3 Bookmark / Resume UI
- [ ] Create `BookmarksScreen` showing all server bookmarks via `getBookmarks` API
- [ ] Display: track title, artist, position timestamp, created date
- [ ] Tap bookmark → resume playback from saved position
- [ ] Add "Bookmark this position" action in NowPlaying context menu
- [ ] Delete bookmark support
- [ ] Auto-create bookmark when pausing tracks longer than 10 minutes (podcast/long mix detection)
- [ ] Show bookmark indicator on tracks that have saved positions

> **Cross-platform**: Same API, same UI concept.

---

## Phase 6: Delight Features (Context-Aware Transitions + Adaptive Bitrate + Collaboration)

The "wow, this app is smart" features.

### 6.1 Context-Aware Transitions (Gapless + Crossfade Intelligence)
- [ ] Parse release type from Navidrome tags (album, compilation, live, etc.)
- [ ] Auto-enable gapless (disable crossfade) for live albums and albums played in order
- [ ] Auto-enable crossfade for shuffled playback and playlists mixing multiple artists
- [ ] Detect genre/energy shifts in shuffled queue (compare adjacent tracks' genres)
- [ ] Apply longer crossfade on large genre shifts (e.g., jazz → electronic = 8s crossfade)
- [ ] Apply shorter crossfade on same-artist/same-album transitions (3s)
- [ ] Add "Smart Transitions" toggle in Settings (default: on) — falls back to manual crossfade/gapless setting when off
- [ ] Log transition decisions for debugging ("Gapless: live album detected")

> **Cross-platform**: Same logic, different audio engines.

### 6.2 Adaptive Bitrate by Network Quality
- [ ] Monitor HTTP download throughput during streaming (bytes received / time)
- [ ] Calculate rolling average throughput over last 10 seconds
- [ ] Define quality tiers: Original (>2Mbps), 320k (>500kbps), 192k (>300kbps), 128k (>150kbps), 96k (fallback)
- [ ] On buffer underrun or throughput drop, downgrade quality on next track
- [ ] On sustained good throughput (30s), upgrade quality on next track
- [ ] Never downgrade mid-track (causes audible artifacts) — only apply on track boundary
- [ ] Show current streaming quality indicator on NowPlaying (subtle: "320k" or "Original")
- [ ] Add "Adaptive Quality" toggle in Settings (default: on for cellular, off for WiFi)
- [ ] Respect user's max quality setting as ceiling

> **Cross-platform**: Web via `navigator.connection` API. iOS via `NWPathMonitor`.

### 6.3 Playlist Collaboration
- [ ] Detect Navidrome multi-user setup (multiple users exist on server)
- [ ] Add "Share Playlist" action on playlist detail screen
- [ ] Use Navidrome's playlist `public` flag to make playlists visible to other users
- [ ] Show shared playlists from other users in a "Shared with me" section
- [ ] Display playlist owner name
- [ ] "Currently Listening" indicator: query other users' now-playing via Subsonic `getNowPlaying` API
- [ ] Show "Friends Activity" section on library home (optional, toggle in Settings)

> **Cross-platform**: Same API across all platforms. Web is the natural place for collaborative playlist editing.

### 6.4 Predictive Pre-buffering
- [ ] When current track reaches 80% played and next track is known (no shuffle randomness pending)
- [ ] Start background HTTP request for first 15 seconds of next track's stream
- [ ] Store pre-buffered data in memory cache (not disk — ephemeral)
- [ ] When ExoPlayer transitions to next track, serve from pre-buffer before hitting network
- [ ] Handle queue changes during pre-buffer (cancel and re-buffer new next track)
- [ ] Disable pre-buffering on metered connections when data saver is active
- [ ] This is invisible to the user — no UI, no settings, just works

> **Cross-platform**: Web via fetch() + AudioBuffer. iOS via AVQueuePlayer (does this natively).

---

## Cross-Platform Parity Report

Features that should be tracked for implementation across all Vibrdrome clients.

| Feature | Android | Web | iOS/macOS |
|---|---|---|---|
| Chromecast | Phase 1 | Cast SDK for Chrome | Google Cast iOS SDK |
| Widgets | Phase 1 (Glance) | N/A | Lock Screen / Live Activity |
| Android Auto / CarPlay | Phase 1 (complete) | N/A | Already implemented |
| True crossfade | Phase 2 | Dual Web Audio sources | Dual AVPlayer |
| AutoEQ import | Phase 2 | Web Audio BiquadFilter | AVAudioUnitEQ |
| Album ReplayGain | Phase 2 | GainNode | AVAudioUnitEQ gain |
| Audio normalization | Phase 2 | AnalyserNode + GainNode | AVAudioEngine tap |
| EQ per device | Phase 2 | MediaDevices API | AVAudioSession route |
| Listening stats | Phase 3 | localStorage + API | SwiftData + API |
| Smart downloads | Phase 3 | Service Worker cache | URLSession background |
| Queue sync | Phase 3 | savePlayQueue/getPlayQueue | savePlayQueue/getPlayQueue |
| Haptic feedback | Phase 4 | Vibration API (limited) | Core Haptics |
| Immersive mode | Phase 4 | Fullscreen visualizer | Same concept |
| Visualizer cast | Phase 4 | External display | AirPlay mirroring |
| Offline hardening | Phase 5 | Service Worker | URLSession |
| Bookmarks UI | Phase 5 | Same API | Same API |
| Smart transitions | Phase 6 | Same logic | Same logic |
| Adaptive bitrate | Phase 6 | navigator.connection | NWPathMonitor |
| Playlist collab | Phase 6 | Same API (natural fit) | Same API |
| Predictive pre-buffer | Phase 6 | fetch() + AudioBuffer | AVQueuePlayer native |

---

## Task Count Summary

| Phase | Tasks | Focus |
|---|---|---|
| Phase 1 | 40 | Table Stakes |
| Phase 2 | 47 | Audio Excellence |
| Phase 3 | 42 | Smart Features |
| Phase 4 | 16 | Immersive Mode |
| Phase 5 | 21 | Platform Polish |
| Phase 6 | 24 | Delight Features |
| **Total** | **190** | |

---

## Decisions Log

- **No Plex/Jellyfin/Emby** — stay Navidrome/Subsonic focused (revisit later)
- **No Wear OS** — low priority, small audience
- **No Android TV** — low priority (revisit after Chromecast proves demand)
- **No local file playback** — users have Navidrome server + offline downloads covers the use case
- **Listening stats** — hybrid approach (local detail + Navidrome API for cross-device)
- **Haptics** — settings toggle, default off, intensity slider, auto-disable on Bluetooth
- **Crossfade** — upgrade to true dual-player; keep single-player as fallback option
- **AutoEQ** — bundle popular profiles + allow import
- **Queue sync** — key ecosystem differentiator across Android/Web/iOS

---

## Polish Backlog (Deferred from Phases 1-2)

Items to revisit after Phase 6 or during a dedicated polish sprint.

- [ ] **Bundle 20 AutoEQ headphone profiles** — Requires downloading from AutoEQ project, converting to APO format, adding as assets with attribution/licensing
- [ ] **Headphone model search UI** — Searchable list of bundled AutoEQ profiles by headphone model name. Depends on bundled profiles above.
- [ ] **EQ curve visualization preview** — Visual graph of the current EQ curve (frequency vs gain) on the EQ screen. Pure UI task.
- [ ] **Manage Device Profiles screen** — Settings screen listing saved per-device EQ profiles with edit/delete. Currently profiles are saved but not browsable.
- [ ] **New device prompt dialog** — When a new audio output device is detected (e.g., new Bluetooth headphones), prompt: "New device detected. Apply an EQ profile?"
- [ ] **Widget deep link to NowPlaying** — Tapping the widget currently opens the app home. Should deep link directly to NowPlaying screen. Requires Compose Navigation deep link wiring.

---

*Last updated: 2026-04-04*
*Generated for Vibrdrome Android by Claude Code*

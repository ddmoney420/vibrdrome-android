# Vibrdrome Android — Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        VIBRDROME ANDROID                            │
│                     Navidrome/Subsonic Client                        │
└─────────────────────────────────────────────────────────────────────┘

╔═══════════════════════════════════════════════════════════════════════╗
║                           UI LAYER (Compose)                         ║
║                                                                      ║
║  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ ║
║  │ Library  │ │ Artists  │ │ Albums   │ │ Playlists│ │  Search   │ ║
║  │  Screen  │ │  Screen  │ │  Detail  │ │  Screen  │ │  Screen   │ ║
║  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬─────┘ ║
║       │            │            │            │              │        ║
║  ┌────┴────────────┴────────────┴────────────┴──────────────┴────┐  ║
║  │              14 Quick Access Pills (2-column grid)             │  ║
║  │  Genres · Radio · Artists · Favorites · Albums · Folders ·     │  ║
║  │  Songs · Downloads · Playlists · Recently Added ·              │  ║
║  │  Generations · Recently Played · Random Mix · Random Album     │  ║
║  └───────────────────────────────────────────────────────────────┘  ║
║                                                                      ║
║  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ ║
║  │  Now     │ │  Queue   │ │   EQ     │ │  Lyrics  │ │Visualizer │ ║
║  │ Playing  │ │  Screen  │ │  Screen  │ │  Screen  │ │  Screen   │ ║
║  └────┬─────┘ └──────────┘ └──────────┘ └──────────┘ └─────┬─────┘ ║
║       │                                                      │       ║
║  ┌────┴────┐                                          ┌──────┴─────┐║
║  │  Mini   │                                          │ Vibrdrome  │║
║  │ Player  │                                          │ (GLSL) or  │║
║  │(bottom) │                                          │ Milkdrop   │║
║  └─────────┘                                          │ (projectM) │║
║                                                       └────────────┘║
║  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐               ║
║  │ Settings │ │  Server  │ │  Radio   │ │  Folder  │               ║
║  │  Screen  │ │ Manager  │ │  Screen  │ │ Browser  │               ║
║  └──────────┘ └──────────┘ └──────────┘ └──────────┘               ║
╚═══════════════════════════════════════════════════════════════════════╝
         │                    │                     │
         │  Navigation        │  State              │  Actions
         ▼                    ▼                     ▼
╔═══════════════════════════════════════════════════════════════════════╗
║                      STATE & PLAYBACK LAYER                          ║
║                                                                      ║
║  ┌─────────────────────────────────────────────────────────────────┐ ║
║  │                     PlaybackManager                             │ ║
║  │                                                                 │ ║
║  │  Queue ◄──► ExoPlayer ◄──► MediaSession ◄──► Lock Screen       │ ║
║  │    │            │              │                Notification     │ ║
║  │    │            │              │                Android Auto     │ ║
║  │    │            ▼              │                                 │ ║
║  │    │     ┌──────────────┐     │    ┌─────────────────────────┐ │ ║
║  │    │     │ BiquadAudio  │     │    │   Volume Factor System  │ │ ║
║  │    │     │  Processor   │     │    │                         │ ║
║  │    │     │  (10-band    │     │    │  ReplayGain × SleepFade │ │ ║
║  │    │     │   EQ DSP)    │     │    │  × CrossfadeFactor      │ │ ║
║  │    │     └──────────────┘     │    └─────────────────────────┘ │ ║
║  │    │                          │                                 │ ║
║  │    ▼                          ▼                                 │ ║
║  │  StateFlows:                MediaLibraryService                 │ ║
║  │  · currentSong              (Android Auto browse tree)          │ ║
║  │  · isPlaying                 Artists → Albums → Songs           │ ║
║  │  · positionMs               Playlists → Songs                   │ ║
║  │  · queue                    Recently Added                      │ ║
║  │  · repeatMode                                                   │ ║
║  │  · shuffleEnabled                                               │ ║
║  │  · playbackSpeed                                                │ ║
║  └─────────────────────────────────────────────────────────────────┘ ║
║                                                                      ║
║  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   ║
║  │  SleepTimer  │  │  EQEngine    │  │     RadioManager         │   ║
║  │              │  │              │  │                          │   ║
║  │ Countdown +  │  │ 10 presets + │  │ Artist radio: similar +  │   ║
║  │ volume fade  │  │ custom bands │  │ top songs, deduplicate   │   ║
║  │ last 10 sec  │  │ persisted    │  │ Song radio: similar      │   ║
║  └──────────────┘  └──────────────┘  └──────────────────────────┘   ║
╚═══════════════════════════════════════════════════════════════════════╝
         │                    │                     │
         ▼                    ▼                     ▼
╔═══════════════════════════════════════════════════════════════════════╗
║                        NETWORK LAYER                                 ║
║                                                                      ║
║  ┌─────────────────────────────────────────────────────────────────┐ ║
║  │                     SubsonicClient                              │ ║
║  │                                                                 │ ║
║  │  Ktor HttpClient ──► 30+ Subsonic API endpoints                 │ ║
║  │                                                                 │ ║
║  │  Auth: password (hex-encoded) with token fallback               │ ║
║  │  Retry: 3 attempts with exponential backoff                     │ ║
║  │  Cache: file-based JSON cache with TTL                          │ ║
║  │                                                                 │ ║
║  │  Endpoints:                                                     │ ║
║  │  · getArtists, getAlbumList2, search3, getStarred2              │ ║
║  │  · stream, getCoverArt, download                                │ ║
║  │  · getPlaylists, createPlaylist, getLyricsBySongId              │ ║
║  │  · star/unstar, scrobble, getRandomSongs, getSimilarSongs2      │ ║
║  │  · getGenres, getMusicDirectory, getInternetRadioStations       │ ║
║  └─────────────────────────────────────────────────────────────────┘ ║
║                                                                      ║
║  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   ║
║  │ResponseCache │  │DownloadMgr   │  │   CacheManager           │   ║
║  │              │  │              │  │                          │   ║
║  │ SHA-256 key  │  │ Ktor stream  │  │ LRU eviction by last    │   ║
║  │ TTL-based    │  │ to local     │  │ access date             │   ║
║  │ file cache   │  │ file storage │  │ Configurable size limit │   ║
║  └──────────────┘  └──────────────┘  └──────────────────────────┘   ║
╚═══════════════════════════════════════════════════════════════════════╝
         │                    │                     │
         ▼                    ▼                     ▼
╔═══════════════════════════════════════════════════════════════════════╗
║                      PERSISTENCE LAYER                               ║
║                                                                      ║
║  ┌───────────────────────┐  ┌─────────────────────────────────────┐ ║
║  │    Room Database      │  │      SharedPreferences              │ ║
║  │                       │  │                                     │ ║
║  │  · SavedPlaybackState │  │  · Server credentials (encrypted)   │ ║
║  │    (queue as JSON)    │  │  · Theme mode (system/dark/light)   │ ║
║  │  · DownloadedSong     │  │  · EQ enabled + preset              │ ║
║  │    (local file refs)  │  │  · Playback speed                   │ ║
║  │  · PendingAction      │  │  · Crossfade settings               │ ║
║  │    (offline retry)    │  │  · Multi-server configs             │ ║
║  └───────────────────────┘  └─────────────────────────────────────┘ ║
╚═══════════════════════════════════════════════════════════════════════╝
         │
         ▼
╔═══════════════════════════════════════════════════════════════════════╗
║                       NATIVE LAYER (C++/JNI)                         ║
║                                                                      ║
║  ┌─────────────────────────────────────────────────────────────────┐ ║
║  │                  libprojectM-4.so (LGPL-2.1)                    │ ║
║  │                                                                 │ ║
║  │  Milkdrop-compatible visualization engine                       │ ║
║  │  · EEL2 expression evaluator for preset scripts                 │ ║
║  │  · Warp mesh with per-frame feedback (the "trippy" effect)      │ ║
║  │  · OpenGL ES 2.0 rendering                                     │ ║
║  │  · 50 bundled Cream of the Crop presets                         │ ║
║  │                                                                 │ ║
║  │  projectm_jni.cpp ◄──► ProjectMBridge.kt ◄──► VisualizerScreen │ ║
║  │       (C JNI)              (Kotlin)              (Compose)      │ ║
║  └─────────────────────────────────────────────────────────────────┘ ║
╚═══════════════════════════════════════════════════════════════════════╝

╔═══════════════════════════════════════════════════════════════════════╗
║                     DEPENDENCY INJECTION (Koin)                      ║
║                                                                      ║
║  AppState ─── SubsonicClient ─── ResponseCache                       ║
║  PlaybackManager ─── SleepTimer ─── EQEngine ─── EQCoefficientsStore ║
║  DownloadManager ─── CacheManager ─── RadioManager                   ║
║  OfflineActionQueue                                                  ║
║                                                                      ║
║  All singletons, injected via Koin module                            ║
╚═══════════════════════════════════════════════════════════════════════╝


## Data Flow: User taps Play on an album

1. AlbumDetailScreen calls playbackManager.play(songs)
2. PlaybackManager checks DownloadDao for local files
3. Builds MediaItems with stream URLs (or local file URIs)
4. Sets items on ExoPlayer, calls prepare() + play()
5. ExoPlayer streams audio from Navidrome server
6. Audio passes through BiquadAudioProcessor (if EQ enabled)
7. Volume adjusted: ReplayGain × SleepFade × Crossfade
8. MediaSession updates lock screen + notification
9. StateFlows update → MiniPlayer and NowPlayingScreen recompose
10. Scrobble fires after 30s or 50% of track
11. Queue state saved to Room after 1s debounce


## Data Flow: Visualizer

1. User taps waves icon on NowPlayingScreen
2. VisualizerScreen creates Android Visualizer (audio capture)
3. Waveform data captured at max rate from ExoPlayer session

   Vibrdrome mode:
   4a. Waveform → energy/bass/mid/treble extraction
   5a. GLSL uniforms updated → fragment shader renders on GPU

   Milkdrop mode:
   4b. Waveform PCM → JNI → projectm_pcm_add_uint8()
   5b. projectm_opengl_render_frame() → Milkdrop preset renders

6. GLSurfaceView displays at 60fps
7. Swipe to cycle presets, double-tap for random
```

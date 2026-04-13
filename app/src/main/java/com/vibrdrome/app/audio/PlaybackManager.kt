package com.vibrdrome.app.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.vibrdrome.app.cast.CastManager
import com.vibrdrome.app.network.Song
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.persistence.DownloadDao
import com.vibrdrome.app.persistence.OfflineActionQueue
import com.vibrdrome.app.persistence.PlaybackStateDao
import com.vibrdrome.app.persistence.SavedPlaybackState
import com.vibrdrome.app.ui.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * Minimal reference to a [Song] for queue persistence.
 * Stores only the fields needed to rebuild MediaItems and display metadata,
 * reducing per-song storage from ~200 bytes to ~80 bytes.
 */
@Serializable
private data class QueueSongRef(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        coverArt = coverArt,
        duration = duration,
        track = track,
    )

    companion object {
        fun from(song: Song) = QueueSongRef(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumId = song.albumId,
            coverArt = song.coverArt,
            duration = song.duration,
            track = song.track,
        )
    }
}

/**
 * ReplayGain application mode.
 */
data class ServerQueueInfo(
    val songs: List<Song>,
    val currentId: String?,
    val positionMs: Long,
    val changedBy: String?,
)

enum class ReplayGainMode {
    /** ReplayGain disabled. */
    OFF,
    /** Always use track gain. */
    TRACK,
    /** Always use album gain. */
    ALBUM,
    /** Auto: album gain when playing an album in order, track gain when shuffled. */
    AUTO,
}

class PlaybackManager(
    context: Context,
    private val appState: AppState,
    private val playbackStateDao: PlaybackStateDao,
    private val downloadDao: DownloadDao,
    val sleepTimer: SleepTimer,
    val eqCoefficientsStore: EQCoefficientsStore,
    val castManager: CastManager,
    private val listeningTracker: ListeningTracker,
    private val offlineActionQueue: OfflineActionQueue,
    val smartTransitions: SmartTransitions,
    val adaptiveBitrate: AdaptiveBitrate,
    private val preBufferManager: PreBufferManager,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = appContext.getSharedPreferences("playback_prefs", android.content.Context.MODE_PRIVATE)

    @OptIn(UnstableApi::class)
    val biquadProcessor = BiquadAudioProcessor(eqCoefficientsStore)

    @OptIn(UnstableApi::class)
    val localPlayer: ExoPlayer = createPlayerWithEQ()

    /**
     * The currently active player — either the local ExoPlayer or the CastPlayer.
     * All playback operations go through this property.
     */
    var player: Player = localPlayer
        private set

    /** True when playback is routed to a Cast device. */
    val isCasting: StateFlow<Boolean> = castManager.isCasting
    val castDeviceName: StateFlow<String?> = castManager.castDeviceName

    /** Callback for PlaybackService to swap the MediaSession player. */
    var onPlayerSwapped: ((Player) -> Unit)? = null

    /** Called on track transition — used by HapticEngine to reset beat detection. */
    var onTrackChanged: (() -> Unit)? = null

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentCoverArtUrl = MutableStateFlow<String?>(null)
    val currentCoverArtUrl: StateFlow<String?> = _currentCoverArtUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(prefs.getFloat("playback_speed", 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private var crossfadeDurationMs = prefs.getLong("crossfade_duration", 5000L)

    // True dual-player crossfade engine
    val crossfadeEngine = CrossfadeEngine(appContext, eqCoefficientsStore)

    private val _crossfadeCurve = MutableStateFlow(
        CrossfadeCurve.entries.getOrElse(prefs.getInt("crossfade_curve", 1)) { CrossfadeCurve.EQUAL_POWER }
    )
    val crossfadeCurve: StateFlow<CrossfadeCurve> = _crossfadeCurve.asStateFlow()

    /** When true, crossfade only applies during shuffle; albums use gapless. */
    private val _crossfadeOnlyOnShuffle = MutableStateFlow(prefs.getBoolean("crossfade_only_shuffle", false))
    val crossfadeOnlyOnShuffle: StateFlow<Boolean> = _crossfadeOnlyOnShuffle.asStateFlow()

    // Audio normalizer for tracks without ReplayGain
    val audioNormalizer = AudioNormalizer(appContext)

    // Volume factors
    private var replayGainFactor = 1.0f
    private var normalizationFactor = 1.0f
    private var sleepFadeFactor = 1.0f

    private val _autoNormalizeEnabled = MutableStateFlow(prefs.getBoolean("auto_normalize", true))
    val autoNormalizeEnabled: StateFlow<Boolean> = _autoNormalizeEnabled.asStateFlow()

    // Auto-continue queue with similar songs
    private val _autoContinueEnabled = MutableStateFlow(prefs.getBoolean("auto_continue", false))
    val autoContinueEnabled: StateFlow<Boolean> = _autoContinueEnabled.asStateFlow()

    fun setAutoContinueEnabled(enabled: Boolean) {
        _autoContinueEnabled.value = enabled
        prefs.edit().putBoolean("auto_continue", enabled).apply()
    }

    // Queue sync
    private val _queueSyncEnabled = MutableStateFlow(prefs.getBoolean("queue_sync_enabled", true))
    val queueSyncEnabled: StateFlow<Boolean> = _queueSyncEnabled.asStateFlow()
    private var lastQueueSyncTime = 0L
    private var queueSyncJob: Job? = null

    // ReplayGain settings
    private val _replayGainMode = MutableStateFlow(
        ReplayGainMode.entries.getOrElse(prefs.getInt("replay_gain_mode", 3)) { ReplayGainMode.AUTO }
    )
    val replayGainMode: StateFlow<ReplayGainMode> = _replayGainMode.asStateFlow()

    private val _replayGainPreamp = MutableStateFlow(prefs.getFloat("replay_gain_preamp", 0f))
    val replayGainPreamp: StateFlow<Float> = _replayGainPreamp.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null
    private var saveJob: Job? = null
    private var serviceStarted = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                startPositionTracking()
                listeningTracker.onResumed()
            } else {
                stopPositionTracking()
                listeningTracker.onPaused()
            }
            scheduleSave()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrentSong()
            updateReplayGain()
            hasScrobbled = false
            scheduleSave()
            // Track the new song for listening stats
            _currentSong.value?.let { listeningTracker.onTrackStarted(it) }
            onTrackChanged?.invoke()

            // Auto-continue: fetch similar songs when nearing end of queue
            if (_autoContinueEnabled.value) {
                val currentIdx = player.currentMediaItemIndex
                val queueSize = _queue.value.size
                if (currentIdx >= queueSize - 2 && queueSize > 0) {
                    val songId = _currentSong.value?.id ?: return
                    val existingIds = _queue.value.map { it.id }.toSet()
                    scope.launch {
                        try {
                            val similar = appState.subsonicClient.getSimilarSongs(songId, count = 15)
                            val newSongs = similar.filter { it.id !in existingIds }
                            newSongs.forEach { addToQueue(it) }
                        } catch (_: Throwable) { }
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                _durationMs.value = player.duration.coerceAtLeast(0)
            }
        }

        override fun onRepeatModeChanged(mode: Int) {
            _repeatMode.value = mode
            scheduleSave()
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffleEnabled.value = enabled
            scheduleSave()
        }

        override fun onPlayerError(error: PlaybackException) {
            appState.showError("Playback error — retrying...")
            scope.launch {
                delay(2000)
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    init {
        localPlayer.addListener(playerListener)

        // Sleep timer volume integration
        scope.launch {
            sleepTimer.fadeFactor.collect { factor ->
                sleepFadeFactor = factor
                applyVolume()
                if (factor == 0f) {
                    player.pause()
                }
            }
        }

        // Restore saved playback speed
        val savedSpeed = _playbackSpeed.value
        if (savedSpeed != 1.0f) {
            localPlayer.playbackParameters = PlaybackParameters(savedSpeed)
        }

        // Sync engine states from prefs
        crossfadeEngine.enabled = _crossfadeEnabled.value
        crossfadeEngine.curve = _crossfadeCurve.value
        audioNormalizer.enabled = _autoNormalizeEnabled.value

        // Cast session lifecycle
        castManager.setSessionCallbacks(
            onStarted = { switchToCastPlayer() },
            onEnded = { switchToLocalPlayer() },
            onError = { msg -> appState.showError(msg) },
        )

        scope.launch { restoreQueue() }
    }

    // MARK: - Cast Player Swapping

    private fun switchToCastPlayer() {
        val castPlayer = castManager.getPlayer() ?: return
        val wasPlaying = player.isPlaying
        val currentQueue = _queue.value
        val currentIndex = player.currentMediaItemIndex
        val currentPosition = player.currentPosition

        // Pause local player
        localPlayer.pause()

        // Attach listener to cast player
        castPlayer.addListener(playerListener)

        // Load queue into cast player using HTTP stream URLs (not local files)
        if (currentQueue.isNotEmpty()) {
            val client = appState.subsonicClient
            val castItems = currentQueue.map { it.toHttpMediaItem(client, forCast = true) }
            castPlayer.setMediaItems(castItems, currentIndex, currentPosition)
            castPlayer.prepare()
            if (wasPlaying) castPlayer.play()
        }

        // Remove listener from local player
        localPlayer.removeListener(playerListener)

        player = castPlayer
        onPlayerSwapped?.invoke(castPlayer)
    }

    private fun switchToLocalPlayer() {
        val castPlayer = castManager.getPlayer()
        val wasPlaying = player.isPlaying
        val currentIndex = player.currentMediaItemIndex
        val currentPosition = player.currentPosition

        // Remove listener from cast player
        castPlayer?.removeListener(playerListener)

        // Restore queue to local player
        val currentQueue = _queue.value
        if (currentQueue.isNotEmpty()) {
            val client = appState.subsonicClient
            scope.launch {
                val localItems = currentQueue.map { it.toMediaItemResolved(client) }
                localPlayer.setMediaItems(localItems, currentIndex, currentPosition)
                localPlayer.prepare()
                if (wasPlaying) localPlayer.play()
            }
        }

        // Re-attach listener to local player
        localPlayer.addListener(playerListener)

        player = localPlayer
        onPlayerSwapped?.invoke(localPlayer)
        updateReplayGain()
    }

    /**
     * Build a MediaItem that always uses the HTTP stream URL (never local file).
     * Required for Cast since the Cast device can't access local files.
     */
    fun Song.toHttpMediaItem(client: SubsonicClient, forCast: Boolean = false): MediaItem {
        val artUri = coverArt?.let { Uri.parse(client.coverArtURL(it, size = 480)) }
        val quality = appState.getEffectiveStreamQuality()
        // Cap Cast streams at 320kbps — raw FLAC (up to 2800kbps) causes buffering on Chromecast
        val castBitRate = if (forCast) 320 else null
        val effectiveBitRate = castBitRate ?: (if (quality > 0) quality else null)
        val streamUri = client.streamURL(id, maxBitRate = effectiveBitRate)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    fun release() {
        positionJob?.cancel()
        saveJob?.cancel()
        listeningTracker.onStopped()
        scope.launch { saveQueueState() }
        crossfadeEngine.release()
        castManager.release()
        localPlayer.release()
    }

    // MARK: - Volume System

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        crossfadeEngine.enabled = enabled
        prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
        if (!enabled && player is ExoPlayer) {
            crossfadeEngine.cancelCrossfade(
                player as ExoPlayer,
                (replayGainFactor * sleepFadeFactor).coerceIn(0f, 1f),
            )
        }
    }

    fun setCrossfadeDuration(durationMs: Long) {
        crossfadeDurationMs = durationMs.coerceIn(1000, 12000)
        prefs.edit().putLong("crossfade_duration", crossfadeDurationMs).apply()
    }

    fun setCrossfadeCurve(curve: CrossfadeCurve) {
        _crossfadeCurve.value = curve
        crossfadeEngine.curve = curve
        prefs.edit().putInt("crossfade_curve", curve.ordinal).apply()
    }

    fun setCrossfadeOnlyOnShuffle(enabled: Boolean) {
        _crossfadeOnlyOnShuffle.value = enabled
        prefs.edit().putBoolean("crossfade_only_shuffle", enabled).apply()
    }

    private fun applyVolume() {
        // Volume factors only apply to local playback — CastPlayer volume is device-controlled
        if (!castManager.isCasting.value) {
            localPlayer.volume = (replayGainFactor * normalizationFactor * sleepFadeFactor).coerceIn(0f, 1f)
        }
    }

    private fun updateReplayGain() {
        val song = _currentSong.value
        replayGainFactor = computeReplayGainFactor(song)
        // Reset normalization for new track
        val hasRg = song?.replayGain?.trackGain != null || song?.replayGain?.albumGain != null
        audioNormalizer.onTrackChanged(song?.id ?: "", hasRg)
        normalizationFactor = audioNormalizer.gainFactor
        applyVolume()
    }

    private fun computeReplayGainFactor(song: Song?): Float {
        val rg = song?.replayGain ?: return 1.0f
        val mode = _replayGainMode.value
        if (mode == ReplayGainMode.OFF) return 1.0f

        val gain: Double? = when (mode) {
            ReplayGainMode.TRACK -> rg.trackGain
            ReplayGainMode.ALBUM -> rg.albumGain ?: rg.trackGain
            ReplayGainMode.AUTO -> {
                // Use album gain when playing an album in order, track gain when shuffled
                if (_shuffleEnabled.value) {
                    rg.trackGain
                } else if (isPlayingAlbumInOrder()) {
                    rg.albumGain ?: rg.trackGain
                } else {
                    rg.trackGain
                }
            }
            ReplayGainMode.OFF -> null
        }

        if (gain == null) return 1.0f

        val preamp = _replayGainPreamp.value
        val totalGain = gain.toFloat() + preamp

        // Apply gain, respect peak to prevent clipping
        val factor = 10f.pow(totalGain / 20f)
        val peak = when (mode) {
            ReplayGainMode.ALBUM -> rg.albumPeak ?: rg.trackPeak
            ReplayGainMode.AUTO -> if (!_shuffleEnabled.value && isPlayingAlbumInOrder()) {
                rg.albumPeak ?: rg.trackPeak
            } else {
                rg.trackPeak
            }
            else -> rg.trackPeak
        }
        val maxFactor = if (peak != null && peak > 0) (1.0 / peak).toFloat() else 4f
        return factor.coerceIn(0.1f, maxFactor.coerceAtMost(4f))
    }

    /** Check if the current queue represents an album played in track order. */
    private fun isPlayingAlbumInOrder(): Boolean {
        val q = _queue.value
        if (q.size < 2) return false
        val albumId = q.firstOrNull()?.albumId ?: return false
        return q.all { it.albumId == albumId }
    }

    fun setReplayGainMode(mode: ReplayGainMode) {
        _replayGainMode.value = mode
        prefs.edit().putInt("replay_gain_mode", mode.ordinal).apply()
        updateReplayGain()
    }

    fun setAutoNormalize(enabled: Boolean) {
        _autoNormalizeEnabled.value = enabled
        audioNormalizer.enabled = enabled
        prefs.edit().putBoolean("auto_normalize", enabled).apply()
        if (!enabled) {
            normalizationFactor = 1.0f
            applyVolume()
        }
    }

    /**
     * Feed visualizer waveform data to the audio normalizer.
     * Call from VisualizerScreen's data capture callback.
     */
    fun feedNormalizerData(waveform: ByteArray) {
        if (_autoNormalizeEnabled.value && !castManager.isCasting.value) {
            val newFactor = audioNormalizer.feedWaveform(waveform)
            if (newFactor != normalizationFactor) {
                normalizationFactor = newFactor
                applyVolume()
            }
        }
    }

    // MARK: - Queue Sync

    fun setQueueSyncEnabled(enabled: Boolean) {
        _queueSyncEnabled.value = enabled
        prefs.edit().putBoolean("queue_sync_enabled", enabled).apply()
    }

    /**
     * Save current queue to the Navidrome server for cross-device pickup.
     * Debounced to max once every 30 seconds.
     */
    private fun scheduleQueueSync() {
        if (!_queueSyncEnabled.value) return
        val now = System.currentTimeMillis()
        if (now - lastQueueSyncTime < 30_000) return

        queueSyncJob?.cancel()
        queueSyncJob = scope.launch {
            delay(5000) // Debounce 5 seconds
            try {
                val songs = _queue.value
                if (songs.isEmpty()) return@launch
                val ids = songs.map { it.id }
                val currentId = _currentSong.value?.id
                val positionMs = player.currentPosition.toInt()
                appState.subsonicClient.savePlayQueue(ids, currentId, positionMs)
                lastQueueSyncTime = System.currentTimeMillis()
            } catch (_: Exception) {
                // Silently fail — queue sync is best-effort
            }
        }
    }

    /**
     * Check server for a play queue from another device.
     * Returns the server queue if it differs from local, null otherwise.
     */
    suspend fun checkServerQueue(): ServerQueueInfo? {
        if (!_queueSyncEnabled.value) return null
        return try {
            val pq = appState.subsonicClient.getPlayQueue() ?: return null
            val entries = pq.entry ?: return null
            if (entries.isEmpty()) return null

            // Check if the server queue is different from local
            val localIds = _queue.value.map { it.id }
            val serverIds = entries.map { it.id }
            if (localIds == serverIds && _currentSong.value?.id == pq.current) return null

            ServerQueueInfo(
                songs = entries,
                currentId = pq.current,
                positionMs = pq.position?.toLong() ?: 0,
                changedBy = pq.changedBy,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resume playback from a server queue (from another device).
     */
    fun resumeFromServerQueue(info: ServerQueueInfo) {
        val startIndex = info.currentId?.let { id ->
            info.songs.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        } ?: 0

        play(info.songs, startIndex)
        if (info.positionMs > 0) {
            seekTo(info.positionMs)
        }
    }

    fun setReplayGainPreamp(db: Float) {
        _replayGainPreamp.value = db.coerceIn(-6f, 6f)
        prefs.edit().putFloat("replay_gain_preamp", _replayGainPreamp.value).apply()
        updateReplayGain()
    }

    // MARK: - Playback

    fun play(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return

        // Route through jukebox if enabled
        val jukeboxManager = try {
            org.koin.java.KoinJavaComponent.getKoin().get<JukeboxManager>()
        } catch (_: Throwable) { null }
        if (jukeboxManager != null && jukeboxManager.enabled.value) {
            player.pause()
            jukeboxManager.playQueue(songs, startIndex)
            // Still update local queue state for UI
            _queue.value = songs
            _currentIndex.value = startIndex
            _currentSong.value = songs.getOrNull(startIndex)
            return
        }

        ensureServiceStarted()
        _queue.value = songs
        val client = appState.subsonicClient
        scope.launch {
            val mediaItems = songs.map { it.toMediaItemResolved(client) }
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
            updateCurrentSong()
            updateReplayGain()
            scheduleSave()
        }
    }

    fun playShuffle(songs: List<Song>) {
        play(songs.shuffled())
    }

    fun playNext(song: Song) {
        if (_queue.value.isEmpty()) {
            play(listOf(song))
            return
        }
        ensureServiceStarted()
        val client = appState.subsonicClient
        val insertIndex = player.currentMediaItemIndex + 1
        val mutableQueue = _queue.value.toMutableList()
        mutableQueue.add(insertIndex, song)
        _queue.value = mutableQueue
        player.addMediaItem(insertIndex, song.toMediaItem(client))
        scheduleSave()
    }

    fun addToQueue(song: Song) {
        if (_queue.value.isEmpty()) {
            play(listOf(song))
            return
        }
        // Route through jukebox if enabled
        val jukeboxManager = try {
            org.koin.java.KoinJavaComponent.getKoin().get<JukeboxManager>()
        } catch (_: Throwable) { null }
        if (jukeboxManager != null && jukeboxManager.enabled.value) {
            jukeboxManager.addToQueue(song)
            _queue.value = _queue.value + song
            return
        }
        ensureServiceStarted()
        val client = appState.subsonicClient
        _queue.value = _queue.value + song
        player.addMediaItem(song.toMediaItem(client))
        scheduleSave()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        cancelActiveCrossfade()
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() {
        cancelActiveCrossfade()
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
    }

    private var seekCooldownUntil = 0L

    fun seekTo(positionMs: Long) {
        cancelActiveCrossfade()
        player.seekTo(positionMs)
        _positionMs.value = positionMs
        // Prevent crossfade from triggering for 2 seconds after a seek
        seekCooldownUntil = System.currentTimeMillis() + 2000
    }

    private fun cancelActiveCrossfade() {
        if (crossfadeEngine.isCrossfading && player is ExoPlayer) {
            crossfadeEngine.cancelCrossfade(
                player as ExoPlayer,
                (replayGainFactor * sleepFadeFactor).coerceIn(0f, 1f),
            )
        }
        preBufferManager.cancel()
    }

    fun toggleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        prefs.edit().putFloat("playback_speed", speed).apply()
        // Playback speed only works on local player — CastPlayer doesn't support it
        if (!castManager.isCasting.value) {
            localPlayer.playbackParameters = PlaybackParameters(speed)
        }
    }

    // MARK: - Radio

    fun playRadioStream(name: String, streamUrl: String, coverArtId: String? = null) {
        ensureServiceStarted()
        // Resolve PLS/M3U playlists to direct stream URLs
        scope.launch {
            val resolvedUrl = resolveStreamUrl(streamUrl)
            playRadioStreamDirect(name, resolvedUrl, coverArtId)
        }
    }

    private fun playRadioStreamDirect(name: String, streamUrl: String, coverArtId: String? = null) {
        _queue.value = emptyList()
        // Create a dummy Song so MiniPlayer shows
        _currentSong.value = Song(
            id = "radio_${streamUrl.hashCode()}",
            title = name,
            artist = "Radio",
            coverArt = coverArtId,
        )
        _currentCoverArtUrl.value = coverArtId?.let { appState.subsonicClient.coverArtURL(it, size = 480) }
        _currentIndex.value = -1
        _durationMs.value = 0
        _positionMs.value = 0
        val artUri = coverArtId?.let { Uri.parse(appState.subsonicClient.coverArtURL(it, size = 480)) }
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist("Radio")
                    .setArtworkUri(artUri)
                    .setIsPlayable(true)
                    .build()
            )
            // Help ExoPlayer detect stream type for playlist URLs
            .setMimeType(guessRadioMimeType(streamUrl))
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    // MARK: - Scrobbling

    private var hasScrobbled = false
    private var lastScrobbledSongId: String? = null

    private val _scrobbleEnabled = MutableStateFlow(prefs.getBoolean("scrobble_enabled", true))
    val scrobbleEnabled: StateFlow<Boolean> = _scrobbleEnabled.asStateFlow()

    private val _lastScrobbleTime = MutableStateFlow(0L)
    val lastScrobbleTime: StateFlow<Long> = _lastScrobbleTime.asStateFlow()

    fun setScrobbleEnabled(enabled: Boolean) {
        _scrobbleEnabled.value = enabled
        prefs.edit().putBoolean("scrobble_enabled", enabled).apply()
    }

    private fun checkScrobble() {
        if (!_scrobbleEnabled.value) return
        if (hasScrobbled) return
        val song = _currentSong.value ?: return
        // Duplicate prevention: don't re-scrobble same song if user seeked back
        if (song.id == lastScrobbledSongId) return
        val pos = _positionMs.value
        val dur = song.duration?.let { it * 1000L } ?: _durationMs.value
        val threshold = minOf(30_000L, dur / 2)
        if (pos >= threshold && threshold > 0) {
            hasScrobbled = true
            lastScrobbledSongId = song.id
            scope.launch {
                try {
                    appState.subsonicClient.scrobble(song.id)
                    _lastScrobbleTime.value = System.currentTimeMillis()
                } catch (_: Throwable) {
                    // Enqueue for offline retry instead of silently dropping
                    try {
                        offlineActionQueue.enqueue("scrobble", mapOf("id" to song.id))
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    // MARK: - Queue Management

    fun skipToQueueItem(index: Int) {
        if (index in _queue.value.indices) {
            player.seekTo(index, 0)
        }
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= _queue.value.size) return
        val mutableQueue = _queue.value.toMutableList()
        mutableQueue.removeAt(index)
        _queue.value = mutableQueue
        player.removeMediaItem(index)
        scheduleSave()
    }

    fun clearQueue() {
        player.clearMediaItems()
        player.stop()
        _queue.value = emptyList()
        _currentSong.value = null
        _currentCoverArtUrl.value = null
        _currentIndex.value = -1
        _isPlaying.value = false
        _positionMs.value = 0
        _durationMs.value = 0
        scope.launch { playbackStateDao.clear() }
    }

    // MARK: - Internal

    private fun updateCurrentSong() {
        val index = player.currentMediaItemIndex
        _currentIndex.value = index
        val song = _queue.value.getOrNull(index)
        if (song != null) {
            _currentSong.value = song
            _currentCoverArtUrl.value = song.coverArt?.let {
                appState.subsonicClient.coverArtURL(it, size = 480)
            }
        }
        // If queue is empty (radio mode), keep the current dummy song
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                _positionMs.value = player.currentPosition.coerceAtLeast(0)
                val dur = player.duration
                if (dur > 0) _durationMs.value = dur
                checkScrobble()

                // Smart transitions + true dual-player crossfade
                val pastCooldown = System.currentTimeMillis() > seekCooldownUntil
                if (dur > 0 && player is ExoPlayer && !castManager.isCasting.value && pastCooldown) {
                    val remaining = dur - _positionMs.value
                    val nextIndex = player.currentMediaItemIndex + 1
                    val nextSong = _queue.value.getOrNull(nextIndex)
                    val currentSong = _currentSong.value

                    // Check smart transitions first
                    val smartDecision = smartTransitions.decideTransition(
                        currentSong = currentSong,
                        nextSong = nextSong,
                        isShuffled = _shuffleEnabled.value,
                        queueAlbumId = if (isPlayingAlbumInOrder()) currentSong?.albumId else null,
                    )

                    val shouldXfade = when {
                        smartDecision != null -> smartDecision.type == TransitionType.CROSSFADE
                        else -> shouldCrossfade()
                    }
                    val xfadeDuration = smartDecision?.crossfadeDurationMs ?: crossfadeDurationMs

                    if (shouldXfade) {
                        val nextItem = buildNextCrossfadeItem()
                        crossfadeEngine.checkCrossfade(
                            primaryPlayer = player as ExoPlayer,
                            nextMediaItem = nextItem,
                            remainingMs = remaining,
                            crossfadeDurationMs = xfadeDuration,
                            scope = scope,
                            baseVolume = (replayGainFactor * sleepFadeFactor).coerceIn(0f, 1f),
                            onCrossfadeComplete = { overlapMs ->
                                // Advance primary to next track and seek past the
                                // overlap region that the overlay already played
                                if (player.hasNextMediaItem()) {
                                    player.seekToNextMediaItem()
                                    player.seekTo(player.currentMediaItemIndex, overlapMs)
                                }
                            },
                        )
                    }

                    // Adaptive bitrate: check for upgrade on track boundary
                    adaptiveBitrate.checkUpgrade()

                    // Predictive pre-buffering at 80%
                    val progress = if (dur > 0) _positionMs.value.toFloat() / dur else 0f
                    if (nextSong != null) {
                        val client = appState.subsonicClient
                        val nextUrl = client.streamURL(nextSong.id)
                        preBufferManager.checkPreBuffer(progress, nextSong.id, nextUrl)
                    }
                }

                delay(250)
            }
        }
    }

    /** Whether crossfade should be active right now. */
    private fun shouldCrossfade(): Boolean {
        if (!_crossfadeEnabled.value) return false
        if (castManager.isCasting.value) return false
        if (_crossfadeOnlyOnShuffle.value && !_shuffleEnabled.value) return false
        // Disable crossfade in repeat-one mode (gapless loop instead)
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) return false
        return true
    }

    /** Build the next track's MediaItem for the crossfade overlay player (always HTTP, no local file). */
    private fun buildNextCrossfadeItem(): MediaItem? {
        val nextIndex = player.currentMediaItemIndex + 1
        val nextSong = _queue.value.getOrNull(nextIndex) ?: return null
        val client = appState.subsonicClient
        return nextSong.toHttpMediaItem(client) // Reuse — always HTTP stream URL
    }

    private fun stopPositionTracking() {
        positionJob?.cancel()
        _positionMs.value = player.currentPosition.coerceAtLeast(0)
    }

    private suspend fun resolveStreamUrl(url: String): String {
        val lower = url.lowercase()
        if (!lower.endsWith(".pls") && !lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) {
            return url
        }
        return try {
            val client = io.ktor.client.HttpClient(io.ktor.client.engine.android.Android)
            val response = client.get(url)
            val body = response.bodyAsText()
            if (lower.endsWith(".pls")) {
                // Parse PLS: look for File1=URL
                body.lines()
                    .firstOrNull { it.startsWith("File", ignoreCase = true) && it.contains("=") }
                    ?.substringAfter("=")
                    ?.trim()
                    ?: url
            } else {
                // Parse M3U: first non-comment, non-empty line
                body.lines()
                    .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    ?.trim()
                    ?: url
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun guessRadioMimeType(url: String): String? {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".m3u") || lower.endsWith(".m3u8") -> "application/x-mpegURL"
            lower.endsWith(".pls") -> "audio/x-scpls"
            lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".ogg") || lower.endsWith(".oga") -> "audio/ogg"
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".opus") -> "audio/opus"
            else -> null // Let ExoPlayer auto-detect
        }
    }

    private fun ensureServiceStarted() {
        if (!serviceStarted) {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
            serviceStarted = true
        }
    }

    // MARK: - ExoPlayer with EQ

    @OptIn(UnstableApi::class)
    private fun createPlayerWithEQ(): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            biquadProcessor as AudioProcessor
                        )
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
        // HTTP data source with User-Agent for radio stream compatibility
        // Throughput tracking for adaptive bitrate
        val throughputListener = object : TransferListener {
            private var transferStart = 0L
            private var transferBytes = 0L
            override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                transferStart = System.currentTimeMillis()
                transferBytes = 0
            }
            override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                transferBytes += bytesTransferred
            }
            override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                val elapsed = System.currentTimeMillis() - transferStart
                if (isNetwork && elapsed > 0 && transferBytes > 1024) {
                    adaptiveBitrate.recordThroughput(transferBytes, elapsed)
                }
            }
        }
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Vibrdrome/1.0 (Android)")
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
            .setTransferListener(throughputListener)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .build()
    }

    // MARK: - Persistence

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            saveQueueState()
        }
        scheduleQueueSync()
    }

    private suspend fun saveQueueState() {
        val songs = _queue.value
        if (songs.isEmpty()) {
            playbackStateDao.clear()
            return
        }
        val refs = songs.map { QueueSongRef.from(it) }
        playbackStateDao.save(
            SavedPlaybackState(
                queueJson = json.encodeToString(refs),
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
            )
        )
    }

    private suspend fun restoreQueue() {
        val saved = playbackStateDao.get() ?: return
        val songs: List<Song> = try {
            json.decodeFromString<List<QueueSongRef>>(saved.queueJson)
                .map { it.toSong() }
        } catch (_: Exception) {
            // Fall back to decoding full Song objects for migration from old format
            try {
                json.decodeFromString<List<Song>>(saved.queueJson)
            } catch (_: Exception) {
                return
            }
        }
        if (songs.isEmpty()) return
        // Wait for AppState to finish loading credentials (async secure prefs init)
        if (!appState.isConfigured.value) {
            val configured = withTimeoutOrNull(5000) { appState.isConfigured.first { it } }
            if (configured == null) return // No saved credentials — nothing to restore
        }

        _queue.value = songs
        val client = appState.subsonicClient
        val mediaItems = songs.map { it.toMediaItem(client) }
        val safeIndex = saved.currentIndex.coerceIn(0, songs.lastIndex)
        player.setMediaItems(mediaItems, safeIndex, saved.positionMs)
        player.repeatMode = saved.repeatMode
        player.shuffleModeEnabled = saved.shuffleEnabled
        player.prepare()
        updateCurrentSong()
        updateReplayGain()
    }

    private fun Song.toMediaItem(client: SubsonicClient, localPath: String? = null): MediaItem {
        val artUri = coverArt?.let { Uri.parse(client.coverArtURL(it, size = 480)) }
        val quality = appState.getEffectiveStreamQuality()
        val streamUri = if (localPath != null) {
            Uri.fromFile(java.io.File(localPath)).toString()
        } else {
            client.streamURL(id, maxBitRate = if (quality > 0) quality else null)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private suspend fun Song.toMediaItemResolved(client: SubsonicClient): MediaItem {
        val local = downloadDao.getBySongId(id)
        return toMediaItem(client, local?.filePath)
    }
}

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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.persistence.DownloadDao
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

class PlaybackManager(
    context: Context,
    private val appState: AppState,
    private val playbackStateDao: PlaybackStateDao,
    private val downloadDao: DownloadDao,
    val sleepTimer: SleepTimer,
    val eqCoefficientsStore: EQCoefficientsStore,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = appContext.getSharedPreferences("playback_prefs", android.content.Context.MODE_PRIVATE)

    @OptIn(UnstableApi::class)
    val biquadProcessor = BiquadAudioProcessor(eqCoefficientsStore)

    @OptIn(UnstableApi::class)
    val player: ExoPlayer = createPlayerWithEQ()

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
    private var crossfadeFactor = 1.0f
    private var fadeInJob: Job? = null

    // Volume factors
    private var replayGainFactor = 1.0f
    private var sleepFadeFactor = 1.0f

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null
    private var saveJob: Job? = null
    private var serviceStarted = false

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPositionTracking() else stopPositionTracking()
                scheduleSave()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSong()
                updateReplayGain()
                hasScrobbled = false
                scheduleSave()
                // Crossfade: fade in the new track
                if (_crossfadeEnabled.value) {
                    fadeInJob?.cancel()
                    crossfadeFactor = 0f
                    applyVolume()
                    fadeInJob = scope.launch {
                        val steps = (crossfadeDurationMs / 33).toInt().coerceAtLeast(1)
                        for (i in 1..steps) {
                            crossfadeFactor = i.toFloat() / steps
                            applyVolume()
                            delay(33)
                        }
                        crossfadeFactor = 1f
                        applyVolume()
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
                // Auto-retry on stream failure after 2 seconds
                scope.launch {
                    delay(2000)
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                        player.play()
                    }
                }
            }
        })

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
            player.playbackParameters = PlaybackParameters(savedSpeed)
        }

        scope.launch { restoreQueue() }
    }

    fun release() {
        positionJob?.cancel()
        saveJob?.cancel()
        scope.launch { saveQueueState() }
        player.release()
    }

    // MARK: - Volume System

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        prefs.edit().putBoolean("crossfade_enabled", enabled).apply()
        if (!enabled) {
            fadeInJob?.cancel()
            crossfadeFactor = 1.0f
            applyVolume()
        }
    }

    fun setCrossfadeDuration(durationMs: Long) {
        crossfadeDurationMs = durationMs.coerceIn(1000, 12000)
        prefs.edit().putLong("crossfade_duration", crossfadeDurationMs).apply()
    }

    private fun applyVolume() {
        player.volume = (replayGainFactor * sleepFadeFactor * crossfadeFactor).coerceIn(0f, 1f)
    }

    private fun updateReplayGain() {
        val song = _currentSong.value
        replayGainFactor = computeReplayGainFactor(song)
        applyVolume()
    }

    private fun computeReplayGainFactor(song: Song?): Float {
        val gain = song?.replayGain?.trackGain ?: return 1.0f
        return 10f.pow(gain.toFloat() / 20f).coerceIn(0.1f, 4f)
    }

    // MARK: - Playback

    fun play(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
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
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun previous() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _positionMs.value = positionMs
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
        player.playbackParameters = PlaybackParameters(speed)
        _playbackSpeed.value = speed
        prefs.edit().putFloat("playback_speed", speed).apply()
    }

    // MARK: - Radio

    fun playRadioStream(name: String, streamUrl: String) {
        ensureServiceStarted()
        _queue.value = emptyList()
        // Create a dummy Song so MiniPlayer shows
        _currentSong.value = Song(
            id = "radio_${streamUrl.hashCode()}",
            title = name,
            artist = "Radio",
        )
        _currentCoverArtUrl.value = null
        _currentIndex.value = -1
        _durationMs.value = 0
        _positionMs.value = 0
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist("Radio")
                    .setIsPlayable(true)
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    // MARK: - Scrobbling

    private var hasScrobbled = false

    private fun checkScrobble() {
        if (hasScrobbled) return
        val song = _currentSong.value ?: return
        val pos = _positionMs.value
        val dur = song.duration?.let { it * 1000L } ?: _durationMs.value
        val threshold = minOf(30_000L, dur / 2)
        if (pos >= threshold && threshold > 0) {
            hasScrobbled = true
            scope.launch {
                try {
                    appState.subsonicClient.scrobble(song.id)
                } catch (_: Throwable) {}
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
                // Crossfade: fade out as track approaches end
                if (_crossfadeEnabled.value && dur > 0) {
                    val remaining = dur - _positionMs.value
                    if (remaining in 1..crossfadeDurationMs && remaining < dur / 2) {
                        crossfadeFactor = remaining.toFloat() / crossfadeDurationMs
                        applyVolume()
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionTracking() {
        positionJob?.cancel()
        _positionMs.value = player.currentPosition.coerceAtLeast(0)
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
        return ExoPlayer.Builder(appContext, renderersFactory).build()
    }

    // MARK: - Persistence

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            saveQueueState()
        }
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
        if (!appState.isConfigured.value) return

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
        val streamUri = if (localPath != null) {
            Uri.fromFile(java.io.File(localPath)).toString()
        } else {
            client.streamURL(id)
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

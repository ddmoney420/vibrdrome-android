package com.vibrdrome.app.audio

import android.util.Log
import com.vibrdrome.app.network.JukeboxPlaylist
import com.vibrdrome.app.network.JukeboxStatus
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
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

/**
 * Manages jukebox mode — server-side playback through the Navidrome server's audio output.
 * The server plays music through its own speakers; the app acts as a remote control.
 *
 * When jukebox mode is active, local ExoPlayer is paused and all playback commands
 * are forwarded to the server via the jukeboxControl API.
 */
class JukeboxManager(private val appState: AppState) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client: SubsonicClient get() = appState.subsonicClient

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _status = MutableStateFlow<JukeboxStatus?>(null)
    val status: StateFlow<JukeboxStatus?> = _status.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _gain = MutableStateFlow(0.5f)
    val gain: StateFlow<Float> = _gain.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * Enable jukebox mode. Pauses local player and starts polling server status.
     */
    fun enable(localPauseAction: () -> Unit) {
        _enabled.value = true
        localPauseAction()
        startPolling()
        // Fetch initial state
        scope.launch {
            try {
                val playlist = client.jukeboxGet()
                if (playlist != null) {
                    _queue.value = playlist.entry ?: emptyList()
                    _status.value = JukeboxStatus(
                        currentIndex = playlist.currentIndex,
                        playing = playlist.playing,
                        gain = playlist.gain,
                        position = playlist.position,
                    )
                    _gain.value = playlist.gain
                    updateCurrentSong()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Disable jukebox mode. Stops server playback and polling.
     */
    fun disable() {
        _enabled.value = false
        pollingJob?.cancel()
        scope.launch {
            try { client.jukeboxStop() } catch (_: Exception) {}
        }
    }

    fun play() = jukeboxAction { client.jukeboxStart() }

    fun stop() = jukeboxAction { client.jukeboxStop() }

    fun skip(index: Int) = jukeboxAction {
        client.jukeboxSkip(index)
        updateCurrentSong()
    }

    fun next() = jukeboxAction {
        val current = _status.value?.currentIndex ?: 0
        val nextIndex = current + 1
        if (nextIndex < _queue.value.size) {
            client.jukeboxSkip(nextIndex)
        }
    }

    fun previous() = jukeboxAction {
        val current = _status.value?.currentIndex ?: 0
        val prevIndex = (current - 1).coerceAtLeast(0)
        client.jukeboxSkip(prevIndex)
    }

    fun setGain(gain: Float) = jukeboxAction {
        _gain.value = gain.coerceIn(0f, 1f)
        client.jukeboxSetGain(_gain.value)
    }

    /**
     * Set the jukebox queue to a list of songs and start playing.
     */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) = jukeboxAction {
        _queue.value = songs
        client.jukeboxSet(songs.map { it.id })
        client.jukeboxSkip(startIndex)
        client.jukeboxStart()
        updateCurrentSong()
    }

    fun addToQueue(song: Song) = jukeboxAction {
        _queue.value = _queue.value + song
        client.jukeboxAdd(listOf(song.id))
    }

    fun clearQueue() = jukeboxAction {
        _queue.value = emptyList()
        _currentSong.value = null
        client.jukeboxClear()
    }

    fun shuffle() = jukeboxAction {
        client.jukeboxShuffle()
        // Re-fetch to get new order
        val playlist = client.jukeboxGet()
        if (playlist != null) {
            _queue.value = playlist.entry ?: emptyList()
            updateCurrentSong()
        }
    }

    private fun updateCurrentSong() {
        val index = _status.value?.currentIndex ?: 0
        _currentSong.value = _queue.value.getOrNull(index)
    }

    /**
     * Poll server for jukebox status every 2 seconds while active.
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && _enabled.value) {
                try {
                    val status = client.jukeboxStatus()
                    if (status != null) {
                        _status.value = status
                        _gain.value = status.gain
                        updateCurrentSong()
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    private fun jukeboxAction(action: suspend () -> Unit) {
        if (!_enabled.value) {
            Log.w("JukeboxManager", "Action ignored — jukebox not enabled")
            return
        }
        scope.launch {
            try {
                Log.d("JukeboxManager", "Executing jukebox action")
                action()
                // Refresh status after action
                val status = client.jukeboxStatus()
                if (status != null) {
                    Log.d("JukeboxManager", "Status: playing=${status.playing}, index=${status.currentIndex}, gain=${status.gain}")
                    _status.value = status
                    updateCurrentSong()
                }
            } catch (e: Exception) {
                Log.e("JukeboxManager", "Jukebox action failed", e)
            }
        }
    }
}

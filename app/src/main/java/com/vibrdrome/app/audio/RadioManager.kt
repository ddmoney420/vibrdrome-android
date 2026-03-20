package com.vibrdrome.app.audio

import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RadioManager {

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _radioType = MutableStateFlow("")
    val radioType: StateFlow<String> = _radioType.asStateFlow()

    private val _seedName = MutableStateFlow("")
    val seedName: StateFlow<String> = _seedName.asStateFlow()

    private val playedIds = mutableSetOf<String>()

    suspend fun startArtistRadio(
        artistId: String,
        artistName: String,
        client: SubsonicClient,
        playbackManager: PlaybackManager,
    ) {
        _isActive.value = true
        _radioType.value = "Artist Radio"
        _seedName.value = artistName
        playedIds.clear()

        val songs = fetchArtistRadioSongs(artistId, artistName, client)
        if (songs.isNotEmpty()) {
            playbackManager.play(songs)
        }
    }

    suspend fun startSongRadio(
        songId: String,
        songTitle: String,
        client: SubsonicClient,
        playbackManager: PlaybackManager,
    ) {
        _isActive.value = true
        _radioType.value = "Song Radio"
        _seedName.value = songTitle
        playedIds.clear()

        val similar = client.getSimilarSongs(songId, count = 50)
        val deduped = deduplicateSongs(similar)
        if (deduped.isNotEmpty()) {
            playbackManager.play(deduped)
        }
    }

    suspend fun refill(
        client: SubsonicClient,
        playbackManager: PlaybackManager,
    ) {
        if (!_isActive.value) return
        val currentQueue = playbackManager.queue.value
        currentQueue.forEach { playedIds.add(it.id) }

        val newSongs = when (_radioType.value) {
            "Artist Radio" -> {
                val random = client.getRandomSongs(size = 30)
                deduplicateSongs(random)
            }
            "Song Radio" -> {
                val lastSong = currentQueue.lastOrNull() ?: return
                val similar = client.getSimilarSongs(lastSong.id, count = 30)
                deduplicateSongs(similar)
            }
            else -> emptyList()
        }

        newSongs.forEach { playbackManager.addToQueue(it) }
    }

    fun stop() {
        _isActive.value = false
        _radioType.value = ""
        _seedName.value = ""
        playedIds.clear()
    }

    private suspend fun fetchArtistRadioSongs(
        artistId: String,
        artistName: String,
        client: SubsonicClient,
    ): List<Song> {
        val sources = mutableListOf<Song>()

        // Primary: similar songs from this artist
        try {
            sources.addAll(client.getSimilarSongs(artistId, count = 30))
        } catch (_: Throwable) {}

        // Secondary: top songs by this artist
        try {
            sources.addAll(client.getTopSongs(artistName, count = 20))
        } catch (_: Throwable) {}

        // Blend: interleave primary and secondary
        val blended = interleave(sources)
        return deduplicateSongs(blended).take(50)
    }

    private fun interleave(songs: List<Song>): List<Song> {
        if (songs.size <= 1) return songs
        val shuffled = songs.shuffled()
        return shuffled
    }

    private fun deduplicateSongs(songs: List<Song>): List<Song> {
        val seen = mutableSetOf<String>()
        seen.addAll(playedIds)
        return songs.filter { song ->
            val key = "${song.title.lowercase()}-${song.artist?.lowercase()}"
            if (song.id in seen || key in seen) {
                false
            } else {
                seen.add(song.id)
                seen.add(key)
                true
            }
        }
    }
}

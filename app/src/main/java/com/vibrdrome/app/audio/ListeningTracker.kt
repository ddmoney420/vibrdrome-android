package com.vibrdrome.app.audio

import com.vibrdrome.app.network.Song
import com.vibrdrome.app.persistence.ListeningSession
import com.vibrdrome.app.persistence.ListeningStatsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks listening sessions by observing PlaybackManager state.
 * Records when tracks start, how long they're played, and whether they were
 * completed (>80% played) or skipped (<20% played).
 */
class ListeningTracker(private val dao: ListeningStatsDao) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private var currentSessionId: Long? = null
    private var currentSong: Song? = null
    private var sessionStartTime: Long = 0
    private var accumulatedMs: Long = 0
    private var lastResumeTime: Long = 0
    private var isPlaying = false

    /**
     * Called when a new track starts playing.
     */
    fun onTrackStarted(song: Song) {
        scope.launch {
            mutex.withLock {
                // Finalize previous session
                finalizeCurrentSessionLocked()

                currentSong = song
                sessionStartTime = System.currentTimeMillis()
                accumulatedMs = 0
                lastResumeTime = sessionStartTime
                isPlaying = true

                // Insert new session
                val session = ListeningSession(
                    songId = song.id,
                    artistName = song.artist,
                    albumName = song.album,
                    genre = song.genre,
                    startedAt = sessionStartTime,
                    trackDurationMs = (song.duration ?: 0) * 1000L,
                )
                currentSessionId = dao.insert(session)
            }
        }
    }

    /**
     * Called when playback is paused.
     */
    fun onPaused() {
        if (isPlaying) {
            accumulatedMs += System.currentTimeMillis() - lastResumeTime
            isPlaying = false
            updateSession()
        }
    }

    /**
     * Called when playback is resumed.
     */
    fun onResumed() {
        if (!isPlaying) {
            lastResumeTime = System.currentTimeMillis()
            isPlaying = true
        }
    }

    /**
     * Called when playback stops entirely (queue cleared, app closing).
     */
    fun onStopped() {
        scope.launch { mutex.withLock { finalizeCurrentSessionLocked() } }
    }

    private suspend fun finalizeCurrentSessionLocked() {
        val id = currentSessionId ?: return
        val song = currentSong ?: return

        if (isPlaying) {
            accumulatedMs += System.currentTimeMillis() - lastResumeTime
            isPlaying = false
        }

        val trackDur = (song.duration ?: 0) * 1000L
        val completed = trackDur > 0 && accumulatedMs >= trackDur * 0.8
        val skipped = trackDur > 0 && accumulatedMs < trackDur * 0.2 && accumulatedMs > 0

        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(
            durationMs = accumulatedMs,
            completed = completed,
            skipped = skipped,
        ))

        currentSessionId = null
        currentSong = null
    }

    private fun updateSession() {
        val id = currentSessionId ?: return
        scope.launch {
            mutex.withLock {
                val existing = dao.getById(id) ?: return@withLock
                dao.update(existing.copy(durationMs = accumulatedMs))
            }
        }
    }
}

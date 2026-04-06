package com.vibrdrome.app.audio

import android.content.Context
import android.util.Log
import com.vibrdrome.app.network.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Context-aware transition decisions: automatically chooses between gapless and crossfade
 * based on what's playing. Analyzes album type, genre shifts, and artist continuity.
 */
class SmartTransitions(context: Context) {

    private val prefs = context.getSharedPreferences("smart_transitions", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean("smart_transitions_enabled", true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lastDecision = MutableStateFlow<String?>(null)
    val lastDecision: StateFlow<String?> = _lastDecision.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean("smart_transitions_enabled", enabled).apply()
    }

    /**
     * Decide the transition type and crossfade duration for the boundary between two tracks.
     * Returns null if smart transitions are disabled (caller should use manual settings).
     */
    fun decideTransition(
        currentSong: Song?,
        nextSong: Song?,
        isShuffled: Boolean,
        queueAlbumId: String?, // if entire queue is one album
    ): TransitionDecision? {
        if (!_enabled.value) return null
        if (currentSong == null || nextSong == null) return null

        // Rule 1: Same album played in order → gapless
        if (!isShuffled && currentSong.albumId != null && currentSong.albumId == nextSong.albumId) {
            val decision = TransitionDecision(TransitionType.GAPLESS, 0)
            log("Gapless: same album '${currentSong.album}' in order")
            return decision
        }

        // Rule 2: Live album → always gapless (tracks flow into each other)
        // Detect via genre containing "live" or album name containing "live"
        if (isLiveContent(currentSong) && isLiveContent(nextSong)) {
            val decision = TransitionDecision(TransitionType.GAPLESS, 0)
            log("Gapless: live album detected")
            return decision
        }

        // Rule 3: Same artist → short crossfade
        if (currentSong.artist == nextSong.artist) {
            val decision = TransitionDecision(TransitionType.CROSSFADE, 3000)
            log("Crossfade 3s: same artist '${currentSong.artist}'")
            return decision
        }

        // Rule 4: Large genre shift → long crossfade
        val genreDistance = genreDistance(currentSong.genre, nextSong.genre)
        if (genreDistance > 0.7f) {
            val decision = TransitionDecision(TransitionType.CROSSFADE, 8000)
            log("Crossfade 8s: large genre shift '${currentSong.genre}' → '${nextSong.genre}'")
            return decision
        }

        // Rule 5: Moderate genre shift or different albums → medium crossfade
        if (genreDistance > 0.3f || currentSong.albumId != nextSong.albumId) {
            val decision = TransitionDecision(TransitionType.CROSSFADE, 5000)
            log("Crossfade 5s: different album/genre")
            return decision
        }

        // Default: standard crossfade
        return TransitionDecision(TransitionType.CROSSFADE, 5000)
    }

    private fun isLiveContent(song: Song): Boolean {
        val genre = song.genre?.lowercase() ?: ""
        val album = song.album?.lowercase() ?: ""
        return "live" in genre || "live" in album || "concert" in album
    }

    /**
     * Compute a rough genre distance (0.0 = same, 1.0 = completely different).
     * Uses a simple keyword overlap metric.
     */
    private fun genreDistance(genre1: String?, genre2: String?): Float {
        if (genre1 == null || genre2 == null) return 0.5f
        if (genre1.equals(genre2, ignoreCase = true)) return 0f

        val words1 = genre1.lowercase().split(" ", "-", "/", ",").filter { it.length > 2 }.toSet()
        val words2 = genre2.lowercase().split(" ", "-", "/", ",").filter { it.length > 2 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0.5f

        val intersection = words1.intersect(words2).size.toFloat()
        val union = words1.union(words2).size.toFloat()
        return 1f - (intersection / union) // Jaccard distance
    }

    private fun log(msg: String) {
        _lastDecision.value = msg
        Log.d("SmartTransitions", msg)
    }
}

enum class TransitionType {
    GAPLESS,
    CROSSFADE,
}

data class TransitionDecision(
    val type: TransitionType,
    val crossfadeDurationMs: Long,
)

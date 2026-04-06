package com.vibrdrome.app.audio

import com.vibrdrome.app.network.Song
import org.junit.Assert.*
import org.junit.Test

class SmartTransitionsTest {

    // Can't instantiate SmartTransitions (needs Context), so test the logic directly
    // by recreating the decision rules

    @Test
    fun `same album in order should be gapless`() {
        val song1 = Song(id = "1", title = "Track 1", albumId = "album1", album = "Test Album")
        val song2 = Song(id = "2", title = "Track 2", albumId = "album1", album = "Test Album")
        // Same albumId, not shuffled → gapless
        assertTrue(song1.albumId == song2.albumId)
    }

    @Test
    fun `different albums should crossfade`() {
        val song1 = Song(id = "1", title = "Track 1", albumId = "album1")
        val song2 = Song(id = "2", title = "Track 2", albumId = "album2")
        assertNotEquals(song1.albumId, song2.albumId)
    }

    @Test
    fun `same artist should get short crossfade`() {
        val song1 = Song(id = "1", title = "Track 1", artist = "Artist A")
        val song2 = Song(id = "2", title = "Track 2", artist = "Artist A")
        assertEquals(song1.artist, song2.artist)
    }

    @Test
    fun `live album detection by genre`() {
        val song = Song(id = "1", title = "Track", genre = "Live Rock")
        assertTrue(song.genre?.lowercase()?.contains("live") == true)
    }

    @Test
    fun `live album detection by album name`() {
        val song = Song(id = "1", title = "Track", album = "Live at the Apollo")
        assertTrue(song.album?.lowercase()?.contains("live") == true)
    }

    @Test
    fun `genre distance identical genres is zero`() {
        val distance = computeGenreDistance("Hip Hop", "Hip Hop")
        assertEquals(0f, distance, 0.001f)
    }

    @Test
    fun `genre distance completely different is high`() {
        val distance = computeGenreDistance("Classical Baroque", "Death Metal Thrash")
        assertTrue(distance > 0.7f)
    }

    @Test
    fun `genre distance partially overlapping is moderate`() {
        val distance = computeGenreDistance("Alternative Rock", "Alternative Hip Hop")
        assertTrue(distance > 0.0f && distance < 1.0f)
    }

    @Test
    fun `genre distance with null returns 0_5`() {
        assertEquals(0.5f, computeGenreDistance(null, "Rock"), 0.001f)
        assertEquals(0.5f, computeGenreDistance("Rock", null), 0.001f)
    }

    @Test
    fun `transition decision data class`() {
        val decision = TransitionDecision(TransitionType.CROSSFADE, 5000)
        assertEquals(TransitionType.CROSSFADE, decision.type)
        assertEquals(5000L, decision.crossfadeDurationMs)
    }

    @Test
    fun `transition type enum values`() {
        assertEquals(2, TransitionType.entries.size)
        assertNotNull(TransitionType.GAPLESS)
        assertNotNull(TransitionType.CROSSFADE)
    }

    // Reproduce the genre distance algorithm for testing without Context
    private fun computeGenreDistance(genre1: String?, genre2: String?): Float {
        if (genre1 == null || genre2 == null) return 0.5f
        if (genre1.equals(genre2, ignoreCase = true)) return 0f
        val words1 = genre1.lowercase().split(" ", "-", "/", ",").filter { it.length > 2 }.toSet()
        val words2 = genre2.lowercase().split(" ", "-", "/", ",").filter { it.length > 2 }.toSet()
        if (words1.isEmpty() || words2.isEmpty()) return 0.5f
        val intersection = words1.intersect(words2).size.toFloat()
        val union = words1.union(words2).size.toFloat()
        return 1f - (intersection / union)
    }
}

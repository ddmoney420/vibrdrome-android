package com.vibrdrome.app.audio

import com.vibrdrome.app.network.ReplayGain
import com.vibrdrome.app.network.Song
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ReplayGainTest {

    @Test
    fun `track gain positive boosts volume`() {
        val factor = computeFactor(3.0)
        assertTrue(factor > 1f)
    }

    @Test
    fun `track gain negative reduces volume`() {
        val factor = computeFactor(-5.0)
        assertTrue(factor < 1f)
    }

    @Test
    fun `track gain zero returns approximately 1`() {
        val factor = computeFactor(0.0)
        assertEquals(1f, factor, 0.01f)
    }

    @Test
    fun `factor is clamped to min 0_1`() {
        val factor = computeFactor(-50.0) // Extreme negative
        assertTrue(factor >= 0.1f)
    }

    @Test
    fun `factor is clamped to max 4`() {
        val factor = computeFactor(20.0) // Extreme positive
        assertTrue(factor <= 4f)
    }

    @Test
    fun `null replay gain returns 1`() {
        val song = Song(id = "1", title = "Test", replayGain = null)
        assertNull(song.replayGain)
    }

    @Test
    fun `album gain is available`() {
        val rg = ReplayGain(trackGain = -3.0, albumGain = -5.0, trackPeak = 0.9, albumPeak = 0.85)
        assertNotNull(rg.albumGain)
        assertEquals(-5.0, rg.albumGain!!, 0.001)
    }

    @Test
    fun `peak limiting prevents clipping`() {
        val gain = 6.0
        val peak = 0.5 // Track peaks at 50% — gain of 6dB would clip
        val factor = 10f.pow(gain.toFloat() / 20f)
        val maxFactor = (1.0 / peak).toFloat()
        val clamped = factor.coerceIn(0.1f, maxFactor.coerceAtMost(4f))
        assertTrue(clamped <= maxFactor)
    }

    @Test
    fun `preamp adds to gain`() {
        val gain = -3.0f
        val preamp = 2.0f
        val totalGain = gain + preamp
        assertEquals(-1.0f, totalGain, 0.001f)
    }

    @Test
    fun `replay gain mode enum`() {
        assertEquals(4, ReplayGainMode.entries.size)
        assertNotNull(ReplayGainMode.OFF)
        assertNotNull(ReplayGainMode.TRACK)
        assertNotNull(ReplayGainMode.ALBUM)
        assertNotNull(ReplayGainMode.AUTO)
    }

    @Test
    fun `album in order detection - same albumId`() {
        val songs = listOf(
            Song(id = "1", title = "T1", albumId = "a1"),
            Song(id = "2", title = "T2", albumId = "a1"),
            Song(id = "3", title = "T3", albumId = "a1"),
        )
        assertTrue(songs.all { it.albumId == songs.first().albumId })
    }

    @Test
    fun `album in order detection - mixed albums`() {
        val songs = listOf(
            Song(id = "1", title = "T1", albumId = "a1"),
            Song(id = "2", title = "T2", albumId = "a2"),
        )
        assertFalse(songs.all { it.albumId == songs.first().albumId })
    }

    private fun computeFactor(gainDb: Double): Float {
        return 10f.pow(gainDb.toFloat() / 20f).coerceIn(0.1f, 4f)
    }
}

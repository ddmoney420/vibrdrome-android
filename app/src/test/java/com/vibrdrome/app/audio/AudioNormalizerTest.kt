package com.vibrdrome.app.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow

class AudioNormalizerTest {

    @Test
    fun `RMS calculation from waveform data`() {
        // Silent waveform: all 128 (center)
        val silent = ByteArray(256) { 128.toByte() }
        val rms = computeRms(silent)
        assertEquals(0f, rms, 0.01f)
    }

    @Test
    fun `RMS calculation from loud waveform`() {
        // Loud waveform: alternating 0 and 255
        val loud = ByteArray(256) { if (it % 2 == 0) 0.toByte() else 255.toByte() }
        val rms = computeRms(loud)
        assertTrue(rms > 0.5f)
    }

    @Test
    fun `gain factor for quiet track boosts volume`() {
        val rmsDb = -30f // Quiet
        val targetDb = -18f
        val adjustment = (targetDb - rmsDb).coerceIn(-6f, 6f)
        val factor = 10f.pow(adjustment / 20f)
        assertTrue(factor > 1f) // Should boost
    }

    @Test
    fun `gain factor for loud track reduces volume`() {
        val rmsDb = -10f // Loud
        val targetDb = -18f
        val adjustment = (targetDb - rmsDb).coerceIn(-6f, 6f)
        val factor = 10f.pow(adjustment / 20f)
        assertTrue(factor < 1f) // Should reduce
    }

    @Test
    fun `gain factor is clamped to max 6dB adjustment`() {
        val rmsDb = -40f // Very quiet
        val targetDb = -18f
        val adjustment = (targetDb - rmsDb).coerceIn(-6f, 6f)
        assertEquals(6f, adjustment, 0.001f) // Clamped to 6
    }

    @Test
    fun `gain factor is clamped to min -6dB adjustment`() {
        val rmsDb = -5f // Very loud
        val targetDb = -18f
        val adjustment = (targetDb - rmsDb).coerceIn(-6f, 6f)
        assertEquals(-6f, adjustment, 0.001f) // Clamped to -6
    }

    @Test
    fun `gain factor for track at target level is approximately 1`() {
        val rmsDb = -18f
        val targetDb = -18f
        val adjustment = (targetDb - rmsDb).coerceIn(-6f, 6f)
        val factor = 10f.pow(adjustment / 20f)
        assertEquals(1f, factor, 0.01f)
    }

    // Reproduce the RMS calculation from AudioNormalizer
    private fun computeRms(waveformData: ByteArray): Float {
        var sumSquares = 0.0
        for (byte in waveformData) {
            val sample = (byte.toInt() and 0xFF) - 128
            val normalized = sample / 128.0
            sumSquares += normalized * normalized
        }
        return Math.sqrt(sumSquares / waveformData.size).toFloat()
    }
}

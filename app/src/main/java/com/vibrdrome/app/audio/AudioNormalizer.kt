package com.vibrdrome.app.audio

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Auto-normalizes volume for tracks missing ReplayGain tags.
 *
 * Analyzes PCM waveform data (from Android Visualizer API) during the first few seconds
 * of playback and computes a gain adjustment to reach the target loudness.
 * Caches computed values per song ID to avoid re-analysis.
 */
class AudioNormalizer(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("audio_normalizer", Context.MODE_PRIVATE)

    /** Target RMS level in dB (reference: -18 dBFS is a common broadcast target) */
    private val targetRmsDb = -18f

    /** Max adjustment in dB (prevents extreme corrections) */
    private val maxAdjustmentDb = 6f

    /** Number of waveform samples needed before computing (roughly 3 seconds at ~10 captures/sec) */
    private val minSamples = 30

    private var _enabled = false
    var enabled: Boolean
        get() = _enabled
        set(value) { _enabled = value }

    // Per-track analysis state
    private var currentSongId: String? = null
    private var rmsAccumulator = 0.0
    private var sampleCount = 0
    private var analysisComplete = false

    /** The computed gain factor for the current track. 1.0 = no adjustment. */
    var gainFactor: Float = 1.0f
        private set

    /**
     * Called when a new track starts playing.
     * Checks cache first — if we've analyzed this song before, use the cached value.
     */
    fun onTrackChanged(songId: String, hasReplayGain: Boolean) {
        currentSongId = songId
        rmsAccumulator = 0.0
        sampleCount = 0
        analysisComplete = false

        // Don't normalize if track has ReplayGain or normalizer is disabled
        if (hasReplayGain || !_enabled) {
            gainFactor = 1.0f
            return
        }

        // Check cache
        val cached = prefs.getFloat("norm_$songId", Float.NaN)
        if (!cached.isNaN()) {
            gainFactor = cached
            analysisComplete = true
            return
        }

        // Will analyze via feedWaveform calls
        gainFactor = 1.0f
    }

    /**
     * Feed waveform data from the Visualizer API.
     * Call this during the first ~3 seconds of playback.
     * Returns the updated gain factor (may change as more samples arrive).
     */
    fun feedWaveform(waveformData: ByteArray): Float {
        if (analysisComplete || !_enabled) return gainFactor

        // Compute RMS of this waveform buffer
        var sumSquares = 0.0
        for (byte in waveformData) {
            // Visualizer data is unsigned 8-bit (0-255), center is 128
            val sample = (byte.toInt() and 0xFF) - 128
            val normalized = sample / 128.0
            sumSquares += normalized * normalized
        }
        val bufferRms = Math.sqrt(sumSquares / waveformData.size)
        rmsAccumulator += bufferRms
        sampleCount++

        if (sampleCount >= minSamples) {
            val avgRms = rmsAccumulator / sampleCount
            val rmsDb = if (avgRms > 0.0001) (20 * log10(avgRms)).toFloat() else -60f

            val adjustmentDb = (targetRmsDb - rmsDb).coerceIn(-maxAdjustmentDb, maxAdjustmentDb)
            gainFactor = 10f.pow(adjustmentDb / 20f).coerceIn(0.25f, 2.5f)
            analysisComplete = true

            // Cache the result
            currentSongId?.let { id ->
                prefs.edit().putFloat("norm_$id", gainFactor).apply()
            }
        }

        return gainFactor
    }

    /**
     * Clear the analysis cache. Called if user wants to re-analyze.
     */
    fun clearCache() {
        prefs.edit().clear().apply()
    }
}

package com.vibrdrome.app.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * Parses AutoEQ CSV and Equalizer APO configuration files into [EQPreset]s.
 *
 * AutoEQ format (CSV):
 *   frequency,raw,error,smoothed,error_smoothed,equalization,parametric_eq,fixed_band_eq
 *   20,0.0,-3.2,0.5,-2.8,2.8,...
 *
 * APO format:
 *   Filter 1: ON PK Fc 31 Hz Gain 3.5 dB Q 1.41
 *   Filter 2: ON PK Fc 62 Hz Gain -1.2 dB Q 1.41
 *
 * Both formats are mapped to our 10-band parametric EQ by finding the closest
 * band frequency and assigning the gain value.
 */
object AutoEQImporter {

    private val TARGET_FREQUENCIES = EQPresets.frequencies

    /**
     * Parse an AutoEQ CSV file content.
     * Uses the "fixed_band_eq" column if available, otherwise "equalization".
     */
    fun parseAutoEQCsv(content: String, name: String = "Imported"): EQPreset? {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return null

        val header = lines[0].lowercase().split(",").map { it.trim() }

        // Prefer fixed_band_eq column, fallback to equalization
        val gainColIndex = header.indexOf("fixed_band_eq").takeIf { it >= 0 }
            ?: header.indexOf("equalization").takeIf { it >= 0 }
            ?: return null
        val freqColIndex = header.indexOf("frequency").takeIf { it >= 0 } ?: 0

        // Parse frequency → gain pairs
        val freqGains = mutableListOf<Pair<Float, Float>>()
        for (line in lines.drop(1)) {
            val cols = line.split(",")
            if (cols.size <= gainColIndex) continue
            val freq = cols[freqColIndex].trim().toFloatOrNull() ?: continue
            val gain = cols[gainColIndex].trim().toFloatOrNull() ?: continue
            freqGains.add(freq to gain)
        }

        if (freqGains.isEmpty()) return null
        return mapToPreset(freqGains, name)
    }

    /**
     * Parse an Equalizer APO config file content.
     * Supports: Filter N: ON PK Fc <freq> Hz Gain <gain> dB Q <q>
     */
    fun parseAPOConfig(content: String, name: String = "Imported"): EQPreset? {
        val filterRegex = Regex(
            """Filter\s*\d*:\s*ON\s+PK\s+Fc\s+([\d.]+)\s*Hz\s+Gain\s+([+-]?[\d.]+)\s*dB""",
            RegexOption.IGNORE_CASE,
        )

        val freqGains = mutableListOf<Pair<Float, Float>>()
        for (line in content.lines()) {
            val match = filterRegex.find(line) ?: continue
            val freq = match.groupValues[1].toFloatOrNull() ?: continue
            val gain = match.groupValues[2].toFloatOrNull() ?: continue
            freqGains.add(freq to gain)
        }

        if (freqGains.isEmpty()) return null
        return mapToPreset(freqGains, name)
    }

    /**
     * Auto-detect format and parse.
     */
    fun parse(content: String, name: String = "Imported"): EQPreset? {
        // APO files have "Filter" lines
        if (content.lines().any { it.trimStart().startsWith("Filter", ignoreCase = true) }) {
            return parseAPOConfig(content, name)
        }
        // Otherwise try CSV
        return parseAutoEQCsv(content, name)
    }

    /**
     * Map arbitrary frequency/gain pairs to our fixed 10-band EQ.
     * For each of our target bands, find the closest frequency in the input and use its gain.
     * If multiple input freqs map to the same band, average them.
     */
    private fun mapToPreset(freqGains: List<Pair<Float, Float>>, name: String): EQPreset {
        val gains = FloatArray(TARGET_FREQUENCIES.size) { 0f }
        val counts = IntArray(TARGET_FREQUENCIES.size) { 0 }

        for ((freq, gain) in freqGains) {
            val bandIndex = findClosestBand(freq)
            gains[bandIndex] += gain
            counts[bandIndex]++
        }

        // Average where multiple source freqs mapped to the same band
        for (i in gains.indices) {
            if (counts[i] > 0) {
                gains[i] = (gains[i] / counts[i]).coerceIn(EQPresets.MIN_GAIN, EQPresets.MAX_GAIN)
            }
        }

        return EQPreset(name, gains)
    }

    /**
     * Find the closest band index using log-frequency distance (perceptually uniform).
     */
    private fun findClosestBand(freq: Float): Int {
        val logFreq = log10(freq.coerceAtLeast(1f))
        var bestIndex = 0
        var bestDist = Float.MAX_VALUE
        for (i in TARGET_FREQUENCIES.indices) {
            val dist = abs(logFreq - log10(TARGET_FREQUENCIES[i]))
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return bestIndex
    }
}

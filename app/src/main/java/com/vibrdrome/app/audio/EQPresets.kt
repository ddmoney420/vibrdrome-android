package com.vibrdrome.app.audio

data class EQPreset(
    val name: String,
    val gains: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EQPreset) return false
        return name == other.name && gains.contentEquals(other.gains)
    }

    override fun hashCode() = 31 * name.hashCode() + gains.contentHashCode()
}

object EQPresets {
    val frequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val bandLabels = arrayOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    const val BAND_COUNT = 10
    const val MIN_GAIN = -12f
    const val MAX_GAIN = 12f
    const val DEFAULT_Q = 1.414f

    val flat = EQPreset("Flat", FloatArray(BAND_COUNT) { 0f })
    val rock = EQPreset("Rock", floatArrayOf(4f, 3f, 1f, -1f, -2f, 1f, 3f, 4f, 5f, 4f))
    val pop = EQPreset("Pop", floatArrayOf(-2f, 1f, 4f, 5f, 3f, 0f, -1f, -1f, 2f, 3f))
    val jazz = EQPreset("Jazz", floatArrayOf(3f, 2f, 0f, 2f, -2f, -2f, 0f, 2f, 4f, 4f))
    val classical = EQPreset("Classical", floatArrayOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f))
    val hiphop = EQPreset("Hip Hop", floatArrayOf(5f, 4f, 1f, 3f, -1f, -1f, 1f, 0f, 2f, 3f))
    val electronic = EQPreset("Electronic", floatArrayOf(5f, 4f, 0f, -1f, -2f, 0f, 1f, 4f, 5f, 4f))
    val vocal = EQPreset("Vocal", floatArrayOf(-2f, -1f, 0f, 2f, 5f, 5f, 3f, 1f, 0f, -1f))
    val bassBoost = EQPreset("Bass Boost", floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f))
    val trebleBoost = EQPreset("Treble Boost", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f))

    val allPresets = listOf(flat, rock, pop, jazz, classical, hiphop, electronic, vocal, bassBoost, trebleBoost)
}

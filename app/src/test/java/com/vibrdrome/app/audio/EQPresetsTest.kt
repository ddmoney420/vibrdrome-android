package com.vibrdrome.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EQPresetsTest {

    @Test
    fun `all presets have correct band count`() {
        EQPresets.allPresets.forEach { preset ->
            assertEquals(
                "Preset '${preset.name}' should have ${EQPresets.BAND_COUNT} bands",
                EQPresets.BAND_COUNT,
                preset.gains.size,
            )
        }
    }

    @Test
    fun `flat preset has all zero gains`() {
        EQPresets.flat.gains.forEach { gain ->
            assertEquals(0f, gain, 0.0001f)
        }
    }

    @Test
    fun `all preset gains are within min-max range`() {
        EQPresets.allPresets.forEach { preset ->
            preset.gains.forEach { gain ->
                assertTrue(
                    "Gain $gain in preset '${preset.name}' exceeds range",
                    gain >= EQPresets.MIN_GAIN && gain <= EQPresets.MAX_GAIN,
                )
            }
        }
    }

    @Test
    fun `frequencies array has correct count`() {
        assertEquals(EQPresets.BAND_COUNT, EQPresets.frequencies.size)
    }

    @Test
    fun `frequencies are monotonically increasing`() {
        for (i in 1 until EQPresets.frequencies.size) {
            assertTrue(
                "Frequency ${EQPresets.frequencies[i]} should be > ${EQPresets.frequencies[i - 1]}",
                EQPresets.frequencies[i] > EQPresets.frequencies[i - 1],
            )
        }
    }

    @Test
    fun `band labels have correct count`() {
        assertEquals(EQPresets.BAND_COUNT, EQPresets.bandLabels.size)
    }

    @Test
    fun `preset names are unique`() {
        val names = EQPresets.allPresets.map { it.name }
        assertEquals("Preset names should be unique", names.size, names.toSet().size)
    }

    @Test
    fun `allPresets includes flat`() {
        assertTrue(EQPresets.allPresets.contains(EQPresets.flat))
    }
}

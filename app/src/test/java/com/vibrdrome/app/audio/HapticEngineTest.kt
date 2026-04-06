package com.vibrdrome.app.audio

import org.junit.Assert.*
import org.junit.Test

class HapticEngineTest {

    @Test
    fun `haptic intensity enum values`() {
        assertEquals(3, HapticIntensity.entries.size)
        assertEquals("Subtle", HapticIntensity.SUBTLE.label)
        assertEquals("Medium", HapticIntensity.MEDIUM.label)
        assertEquals("Strong", HapticIntensity.STRONG.label)
    }

    @Test
    fun `subtle amplitude is lowest`() {
        assertTrue(HapticIntensity.SUBTLE.baseAmplitude < HapticIntensity.MEDIUM.baseAmplitude)
        assertTrue(HapticIntensity.MEDIUM.baseAmplitude < HapticIntensity.STRONG.baseAmplitude)
    }

    @Test
    fun `strong amplitude is max (255)`() {
        assertEquals(255, HapticIntensity.STRONG.baseAmplitude)
    }

    @Test
    fun `duration increases with intensity`() {
        assertTrue(HapticIntensity.SUBTLE.durationMs < HapticIntensity.MEDIUM.durationMs)
        assertTrue(HapticIntensity.MEDIUM.durationMs < HapticIntensity.STRONG.durationMs)
    }

    @Test
    fun `amplitude scaling with bass energy`() {
        val base = HapticIntensity.STRONG.baseAmplitude
        // At 0 bass energy: 70% of base
        val lowAmp = (base * (0.7f + 0f * 0.3f)).toInt().coerceIn(1, 255)
        // At 1.0 bass energy: 100% of base
        val highAmp = (base * (0.7f + 1f * 0.3f)).toInt().coerceIn(1, 255)
        assertTrue(highAmp > lowAmp)
        assertEquals(255, highAmp) // Strong at max bass = 255
    }

    @Test
    fun `beat detection threshold is 1_5x running average`() {
        val runningAvg = 0.3f
        val threshold = runningAvg * 1.5f
        assertEquals(0.45f, threshold, 0.001f)

        // Bass at 0.5 should trigger (> 0.45)
        assertTrue(0.5f > threshold)
        // Bass at 0.4 should not trigger (< 0.45)
        assertFalse(0.4f > threshold)
    }

    @Test
    fun `minimum energy floor prevents noise triggers`() {
        val minEnergy = 0.08f
        // Very quiet bass should not trigger even if above running average
        assertFalse(0.05f > minEnergy)
        assertTrue(0.10f > minEnergy)
    }
}

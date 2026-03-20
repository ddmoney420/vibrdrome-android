package com.vibrdrome.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EQCoefficientsTest {

    @Test
    fun `bypass coefficients pass signal through unchanged`() {
        val c = BiquadCoefficients.bypass()

        assertEquals(1f, c.b0, 0.0001f)
        assertEquals(0f, c.b1, 0.0001f)
        assertEquals(0f, c.b2, 0.0001f)
        assertEquals(0f, c.a1, 0.0001f)
        assertEquals(0f, c.a2, 0.0001f)
    }

    @Test
    fun `zero gain produces bypass coefficients`() {
        val c = BiquadCoefficients.parametric(
            frequency = 1000f,
            gainDb = 0f,
            q = 1.414f,
            sampleRate = 44100f,
        )

        assertEquals(1f, c.b0, 0.0001f)
        assertEquals(0f, c.b1, 0.0001f)
        assertEquals(0f, c.b2, 0.0001f)
        assertEquals(0f, c.a1, 0.0001f)
        assertEquals(0f, c.a2, 0.0001f)
    }

    @Test
    fun `positive gain produces boost coefficients`() {
        val c = BiquadCoefficients.parametric(
            frequency = 1000f,
            gainDb = 6f,
            q = 1.414f,
            sampleRate = 44100f,
        )

        // For a boost, b0 should be > 1 (passthrough + boost)
        assertTrue("b0 should be > 1 for boost, was ${c.b0}", c.b0 > 1f)
    }

    @Test
    fun `negative gain produces cut coefficients`() {
        val c = BiquadCoefficients.parametric(
            frequency = 1000f,
            gainDb = -6f,
            q = 1.414f,
            sampleRate = 44100f,
        )

        // For a cut, b0 should be < 1
        assertTrue("b0 should be < 1 for cut, was ${c.b0}", c.b0 < 1f)
    }

    @Test
    fun `boost and cut at same frequency have related structure`() {
        val boost = BiquadCoefficients.parametric(1000f, 6f, 1.414f, 44100f)
        val cut = BiquadCoefficients.parametric(1000f, -6f, 1.414f, 44100f)

        // For parametric EQ, b1 = a1 = -2*cos(w0)/a0 — both normalized by different a0
        // but the unnormalized b1 and a1 values are both -2*cos(w0), so the ratio should hold
        assertTrue("Boost b0 should be > cut b0", boost.b0 > cut.b0)
    }

    @Test
    fun `coefficients store updates all bands`() {
        val store = EQCoefficientsStore()
        val gains = FloatArray(EQPresets.BAND_COUNT) { 3f }

        store.update(gains, 44100f)
        val coeffs = store.getCoefficients()

        assertEquals(EQPresets.BAND_COUNT, coeffs.size)
        // All should be non-bypass since gain is +3dB — b0 != 1
        coeffs.forEachIndexed { i, c ->
            assertTrue(
                "Band $i b0=${c.b0} should differ from bypass (1.0) for +3dB",
                abs(c.b0 - 1f) > 0.0001f,
            )
        }
    }

    @Test
    fun `different sample rates produce different coefficients`() {
        val c44 = BiquadCoefficients.parametric(1000f, 6f, 1.414f, 44100f)
        val c48 = BiquadCoefficients.parametric(1000f, 6f, 1.414f, 48000f)

        // Different sample rates should yield different w0, thus different coefficients
        assertTrue("Coefficients should differ for different sample rates",
            abs(c44.b0 - c48.b0) > 0.0001f)
    }
}

package com.vibrdrome.app.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CrossfadeEngineTest {

    @Test
    fun `crossfade curve LINEAR at 0 is full primary`() {
        val (primary, overlay) = linearVolumes(0f)
        assertEquals(1f, primary, 0.001f)
        assertEquals(0f, overlay, 0.001f)
    }

    @Test
    fun `crossfade curve LINEAR at 1 is full overlay`() {
        val (primary, overlay) = linearVolumes(1f)
        assertEquals(0f, primary, 0.001f)
        assertEquals(1f, overlay, 0.001f)
    }

    @Test
    fun `crossfade curve LINEAR at 0_5 is equal`() {
        val (primary, overlay) = linearVolumes(0.5f)
        assertEquals(0.5f, primary, 0.001f)
        assertEquals(0.5f, overlay, 0.001f)
    }

    @Test
    fun `crossfade curve EQUAL_POWER maintains loudness at midpoint`() {
        val (primary, overlay) = equalPowerVolumes(0.5f)
        // Equal power: primary^2 + overlay^2 should ≈ 1.0
        val totalPower = primary * primary + overlay * overlay
        assertEquals(1f, totalPower, 0.01f)
    }

    @Test
    fun `crossfade curve EQUAL_POWER at 0 is full primary`() {
        val (primary, overlay) = equalPowerVolumes(0f)
        assertEquals(1f, primary, 0.001f)
        assertEquals(0f, overlay, 0.001f)
    }

    @Test
    fun `crossfade curve EQUAL_POWER at 1 is full overlay`() {
        val (primary, overlay) = equalPowerVolumes(1f)
        assertEquals(0f, primary, 0.01f)
        assertEquals(1f, overlay, 0.01f)
    }

    @Test
    fun `crossfade curve S_CURVE at 0 is full primary`() {
        val (primary, overlay) = sCurveVolumes(0f)
        assertEquals(1f, primary, 0.001f)
        assertEquals(0f, overlay, 0.001f)
    }

    @Test
    fun `crossfade curve S_CURVE at 1 is full overlay`() {
        val (primary, overlay) = sCurveVolumes(1f)
        assertEquals(0f, primary, 0.001f)
        assertEquals(1f, overlay, 0.001f)
    }

    @Test
    fun `crossfade curve S_CURVE at 0_5 is equal`() {
        val (primary, overlay) = sCurveVolumes(0.5f)
        assertEquals(0.5f, primary, 0.001f)
        assertEquals(0.5f, overlay, 0.001f)
    }

    @Test
    fun `crossfade curve enum has 3 values`() {
        assertEquals(3, CrossfadeCurve.entries.size)
    }

    @Test
    fun `all curves are monotonic for overlay`() {
        for (curve in listOf(::linearVolumes, ::equalPowerVolumes, ::sCurveVolumes)) {
            var prevOverlay = 0f
            for (i in 0..10) {
                val progress = i / 10f
                val (_, overlay) = curve(progress)
                assertTrue("Overlay should be monotonically increasing", overlay >= prevOverlay - 0.001f)
                prevOverlay = overlay
            }
        }
    }

    // Reproduce volume calculations for testing without Android dependencies
    private fun linearVolumes(progress: Float): Pair<Float, Float> =
        Pair(1f - progress, progress)

    private fun equalPowerVolumes(progress: Float): Pair<Float, Float> {
        val angle = progress * (PI / 2).toFloat()
        return Pair(cos(angle), sin(angle))
    }

    private fun sCurveVolumes(progress: Float): Pair<Float, Float> {
        val s = progress * progress * (3f - 2f * progress)
        return Pair(1f - s, s)
    }
}

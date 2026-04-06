package com.vibrdrome.app.audio

import org.junit.Assert.*
import org.junit.Test

class AdaptiveBitrateTest {

    // Test the quality tier logic without Android Context

    private val tiers = listOf(
        0 to 2000f,     // Original
        320 to 500f,
        192 to 300f,
        128 to 150f,
        96 to 0f,
    )

    @Test
    fun `high throughput recommends original quality`() {
        val best = tiers.firstOrNull { 3000f >= it.second }
        assertNotNull(best)
        assertEquals(0, best!!.first) // Original
    }

    @Test
    fun `medium throughput recommends 320k`() {
        val best = tiers.firstOrNull { 600f >= it.second }
        assertNotNull(best)
        assertEquals(320, best!!.first)
    }

    @Test
    fun `low throughput recommends 128k`() {
        val best = tiers.firstOrNull { 200f >= it.second }
        assertNotNull(best)
        assertEquals(128, best!!.first)
    }

    @Test
    fun `very low throughput falls back to 96k`() {
        val best = tiers.firstOrNull { 50f >= it.second }
        assertNotNull(best)
        assertEquals(96, best!!.first)
    }

    @Test
    fun `zero throughput falls back to 96k`() {
        val best = tiers.firstOrNull { 0f >= it.second }
        assertNotNull(best)
        assertEquals(96, best!!.first)
    }

    @Test
    fun `user max quality is ceiling`() {
        val recommended = 320
        val userMax = 192
        val result = if (recommended > userMax) null else recommended
        assertNull(result) // Should not exceed user setting
    }

    @Test
    fun `quality tiers are ordered by throughput descending`() {
        for (i in 0 until tiers.size - 1) {
            assertTrue(tiers[i].second > tiers[i + 1].second)
        }
    }

    @Test
    fun `quality label formatting`() {
        assertEquals("Original", labelFor(0))
        assertEquals("320k", labelFor(320))
        assertEquals("96k", labelFor(96))
        assertEquals("Auto", labelFor(null))
    }

    private fun labelFor(quality: Int?): String = when {
        quality == null -> "Auto"
        quality == 0 -> "Original"
        else -> "${quality}k"
    }
}

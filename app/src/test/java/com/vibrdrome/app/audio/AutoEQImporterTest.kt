package com.vibrdrome.app.audio

import org.junit.Assert.*
import org.junit.Test

class AutoEQImporterTest {

    @Test
    fun `parse APO config basic filters`() {
        val apo = """
            Preamp: -2.5 dB
            Filter 1: ON PK Fc 31 Hz Gain 3.5 dB Q 1.41
            Filter 2: ON PK Fc 62 Hz Gain -1.2 dB Q 1.41
            Filter 3: ON PK Fc 125 Hz Gain 0.0 dB Q 1.41
            Filter 4: ON PK Fc 250 Hz Gain 2.0 dB Q 1.41
            Filter 5: ON PK Fc 500 Hz Gain -0.5 dB Q 1.41
        """.trimIndent()

        val preset = AutoEQImporter.parseAPOConfig(apo, "Test APO")
        assertNotNull(preset)
        assertEquals("Test APO", preset!!.name)
        assertEquals(EQPresets.BAND_COUNT, preset.gains.size)
        assertEquals(3.5f, preset.gains[0], 0.1f) // 31 Hz
        assertEquals(-1.2f, preset.gains[1], 0.1f) // 62 Hz
    }

    @Test
    fun `parse APO config case insensitive`() {
        val apo = "filter 1: on pk fc 1000 hz gain 5.0 db q 1.41"
        val preset = AutoEQImporter.parseAPOConfig(apo, "Case Test")
        assertNotNull(preset)
        assertEquals(5.0f, preset!!.gains[5], 0.1f) // 1000 Hz → band 5
    }

    @Test
    fun `parse APO config empty returns null`() {
        assertNull(AutoEQImporter.parseAPOConfig("", "Empty"))
        assertNull(AutoEQImporter.parseAPOConfig("no filters here", "NoFilters"))
    }

    @Test
    fun `parse AutoEQ CSV with fixed_band_eq column`() {
        val csv = """
            frequency,raw,error,smoothed,error_smoothed,equalization,parametric_eq,fixed_band_eq
            20,-3.2,0.5,-2.8,2.8,1.0,0.5,2.0
            100,1.5,0.3,1.2,1.0,0.8,0.4,1.5
            1000,-0.5,0.2,-0.3,0.5,0.3,0.1,-0.5
            10000,2.0,0.4,1.8,1.5,1.2,0.6,2.5
        """.trimIndent()

        val preset = AutoEQImporter.parseAutoEQCsv(csv, "CSV Test")
        assertNotNull(preset)
        assertEquals("CSV Test", preset!!.name)
        assertEquals(EQPresets.BAND_COUNT, preset.gains.size)
    }

    @Test
    fun `parse CSV with no usable columns returns null`() {
        val csv = "col1,col2\n1,2\n3,4"
        assertNull(AutoEQImporter.parseAutoEQCsv(csv, "Bad"))
    }

    @Test
    fun `auto detect APO format`() {
        val apo = "Filter 1: ON PK Fc 100 Hz Gain 3.0 dB Q 1.41"
        val preset = AutoEQImporter.parse(apo, "Auto")
        assertNotNull(preset)
    }

    @Test
    fun `auto detect CSV format`() {
        val csv = "frequency,equalization\n100,3.0\n1000,-2.0"
        val preset = AutoEQImporter.parse(csv, "Auto")
        assertNotNull(preset)
    }

    @Test
    fun `gains are clamped to min-max range`() {
        val apo = "Filter 1: ON PK Fc 31 Hz Gain 25.0 dB Q 1.41"
        val preset = AutoEQImporter.parseAPOConfig(apo, "Clamped")
        assertNotNull(preset)
        assertTrue(preset!!.gains[0] <= EQPresets.MAX_GAIN)
    }

    @Test
    fun `frequency mapping uses log distance`() {
        // 30 Hz should map to band 0 (31 Hz), not band 1 (62 Hz)
        val apo = "Filter 1: ON PK Fc 30 Hz Gain 5.0 dB Q 1.41"
        val preset = AutoEQImporter.parseAPOConfig(apo, "LogDist")
        assertNotNull(preset)
        assertEquals(5.0f, preset!!.gains[0], 0.1f) // Should map to 31 Hz band
    }

    @Test
    fun `multiple frequencies mapping to same band are averaged`() {
        val apo = """
            Filter 1: ON PK Fc 28 Hz Gain 4.0 dB Q 1.41
            Filter 2: ON PK Fc 33 Hz Gain 6.0 dB Q 1.41
        """.trimIndent()
        val preset = AutoEQImporter.parseAPOConfig(apo, "Averaged")
        assertNotNull(preset)
        assertEquals(5.0f, preset!!.gains[0], 0.1f) // Average of 4 and 6
    }

    @Test
    fun `negative gains are preserved`() {
        val apo = "Filter 1: ON PK Fc 1000 Hz Gain -8.0 dB Q 1.41"
        val preset = AutoEQImporter.parseAPOConfig(apo, "Negative")
        assertNotNull(preset)
        assertEquals(-8.0f, preset!!.gains[5], 0.1f)
    }
}

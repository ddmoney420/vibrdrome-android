package com.vibrdrome.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `formatDuration with seconds only`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5))
        assertEquals("0:59", formatDuration(59))
    }

    @Test
    fun `formatDuration with minutes`() {
        assertEquals("1:00", formatDuration(60))
        assertEquals("3:45", formatDuration(225))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun `formatDuration with hours`() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:05:30", formatDuration(3930))
        assertEquals("2:30:00", formatDuration(9000))
    }

    @Test
    fun `formatDurationMs converts milliseconds to formatted string`() {
        assertEquals("0:00", formatDurationMs(0))
        assertEquals("0:00", formatDurationMs(500))
        assertEquals("0:01", formatDurationMs(1000))
        assertEquals("0:01", formatDurationMs(1999))
        assertEquals("3:45", formatDurationMs(225_000))
        assertEquals("1:00:00", formatDurationMs(3_600_000))
    }
}

package com.vibrdrome.app.network

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SubsonicModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `JukeboxStatus deserialization`() {
        val jsonStr = """{"currentIndex":3,"playing":true,"gain":0.75,"position":120}"""
        val status = json.decodeFromString<JukeboxStatus>(jsonStr)
        assertEquals(3, status.currentIndex)
        assertTrue(status.playing)
        assertEquals(0.75f, status.gain, 0.001f)
        assertEquals(120, status.position)
    }

    @Test
    fun `JukeboxStatus defaults`() {
        val status = JukeboxStatus()
        assertEquals(0, status.currentIndex)
        assertFalse(status.playing)
        assertEquals(0.5f, status.gain, 0.001f)
        assertEquals(0, status.position)
    }

    @Test
    fun `JukeboxPlaylist with entries`() {
        val jsonStr = """{"currentIndex":1,"playing":true,"gain":0.8,"position":60,"entry":[{"id":"s1","title":"Song 1"},{"id":"s2","title":"Song 2"}]}"""
        val playlist = json.decodeFromString<JukeboxPlaylist>(jsonStr)
        assertEquals(1, playlist.currentIndex)
        assertTrue(playlist.playing)
        assertEquals(2, playlist.entry?.size)
        assertEquals("Song 1", playlist.entry?.get(0)?.title)
    }

    @Test
    fun `NowPlayingEntry deserialization`() {
        val jsonStr = """{"username":"dmoney","minutesAgo":2,"id":"s1","title":"Test Song","artist":"Artist"}"""
        val entry = json.decodeFromString<NowPlayingEntry>(jsonStr)
        assertEquals("dmoney", entry.username)
        assertEquals(2, entry.minutesAgo)
        assertEquals("Test Song", entry.title)
    }

    @Test
    fun `PlayQueue deserialization`() {
        val jsonStr = """{"current":"s2","position":45000,"changed":"2026-04-05","changedBy":"android","entry":[{"id":"s1","title":"T1"},{"id":"s2","title":"T2"}]}"""
        val pq = json.decodeFromString<PlayQueue>(jsonStr)
        assertEquals("s2", pq.current)
        assertEquals(45000, pq.position)
        assertEquals("android", pq.changedBy)
        assertEquals(2, pq.entry?.size)
    }

    @Test
    fun `ReplayGain deserialization`() {
        val jsonStr = """{"trackGain":-3.5,"albumGain":-5.2,"trackPeak":0.95,"albumPeak":0.88}"""
        val rg = json.decodeFromString<ReplayGain>(jsonStr)
        assertEquals(-3.5, rg.trackGain!!, 0.001)
        assertEquals(-5.2, rg.albumGain!!, 0.001)
        assertEquals(0.95, rg.trackPeak!!, 0.001)
        assertEquals(0.88, rg.albumPeak!!, 0.001)
    }

    @Test
    fun `ReplayGain with null fields`() {
        val jsonStr = """{"trackGain":-3.5}"""
        val rg = json.decodeFromString<ReplayGain>(jsonStr)
        assertEquals(-3.5, rg.trackGain!!, 0.001)
        assertNull(rg.albumGain)
        assertNull(rg.trackPeak)
    }

    @Test
    fun `Song with genre field`() {
        val jsonStr = """{"id":"1","title":"Test","genre":"Electronic"}"""
        val song = json.decodeFromString<Song>(jsonStr)
        assertEquals("Electronic", song.genre)
    }

    @Test
    fun `InternetRadioStation coverArt workaround`() {
        val station = InternetRadioStation(
            id = "r1",
            name = "Test Radio",
            streamUrl = "http://stream.test.com",
            coverArt = "some-file.png",
        )
        // fixedCoverArt should return "ra-r1" for non-prefixed coverArt
        val fixed = station.fixedCoverArt()
        assertEquals("ra-r1", fixed)
    }

    @Test
    fun `InternetRadioStation already-prefixed coverArt`() {
        val station = InternetRadioStation(
            id = "r1",
            name = "Test",
            streamUrl = "http://test.com",
            coverArt = "ra-r1",
        )
        assertEquals("ra-r1", station.fixedCoverArt())
    }
}

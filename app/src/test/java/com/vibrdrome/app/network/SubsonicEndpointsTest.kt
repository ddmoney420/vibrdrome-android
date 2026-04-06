package com.vibrdrome.app.network

import org.junit.Assert.*
import org.junit.Test

class SubsonicEndpointsTest {

    @Test
    fun `jukebox control get action`() {
        val endpoint = SubsonicEndpoint.JukeboxControl(action = "get")
        assertEquals("/rest/jukeboxControl", endpoint.path)
        assertEquals("get", endpoint.queryItems["action"])
    }

    @Test
    fun `jukebox control skip with index and offset`() {
        val endpoint = SubsonicEndpoint.JukeboxControl(action = "skip", index = 5, offset = 30)
        assertEquals("skip", endpoint.queryItems["action"])
        assertEquals("5", endpoint.queryItems["index"])
        assertEquals("30", endpoint.queryItems["offset"])
    }

    @Test
    fun `jukebox control setGain`() {
        val endpoint = SubsonicEndpoint.JukeboxControl(action = "setGain", gain = 0.75f)
        assertEquals("setGain", endpoint.queryItems["action"])
        assertEquals("0.75", endpoint.queryItems["gain"])
    }

    @Test
    fun `jukebox control set with ids`() {
        val endpoint = SubsonicEndpoint.JukeboxControl(action = "set", ids = listOf("s1", "s2", "s3"))
        assertEquals("set", endpoint.queryItems["action"])
        assertEquals(3, endpoint.idParams.size)
        assertEquals("id" to "s1", endpoint.idParams[0])
        assertEquals("id" to "s2", endpoint.idParams[1])
    }

    @Test
    fun `jukebox control empty ids`() {
        val endpoint = SubsonicEndpoint.JukeboxControl(action = "start")
        assertTrue(endpoint.idParams.isEmpty())
        assertNull(endpoint.queryItems["index"])
        assertNull(endpoint.queryItems["offset"])
        assertNull(endpoint.queryItems["gain"])
    }

    @Test
    fun `getNowPlaying endpoint`() {
        val endpoint = SubsonicEndpoint.GetNowPlaying
        assertEquals("/rest/getNowPlaying", endpoint.path)
        assertTrue(endpoint.queryItems.isEmpty())
    }

    @Test
    fun `stream endpoint with format`() {
        val endpoint = SubsonicEndpoint.Stream("song1", maxBitRate = 320, format = "mp3")
        assertEquals("/rest/stream", endpoint.path)
        assertEquals("song1", endpoint.queryItems["id"])
        assertEquals("320", endpoint.queryItems["maxBitRate"])
        assertEquals("mp3", endpoint.queryItems["format"])
    }

    @Test
    fun `stream endpoint without optional params`() {
        val endpoint = SubsonicEndpoint.Stream("song1")
        assertEquals("song1", endpoint.queryItems["id"])
        assertNull(endpoint.queryItems["maxBitRate"])
        assertNull(endpoint.queryItems["format"])
    }

    @Test
    fun `savePlayQueue with ids`() {
        val endpoint = SubsonicEndpoint.SavePlayQueue(
            ids = listOf("a", "b", "c"),
            current = "b",
            position = 45000,
        )
        assertEquals("/rest/savePlayQueue", endpoint.path)
        assertEquals("b", endpoint.queryItems["current"])
        assertEquals("45000", endpoint.queryItems["position"])
        assertEquals(3, endpoint.idParams.size)
    }

    @Test
    fun `createInternetRadioStation params`() {
        val endpoint = SubsonicEndpoint.CreateInternetRadioStation(
            streamUrl = "http://stream.example.com",
            name = "Test Radio",
            homepageUrl = "http://example.com",
        )
        assertEquals("/rest/createInternetRadioStation", endpoint.path)
        assertEquals("http://stream.example.com", endpoint.queryItems["streamUrl"])
        assertEquals("Test Radio", endpoint.queryItems["name"])
    }
}

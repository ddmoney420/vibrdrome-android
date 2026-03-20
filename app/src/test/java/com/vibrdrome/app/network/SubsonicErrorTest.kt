package com.vibrdrome.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicErrorTest {

    @Test
    fun `HTTP 401 shows authentication message`() {
        val error = SubsonicError.HttpError(401)
        assertEquals("Authentication failed. Check your credentials.", error.userMessage)
    }

    @Test
    fun `HTTP 403 shows permission message`() {
        val error = SubsonicError.HttpError(403)
        assertEquals("Access denied. Your account may not have permission.", error.userMessage)
    }

    @Test
    fun `HTTP 500 shows server error message`() {
        val error = SubsonicError.HttpError(500)
        assertTrue(error.userMessage.contains("server"))
    }

    @Test
    fun `API error code 40 shows wrong credentials`() {
        val error = SubsonicError.ApiError(40, "Wrong username or password")
        assertEquals("Wrong username or password.", error.userMessage)
    }

    @Test
    fun `API error code 70 shows not found`() {
        val error = SubsonicError.ApiError(70, "Data not found")
        assertEquals("The requested item was not found.", error.userMessage)
    }

    @Test
    fun `NoServerConfigured shows config message`() {
        val error = SubsonicError.NoServerConfigured
        assertTrue(error.userMessage.contains("server"))
    }

    @Test
    fun `NetworkUnavailable shows network message`() {
        val error = SubsonicError.NetworkUnavailable
        assertTrue(error.userMessage.contains("network") || error.userMessage.contains("connection"))
    }

    @Test
    fun `userMessage for unknown exception returns generic message`() {
        val message = SubsonicError.userMessage(RuntimeException("something"))
        assertEquals("Something went wrong. Please try again.", message)
    }

    @Test
    fun `userMessage for SocketTimeoutException returns timeout message`() {
        val message = SubsonicError.userMessage(java.net.SocketTimeoutException())
        assertTrue(message.contains("timed out"))
    }

    @Test
    fun `userMessage for UnknownHostException returns unreachable message`() {
        val message = SubsonicError.userMessage(java.net.UnknownHostException())
        assertTrue(message.contains("reach"))
    }

    @Test
    fun `DecodingError preserves cause`() {
        val cause = RuntimeException("parse failed")
        val error = SubsonicError.DecodingError(cause)
        assertEquals(cause, error.cause)
        assertTrue(error.userMessage.contains("Unexpected response"))
    }
}

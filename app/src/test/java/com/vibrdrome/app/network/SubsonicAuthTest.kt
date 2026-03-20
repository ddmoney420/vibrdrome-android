package com.vibrdrome.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SubsonicAuthTest {

    @Test
    fun `auth parameters contain all required fields`() {
        val auth = SubsonicAuth("testuser", "testpass")
        val params = auth.authParameters()

        assertEquals("testuser", params["u"])
        assertEquals("1.16.1", params["v"])
        assertEquals("vibrdrome", params["c"])
        assertEquals("json", params["f"])
        assertTrue(params.containsKey("t"))
        assertTrue(params.containsKey("s"))
    }

    @Test
    fun `token is MD5 of password plus salt`() {
        val auth = SubsonicAuth("user", "mypassword")
        val params = auth.authParameters()

        val salt = params["s"]!!
        val token = params["t"]!!

        // Manually compute expected token
        val expected = md5("mypassword$salt")
        assertEquals(expected, token)
    }

    @Test
    fun `each call generates a different salt`() {
        val auth = SubsonicAuth("user", "pass")
        val salt1 = auth.authParameters()["s"]
        val salt2 = auth.authParameters()["s"]

        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `salt is 12 characters of lowercase alphanumeric`() {
        val auth = SubsonicAuth("user", "pass")
        val salt = auth.authParameters()["s"]!!

        assertEquals(12, salt.length)
        assertTrue(salt.all { it in 'a'..'z' || it in '0'..'9' })
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

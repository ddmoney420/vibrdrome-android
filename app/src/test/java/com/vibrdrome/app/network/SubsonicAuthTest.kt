package com.vibrdrome.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SubsonicAuthTest {

    @Test
    fun `default auth parameters contain all required fields`() {
        val auth = SubsonicAuth("testuser", "testpass")
        val params = auth.authParameters()

        assertEquals("testuser", params["u"])
        assertEquals("1.16.1", params["v"])
        assertEquals("vibrdrome", params["c"])
        assertEquals("json", params["f"])
        assertTrue(params.containsKey("t") || params.containsKey("p"))
    }

    @Test
    fun `password auth is hex encoded`() {
        val auth = SubsonicAuth("user", "mypassword")
        auth.useTokenAuth = false
        val params = auth.authParameters()

        val p = params["p"]!!
        assertTrue("Password should be hex-encoded with enc: prefix", p.startsWith("enc:"))
    }

    @Test
    fun `token auth parameters contain salt and token`() {
        val auth = SubsonicAuth("testuser", "testpass")
        auth.useTokenAuth = true
        val params = auth.authParameters()

        assertTrue(params.containsKey("t"))
        assertTrue(params.containsKey("s"))
        assertEquals("testuser", params["u"])
    }

    @Test
    fun `token is MD5 of password plus salt`() {
        val auth = SubsonicAuth("user", "mypassword")
        auth.useTokenAuth = true
        val params = auth.authParameters()

        val salt = params["s"]!!
        val token = params["t"]!!
        val expected = md5("mypassword$salt")
        assertEquals(expected, token)
    }

    @Test
    fun `each token call generates a different salt`() {
        val auth = SubsonicAuth("user", "pass")
        auth.useTokenAuth = true
        val salt1 = auth.authParameters()["s"]
        val salt2 = auth.authParameters()["s"]

        assertNotEquals(salt1, salt2)
    }

    @Test
    fun `salt is 12 characters of lowercase alphanumeric`() {
        val auth = SubsonicAuth("user", "pass")
        auth.useTokenAuth = true
        val salt = auth.authParameters()["s"]!!

        assertEquals(12, salt.length)
        assertTrue(salt.all { it in 'a'..'z' || it in '0'..'9' })
    }

    @Test
    fun `defaults to token auth`() {
        val auth = SubsonicAuth("user", "pass")
        assertEquals(true, auth.useTokenAuth)
        val params = auth.authParameters()
        assertTrue(params.containsKey("t"))
        assertTrue(params.containsKey("s"))
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

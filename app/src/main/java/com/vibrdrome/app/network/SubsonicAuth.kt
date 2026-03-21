package com.vibrdrome.app.network

import java.security.MessageDigest

class SubsonicAuth(
    val username: String,
    private val password: String,
) {
    val clientName = "vibrdrome"
    val apiVersion = "1.16.1"

    // Track whether token auth failed so we can fall back to password auth
    @Volatile
    var useTokenAuth: Boolean = false

    @Volatile
    var triedBothModes: Boolean = false

    private fun generateSalt(length: Int = 12): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun md5Hash(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexEncode(input: String): String {
        return input.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    fun authParameters(): Map<String, String> {
        val base = mutableMapOf(
            "u" to username,
            "v" to apiVersion,
            "c" to clientName,
            "f" to "json",
        )

        if (useTokenAuth) {
            // Standard token auth: t = MD5(password + salt), s = salt
            val salt = generateSalt()
            val token = md5Hash(password + salt)
            base["t"] = token
            base["s"] = salt
        } else {
            // Fallback: hex-encoded password
            base["p"] = "enc:${hexEncode(password)}"
        }

        return base
    }
}

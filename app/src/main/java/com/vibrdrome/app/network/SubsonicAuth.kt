package com.vibrdrome.app.network

import java.security.MessageDigest

class SubsonicAuth(
    val username: String,
    private val password: String,
) {
    val clientName = "vibrdrome"
    val apiVersion = "1.16.1"

    private fun generateSalt(length: Int = 12): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun md5Hash(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun authParameters(): Map<String, String> {
        val salt = generateSalt()
        val token = md5Hash(password + salt)
        return mapOf(
            "u" to username,
            "t" to token,
            "s" to salt,
            "v" to apiVersion,
            "c" to clientName,
            "f" to "json",
        )
    }
}

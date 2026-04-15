package com.vibrdrome.app.util

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val name: String? = null,
)

object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(this@UpdateChecker.json) }
    }

    suspend fun checkForUpdate(currentVersion: String): GitHubRelease? {
        return try {
            val release: GitHubRelease = client.get("https://api.github.com/repos/ddmoney420/vibrdrome-android/releases/latest").body()
            val latestVersion = release.tag_name.removePrefix("v")
            if (isNewer(latestVersion, currentVersion)) release else null
        } catch (_: Throwable) { null }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

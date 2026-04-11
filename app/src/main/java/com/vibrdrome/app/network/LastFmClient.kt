package com.vibrdrome.app.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LastFmArtistResponse(
    val artist: LastFmArtist? = null,
)

@Serializable
data class LastFmArtist(
    val name: String? = null,
    val bio: LastFmBio? = null,
    val similar: LastFmSimilarWrapper? = null,
    val tags: LastFmTagsWrapper? = null,
)

@Serializable
data class LastFmBio(
    val summary: String? = null,
    val content: String? = null,
)

@Serializable
data class LastFmSimilarWrapper(
    val artist: List<LastFmSimilarArtist>? = null,
)

@Serializable
data class LastFmSimilarArtist(
    val name: String? = null,
    val url: String? = null,
)

@Serializable
data class LastFmTagsWrapper(
    val tag: List<LastFmTag>? = null,
)

@Serializable
data class LastFmTag(
    val name: String? = null,
)

class LastFmClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(this@LastFmClient.json) }
    }

    suspend fun getArtistInfo(artistName: String, apiKey: String): LastFmArtistResponse? {
        return try {
            client.get("https://ws.audioscrobbler.com/2.0/") {
                parameter("method", "artist.getinfo")
                parameter("artist", artistName)
                parameter("api_key", apiKey)
                parameter("format", "json")
            }.body()
        } catch (_: Throwable) { null }
    }
}

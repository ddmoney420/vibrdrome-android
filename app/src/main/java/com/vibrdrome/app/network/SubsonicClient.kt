package com.vibrdrome.app.network

import android.net.Uri
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class SubsonicClient(
    private var baseURL: String,
    username: String,
    password: String,
    private val responseCache: ResponseCache,
    private val onRequiresReAuth: () -> Unit = {},
) {
    private var auth = SubsonicAuth(username, password)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val httpClient = HttpClient(Android) {
        engine {
            connectTimeout = 60_000
            socketTimeout = 60_000
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        private const val TAG = "SubsonicClient"
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS = longArrayOf(1000, 2000, 4000)
    }

    // MARK: - URL Building

    private fun buildUrl(endpoint: SubsonicEndpoint): String {
        val allParams = mutableListOf<Pair<String, String>>()
        auth.authParameters().forEach { (k, v) -> allParams.add(k to v) }
        endpoint.queryItems.forEach { (k, v) -> allParams.add(k to v) }
        when (endpoint) {
            is SubsonicEndpoint.CreatePlaylist -> allParams.addAll(endpoint.songIdParams)
            is SubsonicEndpoint.UpdatePlaylist -> allParams.addAll(endpoint.extraParams)
            is SubsonicEndpoint.SavePlayQueue -> allParams.addAll(endpoint.idParams)
            else -> {}
        }
        val query = allParams.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        return "$baseURL${endpoint.path}?$query"
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    fun streamURL(id: String, maxBitRate: Int? = null, format: String? = null): String {
        return buildUrl(SubsonicEndpoint.Stream(id, maxBitRate, format))
    }

    fun coverArtURL(id: String, size: Int? = null): String {
        return buildUrl(SubsonicEndpoint.GetCoverArt(id, imageSize = size))
    }

    fun downloadURL(id: String): String {
        return buildUrl(SubsonicEndpoint.Download(id))
    }

    // MARK: - Retry Logic

    private fun isRetryable(error: Throwable): Boolean {
        if (error is java.net.SocketTimeoutException) return true
        if (error is java.net.UnknownHostException) return true
        if (error is java.net.ConnectException) return true
        if (error is SubsonicError.HttpError && error.code >= 500) return true
        if (error is SubsonicError.ApiError && error.code == 40 && auth.triedBothModes) return true
        return false
    }

    // MARK: - Core Request

    private suspend fun request(endpoint: SubsonicEndpoint): SubsonicResponseBody {
        var lastError: Throwable? = null

        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val delay = RETRY_DELAYS[minOf(attempt - 1, RETRY_DELAYS.size - 1)]
                Log.i(TAG, "Retry $attempt/$MAX_RETRIES for ${endpoint.path} after ${delay}ms")
                kotlinx.coroutines.delay(delay)
            }

            try {
                return performRequest(endpoint)
            } catch (e: Throwable) {
                lastError = e
                if (!isRetryable(e)) throw e
                Log.w(TAG, "Transient error on ${endpoint.path}: ${e.message}")
            }
        }

        throw lastError!!
    }

    private suspend fun performRequest(endpoint: SubsonicEndpoint): SubsonicResponseBody {
        val url = buildUrl(endpoint)
        val response = httpClient.get(url)

        if (!response.status.isSuccess()) {
            val statusCode = response.status.value
            Log.e(TAG, "HTTP error $statusCode for ${endpoint.path}")
            if (statusCode == 401) {
                onRequiresReAuth()
            }
            throw SubsonicError.HttpError(statusCode)
        }

        val rawBytes = response.readRawBytes()
        val body = decodeResponse(rawBytes, endpoint.path)

        // Cache the raw response data on success
        val key = responseCache.cacheKey(endpoint)
        responseCache.store(rawBytes, key)

        return body
    }

    private fun decodeResponse(data: ByteArray, path: String = ""): SubsonicResponseBody {
        val text = data.toString(Charsets.UTF_8)
        val decoded: SubsonicResponse
        try {
            decoded = json.decodeFromString<SubsonicResponse>(text)
        } catch (e: Exception) {
            throw SubsonicError.DecodingError(e)
        }

        val body = decoded.subsonicResponse

        if (body.status != "ok") {
            val error = body.error
            if (error != null) {
                Log.e(TAG, "API error ${error.code}: ${error.message} for $path")
                if (error.code == 40) {
                    if (!auth.triedBothModes) {
                        // Try the other auth mode
                        Log.i(TAG, "Auth failed, switching mode (token=${auth.useTokenAuth})")
                        auth.useTokenAuth = !auth.useTokenAuth
                        auth.triedBothModes = true
                        throw SubsonicError.ApiError(error.code, error.message)
                    }
                    auth.triedBothModes = false
                    onRequiresReAuth()
                }
                throw SubsonicError.ApiError(error.code, error.message)
            }
            Log.e(TAG, "Non-ok status: ${body.status} for $path")
            throw SubsonicError.ApiError(0, "Server returned status: ${body.status}")
        }

        return body
    }

    suspend fun cachedResponse(endpoint: SubsonicEndpoint, ttlMs: Long): SubsonicResponseBody? {
        val key = responseCache.cacheKey(endpoint)
        val data = responseCache.data(key, ttlMs) ?: return null
        return try {
            decodeResponse(data)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun invalidateCache(endpoint: SubsonicEndpoint) {
        val key = responseCache.cacheKey(endpoint)
        responseCache.remove(key)
    }

    suspend fun clearCache() {
        responseCache.clearAll()
    }

    private suspend fun performAction(endpoint: SubsonicEndpoint) {
        request(endpoint)
    }

    // MARK: - Convenience Methods

    suspend fun ping(): Boolean {
        return try {
            val body = request(SubsonicEndpoint.Ping)
            val connected = body.status == "ok"
            _isConnected.value = connected
            connected
        } catch (e: Throwable) {
            _isConnected.value = false
            throw e
        }
    }

    suspend fun getArtists(musicFolderId: String? = null): List<ArtistIndex> {
        val body = request(SubsonicEndpoint.GetArtists(musicFolderId))
        return body.artists?.index ?: emptyList()
    }

    suspend fun getArtist(id: String): Artist {
        val body = request(SubsonicEndpoint.GetArtist(id))
        return body.artist ?: throw SubsonicError.ApiError(70, "Artist not found")
    }

    suspend fun getAlbum(id: String): Album {
        val body = request(SubsonicEndpoint.GetAlbum(id))
        return body.album ?: throw SubsonicError.ApiError(70, "Album not found")
    }

    suspend fun getSong(id: String): Song {
        val body = request(SubsonicEndpoint.GetSong(id))
        return body.song ?: throw SubsonicError.ApiError(70, "Song not found")
    }

    suspend fun search(
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 40,
        musicFolderId: String? = null,
    ): SearchResult3 {
        val body = request(SubsonicEndpoint.Search3(query, artistCount, albumCount, songCount, musicFolderId = musicFolderId))
        return body.searchResult3 ?: SearchResult3()
    }

    suspend fun getAlbumList(
        type: AlbumListType,
        size: Int = 20,
        offset: Int = 0,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
        musicFolderId: String? = null,
    ): List<Album> {
        val body = request(SubsonicEndpoint.GetAlbumList2(type, pageSize = size, offset = offset, fromYear = fromYear, toYear = toYear, genre = genre, musicFolderId = musicFolderId))
        return body.albumList2?.album ?: emptyList()
    }

    suspend fun getRandomSongs(size: Int = 20, genre: String? = null, musicFolderId: String? = null): List<Song> {
        val body = request(SubsonicEndpoint.GetRandomSongs(pageSize = size, genre = genre, musicFolderId = musicFolderId))
        return body.randomSongs?.song ?: emptyList()
    }

    suspend fun getStarred(musicFolderId: String? = null): Starred2 {
        val body = request(SubsonicEndpoint.GetStarred2(musicFolderId))
        return body.starred2 ?: Starred2()
    }

    suspend fun getGenres(): List<Genre> {
        val body = request(SubsonicEndpoint.GetGenres)
        return body.genres?.genre ?: emptyList()
    }

    suspend fun star(id: String? = null, albumId: String? = null, artistId: String? = null) {
        performAction(SubsonicEndpoint.Star(id, albumId, artistId))
        invalidateCache(SubsonicEndpoint.GetStarred2())
    }

    suspend fun unstar(id: String? = null, albumId: String? = null, artistId: String? = null) {
        performAction(SubsonicEndpoint.Unstar(id, albumId, artistId))
        invalidateCache(SubsonicEndpoint.GetStarred2())
    }

    suspend fun setRating(id: String, rating: Int) {
        performAction(SubsonicEndpoint.SetRating(id, rating))
    }

    suspend fun scrobble(id: String, submission: Boolean = true) {
        performAction(SubsonicEndpoint.Scrobble(id, submission = submission))
    }

    suspend fun getPlaylists(): List<Playlist> {
        val body = request(SubsonicEndpoint.GetPlaylists)
        return body.playlists?.playlist ?: emptyList()
    }

    suspend fun getPlaylist(id: String): Playlist {
        val body = request(SubsonicEndpoint.GetPlaylist(id))
        return body.playlist ?: throw SubsonicError.ApiError(70, "Playlist not found")
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()) {
        performAction(SubsonicEndpoint.CreatePlaylist(name, songIds))
        invalidateCache(SubsonicEndpoint.GetPlaylists)
    }

    suspend fun updatePlaylist(
        id: String,
        name: String? = null,
        comment: String? = null,
        isPublic: Boolean? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList(),
    ) {
        performAction(SubsonicEndpoint.UpdatePlaylist(id, name, comment, isPublic, songIdsToAdd, songIndexesToRemove))
    }

    suspend fun deletePlaylist(id: String) {
        performAction(SubsonicEndpoint.DeletePlaylist(id))
        invalidateCache(SubsonicEndpoint.GetPlaylists)
    }

    suspend fun getLyrics(songId: String): LyricsList? {
        val body = request(SubsonicEndpoint.GetLyricsBySongId(songId))
        return body.lyricsList
    }

    suspend fun getRadioStations(): List<InternetRadioStation> {
        val body = request(SubsonicEndpoint.GetInternetRadioStations)
        return body.internetRadioStations?.internetRadioStation ?: emptyList()
    }

    suspend fun createRadioStation(streamUrl: String, name: String, homepageUrl: String? = null) {
        performAction(SubsonicEndpoint.CreateInternetRadioStation(streamUrl, name, homepageUrl))
    }

    suspend fun deleteRadioStation(id: String) {
        performAction(SubsonicEndpoint.DeleteInternetRadioStation(id))
    }

    suspend fun getPlayQueue(): PlayQueue? {
        val body = request(SubsonicEndpoint.GetPlayQueue)
        return body.playQueue
    }

    suspend fun savePlayQueue(ids: List<String>, current: String? = null, position: Int? = null) {
        performAction(SubsonicEndpoint.SavePlayQueue(ids, current, position))
    }

    suspend fun getBookmarks(): List<Bookmark> {
        val body = request(SubsonicEndpoint.GetBookmarks)
        return body.bookmarks?.bookmark ?: emptyList()
    }

    suspend fun createBookmark(id: String, position: Int, comment: String? = null) {
        performAction(SubsonicEndpoint.CreateBookmark(id, position, comment))
    }

    suspend fun deleteBookmark(id: String) {
        performAction(SubsonicEndpoint.DeleteBookmark(id))
    }

    suspend fun getSimilarSongs(id: String, count: Int = 50): List<Song> {
        val body = request(SubsonicEndpoint.GetSimilarSongs2(id, count))
        return body.similarSongs2?.song ?: emptyList()
    }

    suspend fun getTopSongs(artist: String, count: Int = 50): List<Song> {
        val body = request(SubsonicEndpoint.GetTopSongs(artist, count))
        return body.topSongs?.song ?: emptyList()
    }

    suspend fun getMusicFolders(): List<MusicFolder> {
        val body = request(SubsonicEndpoint.GetMusicFolders)
        return body.musicFolders?.musicFolder ?: emptyList()
    }

    suspend fun getIndexes(musicFolderId: String? = null): IndexesResponse {
        val body = request(SubsonicEndpoint.GetIndexes(musicFolderId))
        return body.indexes ?: throw SubsonicError.ApiError(70, "Indexes not found")
    }

    suspend fun getMusicDirectory(id: String): MusicDirectory {
        val body = request(SubsonicEndpoint.GetMusicDirectory(id))
        return body.directory ?: throw SubsonicError.ApiError(70, "Directory not found")
    }

    // MARK: - Reconfigure

    fun updateCredentials(baseURL: String, username: String, password: String) {
        this.baseURL = baseURL
        this.auth = SubsonicAuth(username, password)
        _isConnected.value = false
    }
}

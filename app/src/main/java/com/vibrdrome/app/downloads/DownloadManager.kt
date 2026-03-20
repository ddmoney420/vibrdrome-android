package com.vibrdrome.app.downloads

import android.content.Context
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.persistence.DownloadDao
import com.vibrdrome.app.persistence.DownloadedSong
import com.vibrdrome.app.ui.AppState
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class DownloadManager(
    context: Context,
    private val appState: AppState,
    private val dao: DownloadDao,
) {
    private val downloadDir = File(context.filesDir, "downloads").also { it.mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = HttpClient(Android) {
        engine {
            connectTimeout = 30_000
            socketTimeout = 120_000
        }
    }

    private val _activeDownloads = MutableStateFlow<Map<String, Float>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, Float>> = _activeDownloads.asStateFlow()

    val downloadedSongs: Flow<List<DownloadedSong>> = dao.getAll()

    fun downloadSong(song: Song) {
        if (_activeDownloads.value.containsKey(song.id)) return

        scope.launch {
            _activeDownloads.value = _activeDownloads.value + (song.id to 0f)
            val file = File(downloadDir, "${song.id}.${song.suffix ?: "mp3"}")
            try {
                val downloadUrl = appState.subsonicClient.downloadURL(song.id)

                httpClient.prepareGet(downloadUrl).execute { response ->
                    val totalSize = response.contentLength() ?: -1L
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(8192)

                    file.outputStream().use { output ->
                        var bytesRead = 0L
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer)
                            if (read <= 0) continue
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalSize > 0) {
                                _activeDownloads.value = _activeDownloads.value +
                                    (song.id to (bytesRead.toFloat() / totalSize))
                            }
                        }
                    }
                }

                dao.insert(
                    DownloadedSong(
                        songId = song.id,
                        filePath = file.absolutePath,
                        fileSize = file.length(),
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        coverArt = song.coverArt,
                        downloadedAt = System.currentTimeMillis(),
                    )
                )
            } catch (e: Exception) {
                file.delete()
                appState.showError("Download failed: ${e.message ?: "Unknown error"}")
            }
            _activeDownloads.value = _activeDownloads.value - song.id
        }
    }

    suspend fun isDownloaded(songId: String): Boolean = dao.getBySongId(songId) != null

    suspend fun getLocalPath(songId: String): String? = dao.getBySongId(songId)?.filePath

    suspend fun deleteDownload(songId: String) {
        val download = dao.getBySongId(songId) ?: return
        File(download.filePath).delete()
        dao.delete(songId)
    }

    suspend fun totalSize(): Long = dao.totalSize() ?: 0
}

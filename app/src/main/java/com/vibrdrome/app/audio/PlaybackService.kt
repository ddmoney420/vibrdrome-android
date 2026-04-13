package com.vibrdrome.app.audio

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.vibrdrome.app.network.AlbumListType
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.ui.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val playbackManager: PlaybackManager by inject()
    private val appState: AppState by inject()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        session = MediaLibrarySession.Builder(this, playbackManager.player, BrowseCallback())
            .build()

        // Swap the MediaSession player when Cast state changes
        playbackManager.onPlayerSwapped = { newPlayer ->
            session?.player = newPlayer
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player != null && player.playWhenReady) {
            // Keep playing in background — don't stop the service
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class BrowseCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = buildBrowsable(ROOT_ID, "Vibrdrome", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch {
                try {
                    val items = fetchChildren(parentId)
                    future.set(LibraryResult.ofItemList(items, params))
                } catch (_: Throwable) {
                    future.set(LibraryResult.ofItemList(emptyList(), params))
                }
            }
            return future
        }

        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            // Resolve browse-tree items: ensure each has a stream URI
            val client = appState.subsonicClient
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) {
                    item
                } else {
                    val songId = item.mediaId
                    MediaItem.Builder()
                        .setMediaId(songId)
                        .setUri(client.streamURL(songId))
                        .setMediaMetadata(item.mediaMetadata)
                        .build()
                }
            }
            return Futures.immediateFuture(resolved)
        }

        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            // Resolve URIs for all items
            val client = appState.subsonicClient
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) item
                else MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(client.streamURL(item.mediaId))
                    .setMediaMetadata(item.mediaMetadata)
                    .build()
            }
            return Futures.immediateFuture(
                MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
            )
        }
    }

    private suspend fun fetchChildren(parentId: String): List<MediaItem> {
        val client = appState.subsonicClient
        return when {
            parentId == ROOT_ID -> listOf(
                buildBrowsable("recent", "Recently Added", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                buildBrowsable("artists", "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                buildBrowsable("albums", "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                buildBrowsable("playlists", "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                buildBrowsable("radio", "Radio", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            )
            parentId == "artists" -> {
                client.getArtists().flatMap { index ->
                    (index.artist ?: emptyList()).map { artist ->
                        buildBrowsable(
                            "artist:${artist.id}", artist.name,
                            MediaMetadata.MEDIA_TYPE_ARTIST,
                        )
                    }
                }
            }
            parentId.startsWith("artist:") -> {
                val artist = client.getArtist(parentId.removePrefix("artist:"))
                (artist.album ?: emptyList()).map { album ->
                    buildBrowsable(
                        "album:${album.id}", album.name,
                        MediaMetadata.MEDIA_TYPE_ALBUM, album.artist,
                    )
                }
            }
            parentId.startsWith("album:") -> {
                val album = client.getAlbum(parentId.removePrefix("album:"))
                (album.song ?: emptyList()).map { buildPlayable(it, client) }
            }
            parentId == "playlists" -> {
                client.getPlaylists().map { playlist ->
                    buildBrowsable(
                        "playlist:${playlist.id}", playlist.name,
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    )
                }
            }
            parentId.startsWith("playlist:") -> {
                val playlist = client.getPlaylist(parentId.removePrefix("playlist:"))
                (playlist.entry ?: emptyList()).map { buildPlayable(it, client) }
            }
            parentId == "recent" -> {
                client.getAlbumList(AlbumListType.NEWEST, size = 20).map { album ->
                    buildBrowsable(
                        "album:${album.id}", album.name,
                        MediaMetadata.MEDIA_TYPE_ALBUM, album.artist,
                    )
                }
            }
            parentId == "albums" -> {
                client.getAlbumList(AlbumListType.ALPHABETICAL_BY_NAME, size = 50).map { album ->
                    buildBrowsable(
                        "album:${album.id}", album.name,
                        MediaMetadata.MEDIA_TYPE_ALBUM, album.artist,
                    )
                }
            }
            parentId == "radio" -> {
                client.getRadioStations().map { station ->
                    val artUri = station.coverArt?.let {
                        Uri.parse(client.coverArtURL("ra-${station.id}", size = 480))
                    }
                    MediaItem.Builder()
                        .setMediaId("radio:${station.id}")
                        .setUri(station.streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(station.name)
                                .setArtworkUri(artUri)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                                .build()
                        )
                        .build()
                }
            }
            else -> emptyList()
        }
    }

    private fun buildBrowsable(
        id: String, title: String, mediaType: Int, subtitle: String? = null,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .build()
        )
        .build()

    private fun buildPlayable(song: Song, client: SubsonicClient): MediaItem {
        val artUri = song.coverArt?.let { Uri.parse(client.coverArtURL(it, size = 480)) }
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(client.streamURL(song.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(artUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    companion object {
        private const val ROOT_ID = "root"
    }
}

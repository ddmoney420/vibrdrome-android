package com.vibrdrome.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object ArtistsRoute

@Serializable
data class ArtistDetailRoute(val artistId: String)

@Serializable
data object AlbumsRoute

@Serializable
data class AlbumDetailRoute(val albumId: String)

@Serializable
data class AlbumsListRoute(
    val listType: String,
    val title: String,
    val genre: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
)

@Serializable
data object ServerConfigRoute

@Serializable
data object NowPlayingRoute

@Serializable
data object SearchRoute

@Serializable
data object QueueRoute

@Serializable
data object GenresRoute

@Serializable
data object SongsRoute

@Serializable
data object GenerationsRoute

@Serializable
data object FavoritesRoute

@Serializable
data object EQRoute

@Serializable
data object PlaylistsRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: String)

@Serializable
data object LyricsRoute

@Serializable
data object RadioRoute

@Serializable
data object SettingsRoute

@Serializable
data object ServerManagerRoute

@Serializable
data object FolderBrowserRoute

@Serializable
data class FolderDetailRoute(val directoryId: String, val title: String? = null)

@Serializable
data object StationSearchRoute

@Serializable
data object AddStationRoute

@Serializable
data object VisualizerRoute

@Serializable
data object DownloadsRoute

@Serializable
data class PlaylistEditorRoute(val playlistId: String? = null)

@Serializable
data object SmartPlaylistRoute

@Serializable
data object StatsRoute

@Serializable
data object BookmarksRoute

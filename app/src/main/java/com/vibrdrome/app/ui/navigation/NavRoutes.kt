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

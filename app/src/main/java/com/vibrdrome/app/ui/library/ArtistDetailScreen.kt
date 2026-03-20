package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.network.Artist
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.network.SubsonicError
import com.vibrdrome.app.ui.components.AlbumCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    client: SubsonicClient,
    onAlbumClick: (albumId: String) -> Unit,
) {
    var artist by remember { mutableStateOf<Artist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(artistId) {
        isLoading = true
        error = null
        try {
            artist = client.getArtist(artistId)
        } catch (e: Throwable) {
            error = SubsonicError.userMessage(e)
        }
        isLoading = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(artist?.name ?: "Artist") }) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading && artist == null -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                error != null && artist == null -> {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )
                }
                else -> {
                    val albums = artist?.album ?: emptyList()
                    LazyColumn {
                        items(albums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                coverArtUrl = album.coverArt?.let { client.coverArtURL(it, size = 112) },
                                modifier = Modifier
                                    .clickable { onAlbumClick(album.id) }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

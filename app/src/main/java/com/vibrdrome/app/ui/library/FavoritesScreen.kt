package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.Starred2
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.components.AlbumCard
import com.vibrdrome.app.ui.components.TrackListItem
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    client: SubsonicClient,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val playbackManager: PlaybackManager = koinInject()
    val appState: AppState = koinInject()
    val folderId by appState.selectedFolderId.collectAsState()
    var starred by remember { mutableStateOf<Starred2?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Artists", "Albums", "Songs")

    LaunchedEffect(folderId) {
        try {
            starred = client.getStarred(folderId)
        } catch (_: Throwable) {}
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                CircularProgressIndicator()
            }
        } else {
            val s = starred

            LazyColumn(modifier = Modifier.padding(padding)) {
                item {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) },
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        val artists = s?.artist ?: emptyList()
                        if (artists.isEmpty()) {
                            item {
                                EmptyState("No favorite artists yet")
                            }
                        } else {
                            items(artists, key = { it.id }) { artist ->
                                ListItem(
                                    headlineContent = { Text(artist.name) },
                                    supportingContent = {
                                        artist.albumCount?.let { Text("$it albums") }
                                    },
                                    leadingContent = {
                                        AlbumArtView(
                                            coverArtUrl = artist.coverArt?.let {
                                                client.coverArtURL(it, size = 88)
                                            },
                                            size = 44.dp,
                                            cornerRadius = 22.dp,
                                        )
                                    },
                                    modifier = Modifier.clickable { onArtistClick(artist.id) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                    1 -> {
                        val albums = s?.album ?: emptyList()
                        if (albums.isEmpty()) {
                            item {
                                EmptyState("No favorite albums yet")
                            }
                        } else {
                            items(albums, key = { it.id }) { album ->
                                AlbumCard(
                                    album = album,
                                    coverArtUrl = album.coverArt?.let {
                                        client.coverArtURL(it, size = 112)
                                    },
                                    modifier = Modifier
                                        .clickable { onAlbumClick(album.id) }
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    2 -> {
                        val songs = s?.song ?: emptyList()
                        if (songs.isEmpty()) {
                            item {
                                EmptyState("No favorite songs yet")
                            }
                        } else {
                            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                                TrackListItem(
                                    song = song,
                                    showTrackNumber = false,
                                    onClick = { playbackManager.play(songs, index) },
                                    onGoToAlbum = { onAlbumClick(it) },
                                    onGoToArtist = { onArtistClick(it) },
                                )
                                HorizontalDivider(Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(64.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

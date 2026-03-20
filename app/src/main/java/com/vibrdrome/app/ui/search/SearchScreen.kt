package com.vibrdrome.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.Artist
import com.vibrdrome.app.network.SearchResult3
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.components.AlbumCard
import com.vibrdrome.app.ui.components.TrackRow
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    client: SubsonicClient,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val playbackManager: PlaybackManager = koinInject()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResult3?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = null
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        try {
            results = client.search(query)
        } catch (_: Throwable) { }
        isSearching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search artists, albums, songs...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isSearching && results == null -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                results != null -> {
                    val r = results!!
                    val artists = r.artist ?: emptyList()
                    val albums = r.album ?: emptyList()
                    val songs = r.song ?: emptyList()

                    if (artists.isEmpty() && albums.isEmpty() && songs.isEmpty()) {
                        Text(
                            text = "No results found",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn {
                            if (artists.isNotEmpty()) {
                                item(key = "header_artists") {
                                    SectionHeader("Artists")
                                }
                                items(artists.take(5), key = { "artist_${it.id}" }) { artist ->
                                    ArtistSearchRow(
                                        artist = artist,
                                        coverArtUrl = artist.coverArt?.let {
                                            client.coverArtURL(it, size = 88)
                                        },
                                        onClick = { onArtistClick(artist.id) },
                                    )
                                }
                            }

                            if (albums.isNotEmpty()) {
                                item(key = "header_albums") {
                                    SectionHeader("Albums")
                                }
                                items(albums.take(5), key = { "album_${it.id}" }) { album ->
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

                            if (songs.isNotEmpty()) {
                                item(key = "header_songs") {
                                    SectionHeader("Songs")
                                }
                                items(songs, key = { "song_${it.id}" }) { song ->
                                    TrackRow(
                                        song = song,
                                        showTrackNumber = false,
                                        modifier = Modifier.clickable {
                                            val index = songs.indexOf(song)
                                            playbackManager.play(songs, index)
                                        },
                                    )
                                }
                            }

                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
                query.isEmpty() -> {
                    Text(
                        text = "Search your music library",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun ArtistSearchRow(
    artist: Artist,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AlbumArtView(coverArtUrl = coverArtUrl, size = 44.dp, cornerRadius = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            artist.albumCount?.let { count ->
                Text(
                    text = "$count album${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

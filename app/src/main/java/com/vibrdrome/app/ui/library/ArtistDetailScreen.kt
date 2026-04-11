package com.vibrdrome.app.ui.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.RadioManager
import com.vibrdrome.app.network.Artist
import com.vibrdrome.app.network.ArtistInfo2
import com.vibrdrome.app.network.LastFmArtistResponse
import com.vibrdrome.app.network.LastFmClient
import com.vibrdrome.app.network.SimilarArtistRef
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.network.SubsonicError
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.components.AlbumCard
import com.vibrdrome.app.ui.components.TrackListItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    client: SubsonicClient,
    onAlbumClick: (albumId: String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToArtist: (artistId: String) -> Unit = {},
) {
    val playbackManager: PlaybackManager = koinInject()
    val radioManager: RadioManager = koinInject()
    val lastFmClient: LastFmClient = koinInject()
    val appState: AppState = koinInject()
    val scope = rememberCoroutineScope()

    var artist by remember { mutableStateOf<Artist?>(null) }
    var artistInfo2 by remember { mutableStateOf<ArtistInfo2?>(null) }
    var lastFmData by remember { mutableStateOf<LastFmArtistResponse?>(null) }
    var topSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var bioExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(artistId) {
        isLoading = true
        error = null
        try {
            artist = client.getArtist(artistId)
            artistInfo2 = try { client.getArtistInfo2(artistId) } catch (_: Throwable) { null }

            val artistName = artist?.name
            if (artistName != null) {
                topSongs = try { client.getTopSongs(artistName, count = 10) } catch (_: Throwable) { emptyList() }

                val apiKey = appState.lastFmApiKey
                if (!apiKey.isNullOrEmpty()) {
                    lastFmData = lastFmClient.getArtistInfo(artistName, apiKey)
                }
            }
        } catch (e: Throwable) {
            error = SubsonicError.userMessage(e)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(artist?.name ?: "Artist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    artist?.let { a ->
                        IconButton(onClick = {
                            scope.launch {
                                radioManager.startArtistRadio(a.id, a.name, client, playbackManager)
                            }
                        }) {
                            Icon(Icons.Default.Radio, contentDescription = "Start Radio")
                        }
                    }
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
                    val similarArtists = artistInfo2?.similarArtist ?: emptyList()
                    val bio = artistInfo2?.biography
                        ?: lastFmData?.artist?.bio?.summary?.replace(Regex("<[^>]*>"), "")
                    val displayTopSongs = topSongs.take(5)

                    LazyColumn {
                        // Artist header with image
                        item(key = "header") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                val imageUrl = artistInfo2?.largeImageUrl
                                    ?: artistInfo2?.mediumImageUrl
                                    ?: artist?.coverArt?.let { client.coverArtURL(it, size = 300) }
                                AlbumArtView(
                                    coverArtUrl = imageUrl,
                                    size = 160.dp,
                                    cornerRadius = 80.dp,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = artist?.name ?: "",
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                val albumCount = artist?.albumCount
                                if (albumCount != null && albumCount > 0) {
                                    Text(
                                        text = "$albumCount albums",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Bio section
                        if (!bio.isNullOrBlank()) {
                            item(key = "bio") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "About",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                    Text(
                                        text = bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = if (bioExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.animateContentSize(),
                                    )
                                    Text(
                                        text = if (bioExpanded) "Show less" else "Read more",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { bioExpanded = !bioExpanded }
                                            .padding(top = 4.dp),
                                    )
                                }
                            }
                        }

                        // Top Songs
                        if (displayTopSongs.isNotEmpty()) {
                            item(key = "top_songs_header") {
                                Text(
                                    text = "Top Songs",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(displayTopSongs, key = { "top_${it.id}" }) { song ->
                                TrackListItem(
                                    song = song,
                                    onClick = {
                                        playbackManager.play(topSongs, startIndex = topSongs.indexOf(song).coerceAtLeast(0))
                                    },
                                    showTrackNumber = false,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    onGoToAlbum = { albumId -> onAlbumClick(albumId) },
                                    onGoToArtist = null,
                                )
                            }
                        }

                        // Similar Artists
                        if (similarArtists.isNotEmpty()) {
                            item(key = "similar_header") {
                                Text(
                                    text = "Similar Artists",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            item(key = "similar_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(similarArtists, key = { it.id }) { similar ->
                                        SimilarArtistItem(
                                            artist = similar,
                                            coverArtUrl = similar.coverArt?.let { client.coverArtURL(it, size = 112) },
                                            onClick = { onNavigateToArtist(similar.id) },
                                        )
                                    }
                                }
                            }
                        }

                        // Albums
                        if (albums.isNotEmpty()) {
                            item(key = "albums_header") {
                                Text(
                                    text = "Albums",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
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

                        item(key = "bottom_spacer") {
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarArtistItem(
    artist: SimilarArtistRef,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
    ) {
        AlbumArtView(
            coverArtUrl = coverArtUrl,
            size = 64.dp,
            cornerRadius = 32.dp,
            modifier = Modifier.clip(CircleShape),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

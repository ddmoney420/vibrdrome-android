package com.vibrdrome.app.ui.playlists

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.Playlist
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.network.SubsonicError
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.components.TrackListItem
import com.vibrdrome.app.util.formatDuration
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    client: SubsonicClient,
    onEditPlaylist: ((String) -> Unit)? = null,
    onNavigateBack: () -> Unit = {},
) {
    val playbackManager: PlaybackManager = koinInject()
    var playlist by remember { mutableStateOf<Playlist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        isLoading = true
        try {
            playlist = client.getPlaylist(playlistId)
        } catch (e: Throwable) {
            error = SubsonicError.userMessage(e)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    onEditPlaylist?.let { edit ->
                        IconButton(onClick = { edit(playlistId) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text(error ?: "", Modifier.align(Alignment.Center).padding(16.dp))
                playlist != null -> {
                    val songs = playlist!!.entry ?: emptyList()

                    LazyColumn {
                        // Header
                        item(key = "header") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                            ) {
                                AlbumArtView(
                                    coverArtUrl = playlist!!.coverArt?.let { client.coverArtURL(it, size = 480) },
                                    size = 200.dp,
                                    cornerRadius = 12.dp,
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = playlist!!.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                val meta = buildList {
                                    playlist!!.songCount?.let { add("$it songs") }
                                    playlist!!.duration?.let { add(formatDuration(it)) }
                                }
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta.joinToString(" \u00b7 "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Actions
                        item(key = "actions") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Button(
                                    onClick = { playbackManager.play(songs) },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play")
                                }
                                OutlinedButton(
                                    onClick = { playbackManager.playShuffle(songs) },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Shuffle")
                                }
                            }
                        }

                        // Tracks
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            TrackListItem(
                                song = song,
                                showTrackNumber = false,
                                onClick = { playbackManager.play(songs, index) },
                            )
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

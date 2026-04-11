package com.vibrdrome.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.downloads.DownloadManager
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.ui.AppState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun BatchSelectionBar(
    selectedSongs: List<Song>,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackManager: PlaybackManager = koinInject()
    val downloadManager: DownloadManager = koinInject()
    val appState: AppState = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPlaylistDialog by remember { mutableStateOf(false) }

    BottomAppBar(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${selectedSongs.size} selected",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 8.dp),
            )

            IconButton(onClick = {
                selectedSongs.forEach { playbackManager.addToQueue(it) }
                Toast.makeText(context, "Added ${selectedSongs.size} to queue", Toast.LENGTH_SHORT).show()
                onClearSelection()
            }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Add to Queue")
            }

            IconButton(onClick = { showPlaylistDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist")
            }

            IconButton(onClick = {
                scope.launch {
                    try {
                        val client = appState.subsonicClient
                        selectedSongs.forEach { client.star(id = it.id) }
                        Toast.makeText(context, "Starred ${selectedSongs.size} songs", Toast.LENGTH_SHORT).show()
                    } catch (_: Throwable) {
                        Toast.makeText(context, "Failed to star", Toast.LENGTH_SHORT).show()
                    }
                }
                onClearSelection()
            }) {
                Icon(Icons.Default.Favorite, contentDescription = "Star All")
            }

            IconButton(onClick = {
                downloadManager.downloadBatch(selectedSongs, "${selectedSongs.size} songs")
                Toast.makeText(context, "Downloading ${selectedSongs.size} songs", Toast.LENGTH_SHORT).show()
                onClearSelection()
            }) {
                Icon(Icons.Default.Download, contentDescription = "Download All")
            }
        }
    }

    if (showPlaylistDialog && selectedSongs.isNotEmpty()) {
        AddToPlaylistDialog(
            songId = selectedSongs.first().id,
            client = appState.subsonicClient,
            onDismiss = { showPlaylistDialog = false },
            onAdded = { name ->
                // Add remaining songs to same playlist
                scope.launch {
                    try {
                        val playlists = appState.subsonicClient.getPlaylists()
                        val playlist = playlists.find { it.name == name }
                        if (playlist != null && selectedSongs.size > 1) {
                            appState.subsonicClient.updatePlaylist(
                                id = playlist.id,
                                songIdsToAdd = selectedSongs.drop(1).map { it.id },
                            )
                        }
                    } catch (_: Throwable) {}
                }
                Toast.makeText(context, "Added ${selectedSongs.size} to $name", Toast.LENGTH_SHORT).show()
                onClearSelection()
            },
        )
    }
}

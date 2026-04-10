package com.vibrdrome.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.RadioManager
import com.vibrdrome.app.downloads.DownloadManager
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.util.formatDuration
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TrackListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = true,
    playbackManager: PlaybackManager = koinInject(),
    appState: AppState = koinInject(),
    downloadManager: DownloadManager = koinInject(),
    radioManager: RadioManager = koinInject(),
    onGoToAlbum: ((String) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var isStarred by remember(song.id) { mutableStateOf(song.starred != null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        if (showTrackNumber && song.track != null) {
            Text(
                text = "${song.track}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                song.artist?.let { artist ->
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                song.duration?.let { duration ->
                    Text(
                        text = " \u00b7 ${formatDuration(duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (isStarred) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Favorited",
                tint = Color(0xFFFF69B4),
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    onClick = {
                        showMenu = false
                        playbackManager.playNext(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    onClick = {
                        showMenu = false
                        playbackManager.addToQueue(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        showMenu = false
                        showPlaylistDialog = true
                    },
                )
                if (onGoToAlbum != null && song.albumId != null) {
                    DropdownMenuItem(
                        text = { Text("Go to Album") },
                        onClick = {
                            showMenu = false
                            onGoToAlbum(song.albumId!!)
                        },
                    )
                }
                if (onGoToArtist != null && song.artistId != null) {
                    DropdownMenuItem(
                        text = { Text("Go to Artist") },
                        onClick = {
                            showMenu = false
                            onGoToArtist(song.artistId!!)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Song Radio") },
                    onClick = {
                        showMenu = false
                        scope.launch {
                            radioManager.startSongRadio(song.id, song.title, appState.subsonicClient, playbackManager)
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = {
                        showMenu = false
                        downloadManager.downloadSong(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isStarred) "Unstar" else "Star") },
                    leadingIcon = {
                        Icon(
                            if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isStarred) Color(0xFFFF69B4)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {
                        showMenu = false
                        val wasStarred = isStarred
                        isStarred = !wasStarred
                        scope.launch {
                            try {
                                val client = appState.subsonicClient
                                if (wasStarred) client.unstar(id = song.id)
                                else client.star(id = song.id)
                            } catch (_: Throwable) {
                                isStarred = wasStarred
                            }
                        }
                    },
                )
            }
        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            songId = song.id,
            client = appState.subsonicClient,
            onDismiss = { showPlaylistDialog = false },
            onAdded = { name ->
                Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

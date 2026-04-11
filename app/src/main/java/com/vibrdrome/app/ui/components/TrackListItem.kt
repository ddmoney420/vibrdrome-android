package com.vibrdrome.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var currentRating by remember(song.id) { mutableStateOf(song.userRating ?: 0) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    val currentPlayingSongId = playbackManager.currentSong.collectAsState().value?.id
    val isCurrentlyPlaying = playbackManager.isPlaying.collectAsState().value

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    playbackManager.playNext(song)
                    Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                    false // Reset swipe position
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    playbackManager.addToQueue(song)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    false // Reset swipe position
                }
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val direction = swipeState.dismissDirection
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                },
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Play Next", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(4.dp))
                            Text("Play Next", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Add to Queue", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Add to Queue", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    else -> {}
                }
            }
        },
    ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        if (showTrackNumber && song.track != null) {
            if (song.id == currentPlayingSongId && isCurrentlyPlaying) {
                EqualizerBars(modifier = Modifier.width(28.dp))
            } else {
                Text(
                    text = "${song.track}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
            }
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

        DownloadBadge(
            songId = song.id,
            downloadManager = downloadManager,
            modifier = Modifier.padding(end = 4.dp),
        )

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
                // Rating row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = {
                            showMenu = false
                            val newRating = if (currentRating == star) 0 else star
                            val oldRating = currentRating
                            currentRating = newRating
                            scope.launch {
                                try {
                                    appState.subsonicClient.setRating(song.id, newRating)
                                } catch (_: Throwable) {
                                    currentRating = oldRating
                                }
                            }
                        }) {
                            Icon(
                                if (star <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Rate $star",
                                tint = if (star <= currentRating) Color(0xFFFFB300)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
    } // SwipeToDismissBox

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

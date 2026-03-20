package com.vibrdrome.app.ui.library

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.DirectoryChild
import com.vibrdrome.app.network.MusicDirectory
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.util.formatDuration
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    directoryId: String,
    title: String?,
    client: SubsonicClient,
    onFolderClick: (id: String, name: String) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val playbackManager: PlaybackManager = koinInject()
    var directory by remember { mutableStateOf<MusicDirectory?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(directoryId) {
        try {
            directory = client.getMusicDirectory(directoryId)
        } catch (_: Throwable) {}
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: directory?.name ?: "Folder") },
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
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                CircularProgressIndicator()
            }
        } else {
            val children = directory?.child ?: emptyList()
            val folders = children.filter { it.isDir }
            val songs = children.filter { !it.isDir }
            val songObjects = songs.map { it.toSong() }

            LazyColumn(modifier = Modifier.padding(padding)) {
                // Subfolders
                itemsIndexed(folders, key = { _, c -> "dir_${c.id}" }) { _, child ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFolderClick(child.id, child.title ?: "Folder") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = child.title ?: child.id,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider()
                }

                // Songs
                itemsIndexed(songs, key = { _, c -> "song_${c.id}" }) { index, child ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playbackManager.play(songObjects, index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = child.title ?: "Unknown",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = buildList {
                                child.artist?.let { add(it) }
                                child.duration?.let { add(formatDuration(it)) }
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
                    HorizontalDivider()
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

private fun DirectoryChild.toSong() = Song(
    id = id,
    title = title ?: "Unknown",
    artist = artist,
    album = album,
    coverArt = coverArt,
    duration = duration,
    track = track,
    year = year,
    genre = genre,
    size = size,
    suffix = suffix,
    bitRate = bitRate,
    contentType = contentType,
    path = path,
    parent = parent,
    starred = starred,
    created = created,
)

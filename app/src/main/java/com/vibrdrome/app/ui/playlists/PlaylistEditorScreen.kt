package com.vibrdrome.app.ui.playlists

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditorScreen(
    playlistId: String?,
    client: SubsonicClient,
    onNavigateBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(playlistId != null) }
    var isSaving by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var showSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load existing playlist if editing
    LaunchedEffect(playlistId) {
        if (playlistId != null) {
            try {
                val playlist = client.getPlaylist(playlistId)
                name = playlist.name
                songs = playlist.entry ?: emptyList()
            } catch (_: Throwable) {}
            isLoading = false
        }
    }

    // Debounced search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        try {
            val results = client.search(searchQuery, artistCount = 0, albumCount = 0, songCount = 20)
            searchResults = results.song ?: emptyList()
        } catch (_: Throwable) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (playlistId != null) "Edit Playlist" else "New Playlist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                try {
                                    if (playlistId != null) {
                                        client.updatePlaylist(
                                            id = playlistId,
                                            name = name,
                                        )
                                    } else {
                                        client.createPlaylist(
                                            name = name,
                                            songIds = songs.map { it.id },
                                        )
                                    }
                                    onNavigateBack()
                                } catch (_: Throwable) {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = name.isNotBlank() && !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(8.dp),
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
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
            LazyColumn(modifier = Modifier.padding(padding)) {
                // Name field
                item(key = "name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }

                // Add songs section
                item(key = "add_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSearch = !showSearch }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Songs", style = MaterialTheme.typography.titleSmall)
                    }
                }

                if (showSearch) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search songs...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    items(searchResults, key = { "search_${it.id}" }) { song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    songs = songs + song
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    showSearch = false
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                song.artist?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }

                // Current songs
                item(key = "songs_header") {
                    Text(
                        text = "${songs.size} songs",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                itemsIndexed(songs, key = { index, song -> "song_${index}_${song.id}" }) { index, song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val meta = buildList {
                                song.artist?.let { add(it) }
                                song.duration?.let { add(formatDuration(it)) }
                            }
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta.joinToString(" \u00b7 "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = {
                            songs = songs.toMutableList().also { it.removeAt(index) }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove")
                        }
                    }
                    HorizontalDivider()
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

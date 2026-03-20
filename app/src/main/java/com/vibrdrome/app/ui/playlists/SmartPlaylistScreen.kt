package com.vibrdrome.app.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.SubsonicClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class SmartGenerator(
    val title: String,
    val description: String,
    val needsInput: Boolean = false,
    val inputLabel: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistScreen(
    client: SubsonicClient,
    onNavigateBack: () -> Unit,
) {
    val playbackManager: PlaybackManager = koinInject()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf<SmartGenerator?>(null) }

    val generators = listOf(
        SmartGenerator("Random Mix", "Discover random songs from your library"),
        SmartGenerator("Artist Mix", "Songs similar to an artist", needsInput = true, inputLabel = "Artist name"),
        SmartGenerator("Genre Mix", "Random songs from a genre", needsInput = true, inputLabel = "Genre"),
        SmartGenerator("Top Songs", "Most popular songs by an artist", needsInput = true, inputLabel = "Artist name"),
        SmartGenerator("Recently Added", "Newest additions to your library"),
        SmartGenerator("Starred Songs", "All your favorite songs shuffled"),
    )

    fun execute(generator: SmartGenerator, input: String = "") {
        isLoading = true
        scope.launch {
            try {
                val songs = when (generator.title) {
                    "Random Mix" -> client.getRandomSongs(size = 50)
                    "Artist Mix" -> {
                        val results = client.search(input, artistCount = 1, albumCount = 0, songCount = 0)
                        val artistId = results.artist?.firstOrNull()?.id
                        if (artistId != null) client.getSimilarSongs(artistId, count = 50)
                        else emptyList()
                    }
                    "Genre Mix" -> client.getRandomSongs(size = 50, genre = input)
                    "Top Songs" -> client.getTopSongs(input, count = 50)
                    "Recently Added" -> {
                        val albums = client.getAlbumList(
                            com.vibrdrome.app.network.AlbumListType.NEWEST, size = 10
                        )
                        albums.flatMap { album ->
                            try { client.getAlbum(album.id).song ?: emptyList() }
                            catch (_: Throwable) { emptyList() }
                        }.take(50)
                    }
                    "Starred Songs" -> {
                        val starred = client.getStarred()
                        (starred.song ?: emptyList()).shuffled()
                    }
                    else -> emptyList()
                }
                if (songs.isNotEmpty()) {
                    playbackManager.play(songs)
                }
            } catch (_: Throwable) {}
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Playlists") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn {
                items(generators) { generator ->
                    ListItem(
                        headlineContent = { Text(generator.title) },
                        supportingContent = { Text(generator.description) },
                        modifier = Modifier.clickable {
                            if (generator.needsInput) {
                                showInputDialog = generator
                            } else {
                                execute(generator)
                            }
                        },
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    showInputDialog?.let { generator ->
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInputDialog = null },
            title = { Text(generator.title) },
            text = {
                Column {
                    Text(generator.description)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(generator.inputLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInputDialog = null
                        execute(generator, input)
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Text("Play")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = null }) { Text("Cancel") }
            },
        )
    }
}

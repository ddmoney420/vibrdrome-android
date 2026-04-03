package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.Song
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.ui.components.TrackListItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    client: SubsonicClient,
    onNavigateBack: () -> Unit = {},
    onGoToAlbum: ((String) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
) {
    val playbackManager: PlaybackManager = koinInject()
    val appState: AppState = koinInject()
    val folderId by appState.selectedFolderId.collectAsState()
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folderId) {
        try {
            songs = client.getRandomSongs(size = 100, musicFolderId = folderId)
        } catch (_: Throwable) {}
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Songs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (songs.isNotEmpty()) playbackManager.playShuffle(songs) },
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle All")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    songs = client.getRandomSongs(size = 100, musicFolderId = folderId)
                                } catch (_: Throwable) {}
                                isLoading = false
                            }
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading && songs.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    TrackListItem(
                        song = song,
                        showTrackNumber = false,
                        onClick = { playbackManager.play(songs, index) },
                        onGoToAlbum = onGoToAlbum,
                        onGoToArtist = onGoToArtist,
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

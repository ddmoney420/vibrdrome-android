package com.vibrdrome.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
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
import com.vibrdrome.app.network.LyricLine
import com.vibrdrome.app.network.StructuredLyrics
import com.vibrdrome.app.network.SubsonicClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    playbackManager: PlaybackManager,
    client: SubsonicClient,
    onNavigateBack: () -> Unit,
) {
    val currentSong by playbackManager.currentSong.collectAsState()
    val positionMs by playbackManager.positionMs.collectAsState()

    var lyrics by remember { mutableStateOf<StructuredLyrics?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var noLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(currentSong?.id) {
        val songId = currentSong?.id ?: return@LaunchedEffect
        isLoading = true
        noLyrics = false
        lyrics = null
        try {
            val result = client.getLyrics(songId)
            val structured = result?.structuredLyrics?.firstOrNull()
            if (structured != null) {
                lyrics = structured
            } else {
                noLyrics = true
            }
        } catch (e: Throwable) {
            android.util.Log.e("LyricsScreen", "Failed to load lyrics", e)
            noLyrics = true
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentSong?.title ?: "Lyrics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                noLyrics -> Text(
                    text = "No lyrics available",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lyrics != null -> {
                    val lines = lyrics!!.line ?: emptyList()
                    val isSynced = lyrics!!.synced

                    if (isSynced) {
                        SyncedLyrics(
                            lines = lines,
                            positionMs = positionMs,
                            onLineClick = { startMs ->
                                playbackManager.seekTo(startMs.toLong())
                            },
                        )
                    } else {
                        PlainLyrics(lines = lines)
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    onLineClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Find the active line
    val activeIndex = lines.indexOfLast { line ->
        val start = line.start ?: return@indexOfLast false
        positionMs >= start
    }

    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -200,
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Spacer(Modifier.height(100.dp)) }
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.value,
                style = if (isActive) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { line.start?.let { onLineClick(it) } }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        item { Spacer(Modifier.height(200.dp)) }
    }
}

@Composable
private fun PlainLyrics(lines: List<LyricLine>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Spacer(Modifier.height(16.dp)) }
        itemsIndexed(lines) { _, line ->
            Text(
                text = line.value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

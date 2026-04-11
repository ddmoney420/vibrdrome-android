package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.network.Album
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.network.SubsonicError
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.components.FormatBadge
import com.vibrdrome.app.ui.components.TrackListItem
import com.vibrdrome.app.util.formatDuration
import com.vibrdrome.app.audio.PlaybackManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    client: SubsonicClient,
    onNavigateBack: () -> Unit = {},
    onNavigateToArtist: ((String) -> Unit)? = null,
) {
    val playbackManager: PlaybackManager = koinInject()
    var album by remember { mutableStateOf<Album?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isStarred by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(albumId) {
        isLoading = true
        error = null
        try {
            album = client.getAlbum(albumId)
            isStarred = album?.starred != null
        } catch (e: Throwable) {
            error = SubsonicError.userMessage(e)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "Album") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                isLoading && album == null -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                error != null && album == null -> {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )
                }
                album != null -> {
                    val currentAlbum = album!!
                    val songs = currentAlbum.song ?: emptyList()

                    val listState = rememberLazyListState()
                    LazyColumn(state = listState) {
                        // Album header with parallax
                        item(key = "header") {
                            val parallaxOffset = if (listState.firstVisibleItemIndex == 0) {
                                listState.firstVisibleItemScrollOffset * 0.4f
                            } else 0f

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 20.dp),
                            ) {
                                AlbumArtView(
                                    coverArtUrl = currentAlbum.coverArt?.let { client.coverArtURL(it, size = 480) },
                                    size = 240.dp,
                                    cornerRadius = 12.dp,
                                    modifier = Modifier.graphicsLayer {
                                        translationY = parallaxOffset
                                        val scale = 1f - (parallaxOffset / 2000f).coerceIn(0f, 0.1f)
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = currentAlbum.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )

                                currentAlbum.artist?.let { artist ->
                                    Text(
                                        text = artist,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = if (onNavigateToArtist != null && currentAlbum.artistId != null)
                                            Modifier.clickable { onNavigateToArtist(currentAlbum.artistId!!) }
                                        else Modifier,
                                    )
                                }

                                // Metadata row
                                val meta = buildList {
                                    currentAlbum.year?.let { add("$it") }
                                    currentAlbum.genre?.let { add(it) }
                                    currentAlbum.songCount?.let { add("$it songs") }
                                    currentAlbum.duration?.let { add(formatDuration(it)) }
                                }
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }

                                // Show format badge from first song
                                songs.firstOrNull()?.let { firstSong ->
                                    FormatBadge(
                                        suffix = firstSong.suffix,
                                        bitRate = firstSong.bitRate,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }

                        // Action buttons
                        item(key = "actions") {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
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

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            val wasStarred = isStarred
                                            isStarred = !wasStarred
                                            scope.launch {
                                                try {
                                                    if (wasStarred) {
                                                        client.unstar(albumId = albumId)
                                                    } else {
                                                        client.star(albumId = albumId)
                                                    }
                                                } catch (_: Throwable) {
                                                    isStarred = wasStarred
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (isStarred) "Unstar" else "Star",
                                            tint = if (isStarred) Color(0xFFFF69B4) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // Songs (grouped by disc if multi-disc)
                        val discs = songs.groupBy { it.discNumber ?: 1 }.toSortedMap()
                        val isMultiDisc = discs.size > 1

                        discs.forEach { (discNum, discSongs) ->
                            if (isMultiDisc) {
                                item(key = "disc_$discNum") {
                                    Text(
                                        text = "Disc $discNum",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 16.dp,
                                            bottom = 4.dp,
                                        ),
                                    )
                                    HorizontalDivider()
                                }
                            }
                            discSongs.forEach { song ->
                                val globalIndex = songs.indexOf(song)
                                item(key = song.id) {
                                    TrackListItem(
                                        song = song,
                                        showTrackNumber = true,
                                        onClick = { playbackManager.play(songs, globalIndex) },
                                        onGoToArtist = onNavigateToArtist,
                                    )
                                    HorizontalDivider(Modifier.padding(start = 56.dp))
                                }
                            }
                        }

                        // Footer
                        currentAlbum.duration?.let { duration ->
                            item(key = "footer") {
                                HorizontalDivider()
                                Text(
                                    text = "${currentAlbum.songCount ?: 0} songs, ${formatDuration(duration)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

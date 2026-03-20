package com.vibrdrome.app.ui.player

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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.SleepTimer
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
    onNavigateToQueue: () -> Unit = {},
    onNavigateToEQ: () -> Unit = {},
    onNavigateToLyrics: () -> Unit = {},
    onNavigateToAlbum: ((String) -> Unit)? = null,
    onNavigateToArtist: ((String) -> Unit)? = null,
) {
    val currentSong by playbackManager.currentSong.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val positionMs by playbackManager.positionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()
    val repeatMode by playbackManager.repeatMode.collectAsState()
    val shuffleEnabled by playbackManager.shuffleEnabled.collectAsState()
    val playbackSpeed by playbackManager.playbackSpeed.collectAsState()
    val coverArtUrl by playbackManager.currentCoverArtUrl.collectAsState()

    val song = currentSong
    if (song == null) {
        // No song playing — show back button so user isn't stuck
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dismiss")
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                Text("No song playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Dismiss")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToLyrics) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Lyrics")
                    }
                    IconButton(onClick = onNavigateToEQ) {
                        Icon(Icons.Default.Equalizer, contentDescription = "Equalizer")
                    }
                    IconButton(onClick = onNavigateToQueue) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.weight(1f))

            AlbumArtView(
                coverArtUrl = coverArtUrl,
                size = 300.dp,
                cornerRadius = 16.dp,
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = song.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onNavigateToArtist != null && song.artistId != null)
                    Modifier.clickable { onNavigateToArtist(song.artistId!!) } else Modifier,
            )
            song.album?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onNavigateToAlbum != null && song.albumId != null)
                        Modifier.clickable { onNavigateToAlbum(song.albumId!!) } else Modifier,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Seek bar
            var isSeeking by remember { mutableStateOf(false) }
            var seekFraction by remember { mutableFloatStateOf(0f) }
            val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

            Slider(
                value = if (isSeeking) seekFraction else fraction,
                onValueChange = {
                    isSeeking = true
                    seekFraction = it
                },
                onValueChangeFinished = {
                    playbackManager.seekTo((seekFraction * durationMs).toLong())
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formatDurationMs(
                        if (isSeeking) (seekFraction * durationMs).toLong() else positionMs
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDurationMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Transport controls
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = { playbackManager.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { playbackManager.previous() }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp),
                    )
                }
                FilledIconButton(
                    onClick = { playbackManager.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { playbackManager.next() }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = { playbackManager.toggleRepeatMode() }) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                        else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Speed + Sleep timer row
            val sleepTimer = playbackManager.sleepTimer
            val sleepActive by sleepTimer.isActive.collectAsState()
            val sleepRemaining by sleepTimer.remainingSeconds.collectAsState()
            var showSleepDialog by remember { mutableStateOf(false) }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                TextButton(
                    onClick = {
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        val nextIndex = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                        playbackManager.setPlaybackSpeed(speeds[nextIndex])
                    },
                ) {
                    Text("${playbackSpeed}x", style = MaterialTheme.typography.labelLarge)
                }

                TextButton(
                    onClick = {
                        if (sleepActive) sleepTimer.cancel() else showSleepDialog = true
                    },
                ) {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sleepActive) {
                        Text(
                            " ${sleepTimer.formattedTime()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (showSleepDialog) {
                AlertDialog(
                    onDismissRequest = { showSleepDialog = false },
                    title = { Text("Sleep Timer") },
                    text = {
                        Column {
                            sleepTimer.options.forEach { minutes ->
                                TextButton(
                                    onClick = {
                                        sleepTimer.start(minutes)
                                        showSleepDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("$minutes minutes")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSleepDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

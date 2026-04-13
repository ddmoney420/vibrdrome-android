package com.vibrdrome.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Subtitles
import android.widget.Toast
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Waves
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
// AndroidView not used — Cast uses system settings intent
import kotlinx.coroutines.launch
import androidx.media3.common.Player
// MediaRouteButton removed — incompatible with Compose themes on some devices
import com.vibrdrome.app.audio.AdaptiveBitrate
import com.vibrdrome.app.audio.JukeboxManager
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.ReplayGainMode
import org.koin.compose.koinInject
import com.vibrdrome.app.audio.SleepTimer
import com.vibrdrome.app.ui.components.AddToPlaylistDialog
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.components.FormatBadge
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
    onNavigateToVisualizer: () -> Unit = {},
) {
    val currentSong by playbackManager.currentSong.collectAsState()
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val positionMs by playbackManager.positionMs.collectAsState()
    val durationMs by playbackManager.durationMs.collectAsState()
    val repeatMode by playbackManager.repeatMode.collectAsState()
    val shuffleEnabled by playbackManager.shuffleEnabled.collectAsState()
    val playbackSpeed by playbackManager.playbackSpeed.collectAsState()
    val coverArtUrl by playbackManager.currentCoverArtUrl.collectAsState()
    val isCasting by playbackManager.isCasting.collectAsState()
    val castDeviceName by playbackManager.castDeviceName.collectAsState()
    val activityContext = LocalContext.current
    val appState: com.vibrdrome.app.ui.AppState = koinInject()
    val jukeboxManager: JukeboxManager = koinInject()
    val isJukebox by jukeboxManager.enabled.collectAsState()
    val jukeboxGain by jukeboxManager.gain.collectAsState()
    val jukeboxStatus by jukeboxManager.status.collectAsState()
    val effectiveIsPlaying = if (isJukebox) jukeboxStatus?.playing ?: false else isPlaying

    var isStarred by remember(currentSong?.id) { mutableStateOf(currentSong?.starred != null) }
    var currentRating by remember(currentSong?.id) { mutableStateOf(currentSong?.userRating ?: 0) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

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
                    // Cast button — opens system Cast route selector dialog
                    IconButton(onClick = {
                        try {
                            val castCtx = playbackManager.castManager.getCastContext() ?: return@IconButton
                            val router = androidx.mediarouter.media.MediaRouter.getInstance(activityContext)
                            val selector = com.google.android.gms.cast.CastMediaControlIntent.categoryForCast(
                                com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                            )
                            val routeSelector = androidx.mediarouter.media.MediaRouteSelector.Builder()
                                .addControlCategory(selector)
                                .build()
                            if (isCasting) {
                                castCtx.sessionManager.endCurrentSession(true)
                            } else {
                                // Trigger Cast SDK's device chooser via MediaRouter
                                router.addCallback(routeSelector, object : androidx.mediarouter.media.MediaRouter.Callback() {}, androidx.mediarouter.media.MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
                                // Open system Cast settings as fallback
                                try {
                                    val intent = android.content.Intent("android.settings.CAST_SETTINGS")
                                    activityContext.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                                        activityContext.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = "Cast",
                            tint = if (isCasting) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onNavigateToVisualizer, enabled = !isCasting) {
                        Icon(Icons.Default.Waves, contentDescription = "Visualizer")
                    }
                    IconButton(onClick = onNavigateToLyrics) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Lyrics")
                    }
                    IconButton(onClick = onNavigateToEQ, enabled = !isCasting) {
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
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            AlbumArtView(
                coverArtUrl = coverArtUrl,
                size = 280.dp,
                cornerRadius = 16.dp,
            )

            // Casting indicator
            if (isCasting) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.CastConnected,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Casting to ${castDeviceName ?: "device"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Jukebox indicator
            if (isJukebox) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Speaker,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Playing on server",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

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

            // ReplayGain / normalization indicator
            val replayGain = song.replayGain
            if (replayGain?.trackGain != null || replayGain?.albumGain != null) {
                val mode = playbackManager.replayGainMode.collectAsState().value
                val gain = when (mode) {
                    ReplayGainMode.ALBUM -> replayGain.albumGain ?: replayGain.trackGain
                    else -> replayGain.trackGain ?: replayGain.albumGain
                }
                if (gain != null) {
                    Text(
                        text = "RG: %+.1f dB".format(gain),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // Format badge
            FormatBadge(
                suffix = song.suffix,
                bitRate = song.bitRate,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Quality + Scrobble indicators
            val adaptiveBitrateEngine: AdaptiveBitrate = koinInject()
            val adaptiveQuality by adaptiveBitrateEngine.currentQuality.collectAsState()
            if (adaptiveQuality != null) {
                Text(
                    text = adaptiveBitrateEngine.qualityLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            // Scrobble indicator
            val lastScrobbleTime by playbackManager.lastScrobbleTime.collectAsState()
            val showScrobbled = remember(lastScrobbleTime) {
                System.currentTimeMillis() - lastScrobbleTime < 3000
            }
            if (showScrobbled) {
                Text(
                    text = "Scrobbled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
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
                IconButton(onClick = { if (isJukebox) jukeboxManager.previous() else playbackManager.previous() }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp),
                    )
                }
                FilledIconButton(
                    onClick = {
                        if (isJukebox) {
                            if (effectiveIsPlaying) jukeboxManager.stop() else jukeboxManager.play()
                        } else {
                            playbackManager.togglePlayPause()
                        }
                    },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (effectiveIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (effectiveIsPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { if (isJukebox) jukeboxManager.next() else playbackManager.next() }) {
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

            Spacer(Modifier.height(4.dp))

            // Speed + Sleep + Bookmark row
            val bookmarkScope = rememberCoroutineScope()
            val bookmarkContext = LocalContext.current
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

                // Bookmark current position
                TextButton(onClick = {
                    bookmarkScope.launch {
                        try {
                            appState.subsonicClient.createBookmark(song.id, positionMs.toInt())
                            Toast.makeText(bookmarkContext, "Bookmarked", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(bookmarkContext, "Bookmark failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Icon(
                        Icons.Default.BookmarkAdd,
                        contentDescription = "Bookmark",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Favorite
                TextButton(onClick = {
                    val wasStarred = isStarred
                    isStarred = !wasStarred
                    bookmarkScope.launch {
                        try {
                            val client = appState.subsonicClient
                            if (wasStarred) client.unstar(id = song.id)
                            else client.star(id = song.id)
                        } catch (_: Throwable) {
                            isStarred = wasStarred
                        }
                    }
                }) {
                    Icon(
                        if (isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isStarred) "Unfavorite" else "Favorite",
                        tint = if (isStarred) Color(0xFFFF69B4)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Add to Playlist
                TextButton(onClick = { showPlaylistDialog = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add to Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            }

            // Star rating row
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                (1..5).forEach { star ->
                    IconButton(
                        onClick = {
                            val newRating = if (currentRating == star) 0 else star
                            val oldRating = currentRating
                            currentRating = newRating
                            bookmarkScope.launch {
                                try {
                                    appState.subsonicClient.setRating(song.id, newRating)
                                } catch (_: Throwable) {
                                    currentRating = oldRating
                                }
                            }
                        },
                    ) {
                        Icon(
                            if (star <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star star",
                            tint = if (star <= currentRating) Color(0xFFFFB300)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPlaylistDialog) {
        AddToPlaylistDialog(
            songId = song.id,
            client = appState.subsonicClient,
            onDismiss = { showPlaylistDialog = false },
            onAdded = { name ->
                Toast.makeText(activityContext, "Added to $name", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

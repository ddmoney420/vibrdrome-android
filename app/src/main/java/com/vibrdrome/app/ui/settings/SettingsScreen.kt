package com.vibrdrome.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.EQEngine
import com.vibrdrome.app.audio.HapticEngine
import com.vibrdrome.app.audio.HapticIntensity
import com.vibrdrome.app.audio.ImmersiveMode
import com.vibrdrome.app.audio.JukeboxManager
import com.vibrdrome.app.audio.AdaptiveBitrate
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.ReplayGainMode
import com.vibrdrome.app.audio.SmartTransitions
import com.vibrdrome.app.downloads.CacheManager
import com.vibrdrome.app.ui.AppState
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appState: AppState,
    onNavigateBack: () -> Unit,
    onNavigateToServerManager: () -> Unit,
    onNavigateToEQ: () -> Unit,
    onSignOut: () -> Unit,
) {
    val eqEngine: EQEngine = koinInject()
    val playbackManager: PlaybackManager = koinInject()
    val cacheManager: CacheManager = koinInject()
    val hapticEngine: HapticEngine = koinInject()
    val immersiveMode: ImmersiveMode = koinInject()
    val smartTransitions: SmartTransitions = koinInject()
    val adaptiveBitrate: AdaptiveBitrate = koinInject()
    val jukeboxManager: JukeboxManager = koinInject()
    var cacheSizeMb by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        cacheSizeMb = cacheManager.currentCacheSizeMb()
    }
    val serverUrl by appState.serverURL.collectAsState()
    val username by appState.username.collectAsState()
    val eqEnabled by eqEngine.isEnabled.collectAsState()
    val eqPreset by eqEngine.currentPresetName.collectAsState()
    val crossfadeEnabled by playbackManager.crossfadeEnabled.collectAsState()
    val hapticEnabled by hapticEngine.enabled.collectAsState()
    val hapticIntensity by hapticEngine.intensity.collectAsState()
    val isImmersive by immersiveMode.enabled.collectAsState()
    val replayGainMode by playbackManager.replayGainMode.collectAsState()
    val queueSyncEnabled by playbackManager.queueSyncEnabled.collectAsState()
    val scrobbleEnabled by playbackManager.scrobbleEnabled.collectAsState()
    val jukeboxEnabled by jukeboxManager.enabled.collectAsState()
    val smartTransitionsEnabled by smartTransitions.enabled.collectAsState()
    val adaptiveCellular by adaptiveBitrate.enabledOnCellular.collectAsState()
    val adaptiveWifi by adaptiveBitrate.enabledOnWifi.collectAsState()
    val autoNormalize by playbackManager.autoNormalizeEnabled.collectAsState()
    val crossfadeOnlyShuffle by playbackManager.crossfadeOnlyOnShuffle.collectAsState()
    var showHapticIntensityDialog by remember { mutableStateOf(false) }
    var showReplayGainDialog by remember { mutableStateOf(false) }
    val wifiQuality by appState.streamQualityWifi.collectAsState()
    val cellularQuality by appState.streamQualityCellular.collectAsState()
    val themeMode by appState.themeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showWifiQualityDialog by remember { mutableStateOf(false) }
    var showCellularQualityDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Server
            SectionHeader("Server")
            ListItem(
                headlineContent = { Text(serverUrl.ifEmpty { "Not configured" }) },
                supportingContent = { Text(username.ifEmpty { "-" }) },
                leadingContent = { Icon(Icons.Default.Dns, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text("Manage Servers") },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNavigateToServerManager),
            )
            ListItem(
                headlineContent = { Text("Sign Out") },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable { showSignOutConfirm = true },
            )
            HorizontalDivider()

            // Playback
            SectionHeader("Playback")
            ListItem(
                headlineContent = { Text("Equalizer") },
                supportingContent = {
                    Text(if (eqEnabled) "On \u00b7 $eqPreset" else "Off")
                },
                leadingContent = { Icon(Icons.Default.Equalizer, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNavigateToEQ),
            )
            ListItem(
                headlineContent = { Text("Crossfade") },
                supportingContent = { Text(if (crossfadeEnabled) "5 seconds" else "Off") },
                leadingContent = { Icon(Icons.Default.Speed, contentDescription = null) },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = crossfadeEnabled,
                        onCheckedChange = { playbackManager.setCrossfadeEnabled(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Jukebox Mode") },
                supportingContent = { Text(if (jukeboxEnabled) "Playing on server" else "Play through server speakers") },
                leadingContent = { Icon(Icons.Default.Speaker, contentDescription = null) },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = jukeboxEnabled,
                        onCheckedChange = {
                            if (it) {
                                jukeboxManager.enable { playbackManager.player.pause() }
                            } else {
                                jukeboxManager.disable()
                            }
                        },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Crossfade only on shuffle") },
                supportingContent = { Text("Gapless for albums, crossfade for shuffle") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = crossfadeOnlyShuffle,
                        onCheckedChange = { playbackManager.setCrossfadeOnlyOnShuffle(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Haptic Feedback") },
                supportingContent = { Text(if (hapticEnabled) hapticIntensity.label else "Off") },
                leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { hapticEngine.setEnabled(it) },
                    )
                },
            )
            if (hapticEnabled) {
                ListItem(
                    headlineContent = { Text("Haptic Intensity") },
                    supportingContent = { Text(hapticIntensity.label) },
                    modifier = Modifier.clickable { showHapticIntensityDialog = true },
                )
            }
            ListItem(
                headlineContent = { Text("Immersive Mode") },
                supportingContent = { Text("Visualizer + haptics + crossfade") },
                leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = isImmersive,
                        onCheckedChange = {
                            immersiveMode.toggle(playbackManager, hapticEngine)
                        },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("ReplayGain") },
                supportingContent = { Text(replayGainMode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.clickable { showReplayGainDialog = true },
            )
            ListItem(
                headlineContent = { Text("Auto Normalize") },
                supportingContent = { Text("Level volume for tracks without ReplayGain") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = autoNormalize,
                        onCheckedChange = { playbackManager.setAutoNormalize(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Scrobble") },
                supportingContent = { Text("Report plays to server") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = scrobbleEnabled,
                        onCheckedChange = { playbackManager.setScrobbleEnabled(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Queue Sync") },
                supportingContent = { Text("Sync queue across devices") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = queueSyncEnabled,
                        onCheckedChange = { playbackManager.setQueueSyncEnabled(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Smart Transitions") },
                supportingContent = { Text("Auto gapless/crossfade based on content") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = smartTransitionsEnabled,
                        onCheckedChange = { smartTransitions.setEnabled(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Adaptive Quality (Cellular)") },
                supportingContent = { Text("Auto-adjust bitrate based on network speed") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = adaptiveCellular,
                        onCheckedChange = { adaptiveBitrate.setEnabledOnCellular(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Adaptive Quality (WiFi)") },
                supportingContent = { Text("Usually not needed on WiFi") },
                trailingContent = {
                    androidx.compose.material3.Switch(
                        checked = adaptiveWifi,
                        onCheckedChange = { adaptiveBitrate.setEnabledOnWifi(it) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Stream Quality (WiFi)") },
                supportingContent = { Text(AppState.qualityLabel(wifiQuality)) },
                leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
                modifier = Modifier.clickable { showWifiQualityDialog = true },
            )
            ListItem(
                headlineContent = { Text("Stream Quality (Cellular)") },
                supportingContent = { Text(AppState.qualityLabel(cellularQuality)) },
                leadingContent = { Icon(Icons.Default.SignalCellularAlt, contentDescription = null) },
                modifier = Modifier.clickable { showCellularQualityDialog = true },
            )
            HorizontalDivider()

            // Appearance
            SectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Text(
                        when (themeMode) {
                            "dark" -> "Dark"
                            "light" -> "Light"
                            else -> "System"
                        }
                    )
                },
                leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                modifier = Modifier.clickable { showThemeDialog = true },
            )
            HorizontalDivider()

            // Storage
            SectionHeader("Storage")
            ListItem(
                headlineContent = { Text("Cache Size") },
                supportingContent = { Text("${cacheSizeMb} MB used") },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
            )
            ListItem(
                headlineContent = { Text("Clear Cache") },
                supportingContent = { Text("Remove cached API responses") },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                modifier = Modifier.clickable {
                    scope.launch {
                        cacheManager.evictApiCache()
                        cacheSizeMb = cacheManager.currentCacheSizeMb()
                    }
                },
            )
            HorizontalDivider()

            // About
            SectionHeader("About")
            ListItem(
                headlineContent = { Text("Vibrdrome") },
                supportingContent = { Text("Version 1.0.0") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
            )

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (value, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = themeMode == value,
                                    onClick = {
                                        appState.setThemeMode(value)
                                        showThemeDialog = false
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                appState.setThemeMode(value)
                                showThemeDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showWifiQualityDialog) {
        StreamQualityDialog(
            title = "WiFi Stream Quality",
            selected = wifiQuality,
            onSelect = { appState.setStreamQualityWifi(it); showWifiQualityDialog = false },
            onDismiss = { showWifiQualityDialog = false },
        )
    }

    if (showCellularQualityDialog) {
        StreamQualityDialog(
            title = "Cellular Stream Quality",
            selected = cellularQuality,
            onSelect = { appState.setStreamQualityCellular(it); showCellularQualityDialog = false },
            onDismiss = { showCellularQualityDialog = false },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    appState.clearCredentials()
                    onSignOut()
                }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showHapticIntensityDialog) {
        AlertDialog(
            onDismissRequest = { showHapticIntensityDialog = false },
            title = { Text("Haptic Intensity") },
            text = {
                Column {
                    HapticIntensity.entries.forEach { level ->
                        ListItem(
                            headlineContent = { Text(level.label) },
                            leadingContent = {
                                RadioButton(
                                    selected = hapticIntensity == level,
                                    onClick = {
                                        hapticEngine.setIntensity(level)
                                        showHapticIntensityDialog = false
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                hapticEngine.setIntensity(level)
                                showHapticIntensityDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHapticIntensityDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showReplayGainDialog) {
        AlertDialog(
            onDismissRequest = { showReplayGainDialog = false },
            title = { Text("ReplayGain Mode") },
            text = {
                Column {
                    ReplayGainMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ReplayGainMode.OFF -> "Off"
                            ReplayGainMode.TRACK -> "Track"
                            ReplayGainMode.ALBUM -> "Album"
                            ReplayGainMode.AUTO -> "Auto (album for albums, track for shuffle)"
                        }
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = replayGainMode == mode,
                                    onClick = {
                                        playbackManager.setReplayGainMode(mode)
                                        showReplayGainDialog = false
                                    },
                                )
                            },
                            modifier = Modifier.clickable {
                                playbackManager.setReplayGainMode(mode)
                                showReplayGainDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReplayGainDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StreamQualityDialog(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AppState.QUALITY_OPTIONS.forEach { (value, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(
                                selected = selected == value,
                                onClick = { onSelect(value) },
                            )
                        },
                        modifier = Modifier.clickable { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

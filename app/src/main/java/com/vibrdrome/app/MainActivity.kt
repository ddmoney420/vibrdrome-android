package com.vibrdrome.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.AlbumListType
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.ui.library.AlbumDetailScreen
import com.vibrdrome.app.ui.library.AlbumsScreen
import com.vibrdrome.app.ui.library.ArtistDetailScreen
import com.vibrdrome.app.ui.library.ArtistsScreen
import com.vibrdrome.app.ui.library.DownloadsScreen
import com.vibrdrome.app.ui.library.FavoritesScreen
import com.vibrdrome.app.ui.library.FolderBrowserScreen
import com.vibrdrome.app.ui.library.FolderDetailScreen
import com.vibrdrome.app.ui.library.GenerationsScreen
import com.vibrdrome.app.ui.library.GenresScreen
import com.vibrdrome.app.ui.library.LibraryScreen
import com.vibrdrome.app.ui.library.SongsScreen
import com.vibrdrome.app.ui.navigation.*
import com.vibrdrome.app.ui.player.EQScreen
import com.vibrdrome.app.ui.player.LyricsScreen
import com.vibrdrome.app.ui.player.MiniPlayer
import com.vibrdrome.app.ui.player.NowPlayingScreen
import com.vibrdrome.app.ui.player.QueueScreen
import com.vibrdrome.app.ui.playlists.PlaylistDetailScreen
import com.vibrdrome.app.ui.playlists.PlaylistEditorScreen
import com.vibrdrome.app.ui.playlists.PlaylistsScreen
import com.vibrdrome.app.ui.playlists.SmartPlaylistScreen
import com.vibrdrome.app.ui.radio.AddStationScreen
import com.vibrdrome.app.ui.radio.RadioScreen
import com.vibrdrome.app.ui.radio.StationSearchScreen
import com.vibrdrome.app.ui.search.SearchScreen
import com.vibrdrome.app.ui.settings.ServerConfigScreen
import com.vibrdrome.app.ui.settings.ServerManagerScreen
import com.vibrdrome.app.ui.settings.SettingsScreen
import com.vibrdrome.app.ui.theme.VibrdromeAppTheme
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    private val appState: AppState by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appState.themeMode.collectAsState()
            VibrdromeAppTheme(themeMode = themeMode) {
                VibrdromeNavHost(appState)
            }
        }
    }
}

@Composable
private fun VibrdromeNavHost(appState: AppState) {
    val playbackManager: PlaybackManager = koinInject()
    val isConfigured by appState.isConfigured.collectAsState()
    val currentSong by playbackManager.currentSong.collectAsState()
    val currentCoverArtUrl by playbackManager.currentCoverArtUrl.collectAsState()
    val requiresReAuth by appState.requiresReAuth.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val showMiniPlayer = currentSong != null &&
        navBackStackEntry?.destination?.route?.contains("NowPlayingRoute") != true
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by appState.snackbarMessage.collectAsState()
    val snackbarScope = rememberCoroutineScope()

    // Show global error snackbar
    snackbarMessage?.let { message ->
        snackbarScope.launch {
            snackbarHostState.showSnackbar(message)
            appState.clearSnackbar()
        }
    }

    if (requiresReAuth) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Session Expired") },
            text = {
                Column {
                    Text("Please re-enter your password to continue.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { appState.reAuthenticate(password) },
                    enabled = password.isNotBlank(),
                ) { Text("Sign In") }
            },
            dismissButton = {
                TextButton(onClick = {
                    appState.clearCredentials()
                    navController.navigate(ServerConfigRoute) { popUpTo(0) { inclusive = true } }
                }) { Text("Sign Out") }
            },
        )
    }

    Scaffold(
        bottomBar = {
            if (showMiniPlayer) {
                MiniPlayer(
                    playbackManager = playbackManager,
                    coverArtUrl = currentCoverArtUrl,
                    onClick = { navController.navigate(NowPlayingRoute) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isConfigured) LibraryRoute else ServerConfigRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            // Auth
            composable<ServerConfigRoute> {
                ServerConfigScreen(
                    appState = appState,
                    onSignedIn = {
                        navController.navigate(LibraryRoute) {
                            popUpTo(ServerConfigRoute) { inclusive = true }
                        }
                    },
                )
            }

            // Library
            composable<LibraryRoute> {
                LibraryScreen(
                    client = appState.subsonicClient,
                    onNavigateToArtists = { navController.navigate(ArtistsRoute) },
                    onNavigateToAlbums = { t, n -> navController.navigate(AlbumsListRoute(t, n)) },
                    onNavigateToAlbumDetail = { navController.navigate(AlbumDetailRoute(it)) },
                    onNavigateToSearch = { navController.navigate(SearchRoute) },
                    onNavigateToSongs = { navController.navigate(SongsRoute) },
                    onNavigateToGenerations = { navController.navigate(GenerationsRoute) },
                    onNavigateToGenres = { navController.navigate(GenresRoute) },
                    onNavigateToFavorites = { navController.navigate(FavoritesRoute) },
                    onNavigateToPlaylists = { navController.navigate(PlaylistsRoute) },
                    onNavigateToRadio = { navController.navigate(RadioRoute) },
                    onNavigateToFolders = { navController.navigate(FolderBrowserRoute) },
                    onNavigateToSettings = { navController.navigate(SettingsRoute) },
                    onNavigateToDownloads = { navController.navigate(DownloadsRoute) },
                )
            }

            composable<ArtistsRoute> {
                ArtistsScreen(
                    client = appState.subsonicClient,
                    onArtistClick = { navController.navigate(ArtistDetailRoute(it)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<ArtistDetailRoute> { e ->
                ArtistDetailScreen(
                    artistId = e.toRoute<ArtistDetailRoute>().artistId,
                    client = appState.subsonicClient,
                    onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<AlbumDetailRoute> { e ->
                AlbumDetailScreen(
                    albumId = e.toRoute<AlbumDetailRoute>().albumId,
                    client = appState.subsonicClient,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<AlbumsListRoute> { e ->
                val r = e.toRoute<AlbumsListRoute>()
                AlbumsScreen(
                    listType = AlbumListType.entries.find { it.value == r.listType } ?: AlbumListType.NEWEST,
                    title = r.title,
                    genre = r.genre,
                    fromYear = r.fromYear,
                    toYear = r.toYear,
                    client = appState.subsonicClient,
                    onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<GenresRoute> {
                GenresScreen(
                    client = appState.subsonicClient,
                    onGenreClick = { g -> navController.navigate(AlbumsListRoute("byGenre", g, genre = g)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<SongsRoute> {
                SongsScreen(client = appState.subsonicClient, onNavigateBack = { navController.popBackStack() })
            }
            composable<GenerationsRoute> {
                GenerationsScreen(
                    onDecadeClick = { f, t, n -> navController.navigate(AlbumsListRoute("byYear", n, fromYear = f, toYear = t)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<FavoritesRoute> {
                FavoritesScreen(
                    client = appState.subsonicClient,
                    onArtistClick = { navController.navigate(ArtistDetailRoute(it)) },
                    onAlbumClick = { navController.navigate(AlbumDetailRoute(it)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<FolderBrowserRoute> {
                FolderBrowserScreen(
                    client = appState.subsonicClient,
                    onFolderClick = { id, name -> navController.navigate(FolderDetailRoute(id, name)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<FolderDetailRoute> { e ->
                val r = e.toRoute<FolderDetailRoute>()
                FolderDetailScreen(
                    directoryId = r.directoryId,
                    title = r.title,
                    client = appState.subsonicClient,
                    onFolderClick = { id, name -> navController.navigate(FolderDetailRoute(id, name)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<DownloadsRoute> {
                DownloadsScreen(onNavigateBack = { navController.popBackStack() })
            }

            // Playlists
            composable<PlaylistsRoute> {
                PlaylistsScreen(
                    client = appState.subsonicClient,
                    onPlaylistClick = { navController.navigate(PlaylistDetailRoute(it)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<PlaylistDetailRoute> { e ->
                PlaylistDetailScreen(
                    playlistId = e.toRoute<PlaylistDetailRoute>().playlistId,
                    client = appState.subsonicClient,
                    onEditPlaylist = { navController.navigate(PlaylistEditorRoute(it)) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable<PlaylistEditorRoute> { e ->
                PlaylistEditorScreen(
                    e.toRoute<PlaylistEditorRoute>().playlistId,
                    appState.subsonicClient,
                ) { navController.popBackStack() }
            }
            composable<SmartPlaylistRoute> {
                SmartPlaylistScreen(appState.subsonicClient) { navController.popBackStack() }
            }

            // Radio
            composable<RadioRoute> {
                RadioScreen(
                    client = appState.subsonicClient,
                    onNavigateBack = { navController.popBackStack() },
                    onSearchStations = { navController.navigate(StationSearchRoute) },
                    onAddStation = { navController.navigate(AddStationRoute) },
                )
            }
            composable<StationSearchRoute> {
                StationSearchScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<AddStationRoute> {
                AddStationScreen(
                    client = appState.subsonicClient,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            // Settings
            composable<SettingsRoute> {
                SettingsScreen(appState, { navController.popBackStack() },
                    { navController.navigate(ServerManagerRoute) },
                    { navController.navigate(EQRoute) },
                    { navController.navigate(ServerConfigRoute) { popUpTo(0) { inclusive = true } } })
            }
            composable<ServerManagerRoute> {
                ServerManagerScreen(appState) { navController.popBackStack() }
            }

            // Player
            composable<NowPlayingRoute> {
                NowPlayingScreen(
                    playbackManager = playbackManager,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToQueue = { navController.navigate(QueueRoute) },
                    onNavigateToEQ = { navController.navigate(EQRoute) },
                    onNavigateToLyrics = { navController.navigate(LyricsRoute) },
                    onNavigateToAlbum = { navController.navigate(AlbumDetailRoute(it)) },
                    onNavigateToArtist = { navController.navigate(ArtistDetailRoute(it)) },
                )
            }
            composable<QueueRoute> {
                QueueScreen(playbackManager) { navController.popBackStack() }
            }
            composable<EQRoute> { EQScreen { navController.popBackStack() } }
            composable<LyricsRoute> {
                LyricsScreen(playbackManager, appState.subsonicClient) { navController.popBackStack() }
            }

            // Search
            composable<SearchRoute> {
                SearchScreen(appState.subsonicClient,
                    { navController.navigate(ArtistDetailRoute(it)) },
                    { navController.navigate(AlbumDetailRoute(it)) },
                    { navController.popBackStack() })
            }
        }
    }
}

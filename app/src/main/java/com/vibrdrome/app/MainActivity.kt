package com.vibrdrome.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vibrdrome.app.network.AlbumListType
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.ui.library.AlbumDetailScreen
import com.vibrdrome.app.ui.library.AlbumsScreen
import com.vibrdrome.app.ui.library.ArtistDetailScreen
import com.vibrdrome.app.ui.library.ArtistsScreen
import com.vibrdrome.app.ui.library.LibraryScreen
import com.vibrdrome.app.ui.navigation.AlbumDetailRoute
import com.vibrdrome.app.ui.navigation.AlbumsListRoute
import com.vibrdrome.app.ui.navigation.ArtistDetailRoute
import com.vibrdrome.app.ui.navigation.ArtistsRoute
import com.vibrdrome.app.ui.navigation.LibraryRoute
import com.vibrdrome.app.ui.navigation.ServerConfigRoute
import com.vibrdrome.app.ui.settings.ServerConfigScreen
import com.vibrdrome.app.ui.theme.VibrdromeAppTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val appState: AppState by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibrdromeAppTheme {
                VibrdromeNavHost(appState)
            }
        }
    }
}

@Composable
private fun VibrdromeNavHost(appState: AppState) {
    val isConfigured by appState.isConfigured.collectAsState()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (isConfigured) LibraryRoute else ServerConfigRoute,
    ) {
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

        composable<LibraryRoute> {
            LibraryScreen(
                client = appState.subsonicClient,
                onNavigateToArtists = { navController.navigate(ArtistsRoute) },
                onNavigateToAlbums = { listType, title ->
                    navController.navigate(AlbumsListRoute(listType, title))
                },
                onNavigateToAlbumDetail = { albumId ->
                    navController.navigate(AlbumDetailRoute(albumId))
                },
            )
        }

        composable<ArtistsRoute> {
            ArtistsScreen(
                client = appState.subsonicClient,
                onArtistClick = { artistId ->
                    navController.navigate(ArtistDetailRoute(artistId))
                },
            )
        }

        composable<ArtistDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ArtistDetailRoute>()
            ArtistDetailScreen(
                artistId = route.artistId,
                client = appState.subsonicClient,
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetailRoute(albumId))
                },
            )
        }

        composable<AlbumDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AlbumDetailRoute>()
            AlbumDetailScreen(
                albumId = route.albumId,
                client = appState.subsonicClient,
            )
        }

        composable<AlbumsListRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AlbumsListRoute>()
            val listType = AlbumListType.entries.find { it.value == route.listType }
                ?: AlbumListType.NEWEST
            AlbumsScreen(
                listType = listType,
                title = route.title,
                genre = route.genre,
                fromYear = route.fromYear,
                toYear = route.toYear,
                client = appState.subsonicClient,
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetailRoute(albumId))
                },
            )
        }
    }
}

package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.network.Album
import com.vibrdrome.app.network.AlbumListType
import com.vibrdrome.app.network.SubsonicClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.theme.VibrdromeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    client: SubsonicClient,
    onNavigateToArtists: () -> Unit,
    onNavigateToAlbums: (listType: String, title: String) -> Unit,
    onNavigateToAlbumDetail: (albumId: String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSongs: () -> Unit = {},
    onNavigateToGenerations: () -> Unit = {},
    onNavigateToGenres: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToRadio: () -> Unit = {},
    onNavigateToFolders: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
) {
    val playbackManager: PlaybackManager = koinInject()
    val scope = rememberCoroutineScope()
    var recentAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var frequentAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var randomAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }

    LaunchedEffect(Unit) {
        // Load cached first, then refresh
        try {
            val cached = client.cachedResponse(
                com.vibrdrome.app.network.SubsonicEndpoint.GetAlbumList2(AlbumListType.NEWEST, pageSize = 10),
                ttlMs = 300_000,
            )
            if (cached != null) recentAlbums = cached.albumList2?.album ?: emptyList()
        } catch (_: Exception) {}

        try { recentAlbums = client.getAlbumList(AlbumListType.NEWEST, size = 10) } catch (_: Exception) {}
        try { frequentAlbums = client.getAlbumList(AlbumListType.FREQUENT, size = 10) } catch (_: Exception) {}
        try { randomAlbums = client.getAlbumList(AlbumListType.RANDOM, size = 10) } catch (_: Exception) {}
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        var isRefreshing by remember { mutableStateOf(false) }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    try { recentAlbums = client.getAlbumList(AlbumListType.NEWEST, size = 10) } catch (_: Exception) {}
                    try { frequentAlbums = client.getAlbumList(AlbumListType.FREQUENT, size = 10) } catch (_: Exception) {}
                    try { randomAlbums = client.getAlbumList(AlbumListType.RANDOM, size = 10) } catch (_: Exception) {}
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding),
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
        ) {
            // Quick access pills
            QuickAccessGrid(
                onNavigateToArtists = onNavigateToArtists,
                onNavigateToAlbums = onNavigateToAlbums,
                onNavigateToSongs = onNavigateToSongs,
                onNavigateToGenerations = onNavigateToGenerations,
                onNavigateToGenres = onNavigateToGenres,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToPlaylists = onNavigateToPlaylists,
                onNavigateToRadio = onNavigateToRadio,
                onNavigateToFolders = onNavigateToFolders,
                onNavigateToDownloads = onNavigateToDownloads,
                onRandomMix = {
                    scope.launch {
                        try {
                            val songs = client.getRandomSongs(size = 50)
                            if (songs.isNotEmpty()) playbackManager.playShuffle(songs)
                        } catch (_: Throwable) {}
                    }
                },
                onRandomAlbum = {
                    scope.launch {
                        try {
                            val albums = client.getAlbumList(
                                com.vibrdrome.app.network.AlbumListType.RANDOM, size = 1
                            )
                            val album = albums.firstOrNull() ?: return@launch
                            val full = client.getAlbum(album.id)
                            val songs = full.song ?: return@launch
                            if (songs.isNotEmpty()) playbackManager.play(songs)
                        } catch (_: Throwable) {}
                    }
                },
            )

            Spacer(Modifier.height(20.dp))

            // Recently Added
            if (recentAlbums.isNotEmpty()) {
                AlbumSection(
                    title = "Recently Added",
                    albums = recentAlbums,
                    client = client,
                    onSeeAll = { onNavigateToAlbums("newest", "Recently Added") },
                    onAlbumClick = onNavigateToAlbumDetail,
                )
            }

            // Most Played
            if (frequentAlbums.isNotEmpty()) {
                AlbumSection(
                    title = "Most Played",
                    albums = frequentAlbums,
                    client = client,
                    onSeeAll = { onNavigateToAlbums("frequent", "Most Played") },
                    onAlbumClick = onNavigateToAlbumDetail,
                )
            }

            // Random Picks
            if (randomAlbums.isNotEmpty()) {
                AlbumSection(
                    title = "Random Picks",
                    albums = randomAlbums,
                    client = client,
                    onSeeAll = { onNavigateToAlbums("random", "Random") },
                    onAlbumClick = onNavigateToAlbumDetail,
                )
            }

            Spacer(Modifier.height(80.dp)) // Bottom padding for mini player
        }
        }
    }
}

@Composable
private fun QuickAccessGrid(
    onNavigateToArtists: () -> Unit,
    onNavigateToAlbums: (listType: String, title: String) -> Unit,
    onNavigateToSongs: () -> Unit,
    onNavigateToGenerations: () -> Unit,
    onNavigateToGenres: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToRadio: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onRandomMix: () -> Unit,
    onRandomAlbum: () -> Unit,
) {
    // Left column: Genres, Artists, Albums, Songs, Playlists, Generations, Random Mix
    // Right column: Radio, Favorites, Folders, Downloads, Recently Added, Recently Played, Random Album
    val pills = listOf(
        // Row 1
        QuickAccessItem("Genres", Icons.Default.Category, Color(0xFF4CAF50), onNavigateToGenres),
        QuickAccessItem("Radio", Icons.Default.Radio, Color(0xFFFF5722), onNavigateToRadio),
        // Row 2
        QuickAccessItem("Artists", Icons.Default.Person, Color(0xFF9C27B0), onNavigateToArtists),
        QuickAccessItem("Favorites", Icons.Default.Favorite, Color(0xFFE91E63), onNavigateToFavorites),
        // Row 3
        QuickAccessItem("Albums", Icons.Default.Album, Color(0xFF2196F3)) {
            onNavigateToAlbums("alphabeticalByName", "Albums")
        },
        QuickAccessItem("Folders", Icons.Default.Folder, Color(0xFF795548), onNavigateToFolders),
        // Row 4
        QuickAccessItem("Songs", Icons.Default.MusicNote, Color(0xFF673AB7), onNavigateToSongs),
        QuickAccessItem("Downloads", Icons.Default.DownloadForOffline, Color(0xFF607D8B), onNavigateToDownloads),
        // Row 5
        QuickAccessItem("Playlists", Icons.AutoMirrored.Filled.QueueMusic, Color(0xFF009688), onNavigateToPlaylists),
        QuickAccessItem("Recently Added", Icons.Default.AutoAwesome, Color(0xFFFFEB3B)) {
            onNavigateToAlbums("newest", "Recently Added")
        },
        // Row 6
        QuickAccessItem("Generations", Icons.Default.CalendarMonth, Color(0xFFF44336), onNavigateToGenerations),
        QuickAccessItem("Recently Played", Icons.Default.PlayCircle, Color(0xFF00BCD4)) {
            onNavigateToAlbums("recent", "Recently Played")
        },
        // Row 7
        QuickAccessItem("Random Mix", Icons.Default.Shuffle, Color(0xFFFF9800), onRandomMix),
        QuickAccessItem("Random Album", Icons.Default.Casino, Color(0xFF8BC34A), onRandomAlbum),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.height((((pills.size + 1) / 2) * 52).dp),
        userScrollEnabled = false,
    ) {
        items(pills.size) { index ->
            val pill = pills[index]
            QuickAccessPill(
                title = pill.title,
                icon = pill.icon,
                iconColor = pill.color,
                onClick = pill.onClick,
            )
        }
    }
}

private data class QuickAccessItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
)

@Composable
private fun QuickAccessPill(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlbumSection(
    title: String,
    albums: List<Album>,
    client: SubsonicClient,
    onSeeAll: () -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSeeAll) {
                Text("See All")
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumHeroCard(
                    album = album,
                    coverArtUrl = album.coverArt?.let { client.coverArtURL(it, size = 320) },
                    onClick = { onAlbumClick(album.id) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AlbumHeroCard(
    album: Album,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(VibrdromeTheme.albumCardSize)
            .clickable(onClick = onClick),
    ) {
        AlbumArtView(
            coverArtUrl = coverArtUrl,
            size = VibrdromeTheme.albumCardSize,
            cornerRadius = 10.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.vibrdrome.app.network.MusicFolder
import com.vibrdrome.app.network.SubsonicClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.ui.LibraryItem
import com.vibrdrome.app.ui.LibraryItemIds
import com.vibrdrome.app.ui.components.AlbumArtView
import com.vibrdrome.app.ui.theme.VibrdromeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    client: SubsonicClient,
    appState: AppState,
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
    onNavigateToStats: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
) {
    val playbackManager: PlaybackManager = koinInject()
    val scope = rememberCoroutineScope()
    var recentAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var frequentAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var randomAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val musicFolders by appState.musicFolders.collectAsState()
    val selectedFolderId by appState.selectedFolderId.collectAsState()
    val libraryLayout by appState.libraryLayout.collectAsState()
    var showFolderMenu by remember { mutableStateOf(false) }
    var showLayoutSheet by remember { mutableStateOf(false) }

    fun loadAlbums(folderId: String? = selectedFolderId) {
        scope.launch {
            try { recentAlbums = client.getAlbumList(AlbumListType.NEWEST, size = 10, musicFolderId = folderId) } catch (_: Exception) {}
            try { frequentAlbums = client.getAlbumList(AlbumListType.FREQUENT, size = 10, musicFolderId = folderId) } catch (_: Exception) {}
            try { randomAlbums = client.getAlbumList(AlbumListType.RANDOM, size = 10, musicFolderId = folderId) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(selectedFolderId) {
        // Load cached first, then refresh
        try {
            val cached = client.cachedResponse(
                com.vibrdrome.app.network.SubsonicEndpoint.GetAlbumList2(AlbumListType.NEWEST, pageSize = 10, musicFolderId = selectedFolderId),
                ttlMs = 300_000,
            )
            if (cached != null) recentAlbums = cached.albumList2?.album ?: emptyList()
        } catch (_: Exception) {}
        loadAlbums()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Library") },
                actions = {
                    if (musicFolders.size > 1) {
                        IconButton(onClick = { showFolderMenu = true }) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = "Switch Library")
                        }
                        DropdownMenu(
                            expanded = showFolderMenu,
                            onDismissRequest = { showFolderMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "All Libraries",
                                        fontWeight = if (selectedFolderId == null) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    appState.selectMusicFolder(null)
                                    showFolderMenu = false
                                },
                            )
                            musicFolders.forEach { folder ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            folder.name ?: folder.id,
                                            fontWeight = if (selectedFolderId == folder.id) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        appState.selectMusicFolder(folder.id)
                                        showFolderMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showLayoutSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Customize Layout")
                    }
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
                    loadAlbums()
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding),
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState()),
        ) {
            // Build pill definitions keyed by ID
            val pillDefs = mapOf(
                LibraryItemIds.GENRES to QuickAccessItem("Genres", Icons.Default.Category, Color(0xFF4CAF50), onNavigateToGenres),
                LibraryItemIds.RADIO to QuickAccessItem("Radio", Icons.Default.Radio, Color(0xFFFF5722), onNavigateToRadio),
                LibraryItemIds.ARTISTS to QuickAccessItem("Artists", Icons.Default.Person, Color(0xFF9C27B0), onNavigateToArtists),
                LibraryItemIds.FAVORITES to QuickAccessItem("Favorites", Icons.Default.Favorite, Color(0xFFE91E63), onNavigateToFavorites),
                LibraryItemIds.ALBUMS to QuickAccessItem("Albums", Icons.Default.Album, Color(0xFF2196F3)) { onNavigateToAlbums("alphabeticalByName", "Albums") },
                LibraryItemIds.FOLDERS to QuickAccessItem("Folders", Icons.Default.Folder, Color(0xFF795548), onNavigateToFolders),
                LibraryItemIds.SONGS to QuickAccessItem("Songs", Icons.Default.MusicNote, Color(0xFF673AB7), onNavigateToSongs),
                LibraryItemIds.DOWNLOADS to QuickAccessItem("Downloads", Icons.Default.DownloadForOffline, Color(0xFF607D8B), onNavigateToDownloads),
                LibraryItemIds.STATS to QuickAccessItem("Stats", Icons.Default.TrendingUp, Color(0xFF3F51B5), onNavigateToStats),
                LibraryItemIds.BOOKMARKS to QuickAccessItem("Bookmarks", Icons.Default.Bookmark, Color(0xFF00BCD4), onNavigateToBookmarks),
                LibraryItemIds.PLAYLISTS to QuickAccessItem("Playlists", Icons.AutoMirrored.Filled.QueueMusic, Color(0xFF009688), onNavigateToPlaylists),
                LibraryItemIds.RECENTLY_ADDED to QuickAccessItem("Recently Added", Icons.Default.AutoAwesome, Color(0xFFFFEB3B)) { onNavigateToAlbums("newest", "Recently Added") },
                LibraryItemIds.GENERATIONS to QuickAccessItem("Generations", Icons.Default.CalendarMonth, Color(0xFFF44336), onNavigateToGenerations),
                LibraryItemIds.RECENTLY_PLAYED to QuickAccessItem("Recently Played", Icons.Default.PlayCircle, Color(0xFF00BCD4)) { onNavigateToAlbums("recent", "Recently Played") },
                LibraryItemIds.RANDOM_MIX to QuickAccessItem("Random Mix", Icons.Default.Shuffle, Color(0xFFFF9800)) {
                    scope.launch {
                        try {
                            val songs = client.getRandomSongs(size = 50, musicFolderId = selectedFolderId)
                            if (songs.isNotEmpty()) playbackManager.playShuffle(songs)
                        } catch (_: Throwable) {}
                    }
                },
                LibraryItemIds.RANDOM_ALBUM to QuickAccessItem("Random Album", Icons.Default.Casino, Color(0xFF8BC34A)) {
                    scope.launch {
                        try {
                            val albums = client.getAlbumList(AlbumListType.RANDOM, size = 1, musicFolderId = selectedFolderId)
                            val album = albums.firstOrNull() ?: return@launch
                            val full = client.getAlbum(album.id)
                            val songs = full.song ?: return@launch
                            if (songs.isNotEmpty()) playbackManager.play(songs)
                        } catch (_: Throwable) {}
                    }
                },
            )

            // Visible pills in user-defined order
            val visiblePills = libraryLayout
                .filter { it.visible && !LibraryItemIds.isCarousel(it.id) }
                .mapNotNull { pillDefs[it.id] }

            if (visiblePills.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height((((visiblePills.size + 1) / 2) * 52).dp),
                    userScrollEnabled = false,
                ) {
                    items(visiblePills.size) { index ->
                        val pill = visiblePills[index]
                        QuickAccessPill(pill.title, pill.icon, pill.color, pill.onClick)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Carousels in user-defined order
            val visibleCarousels = libraryLayout
                .filter { it.visible && LibraryItemIds.isCarousel(it.id) }

            for (carousel in visibleCarousels) {
                when (carousel.id) {
                    LibraryItemIds.CAROUSEL_RECENT -> if (recentAlbums.isNotEmpty()) {
                        AlbumSection("Recently Added", recentAlbums, client,
                            onSeeAll = { onNavigateToAlbums("newest", "Recently Added") },
                            onAlbumClick = onNavigateToAlbumDetail)
                    }
                    LibraryItemIds.CAROUSEL_FREQUENT -> if (frequentAlbums.isNotEmpty()) {
                        AlbumSection("Most Played", frequentAlbums, client,
                            onSeeAll = { onNavigateToAlbums("frequent", "Most Played") },
                            onAlbumClick = onNavigateToAlbumDetail)
                    }
                    LibraryItemIds.CAROUSEL_RANDOM -> if (randomAlbums.isNotEmpty()) {
                        AlbumSection("Random Picks", randomAlbums, client,
                            onSeeAll = { onNavigateToAlbums("random", "Random") },
                            onAlbumClick = onNavigateToAlbumDetail)
                    }
                }
            }

            Spacer(Modifier.height(80.dp)) // Bottom padding for mini player
        }
        }
    }

    if (showLayoutSheet) {
        LayoutCustomizationSheet(
            items = libraryLayout,
            onDismiss = { showLayoutSheet = false },
            onUpdate = { appState.updateLibraryLayout(it) },
            onReset = { appState.resetLibraryLayout() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutCustomizationSheet(
    items: List<LibraryItem>,
    onDismiss: () -> Unit,
    onUpdate: (List<LibraryItem>) -> Unit,
    onReset: () -> Unit,
) {
    var editItems by remember(items) { mutableStateOf(items) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // Fixed header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    "Customize Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onReset()
                    onDismiss()
                }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
            // Section header: Pills
            Text(
                "Shortcuts",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val pillItems = editItems.filter { !LibraryItemIds.isCarousel(it.id) }
            pillItems.forEachIndexed { idx, item ->
                LayoutItemRow(
                    name = LibraryItemIds.displayName(item.id),
                    visible = item.visible,
                    canMoveUp = idx > 0,
                    canMoveDown = idx < pillItems.size - 1,
                    onToggle = { checked ->
                        editItems = editItems.map { if (it.id == item.id) it.copy(visible = checked) else it }
                        onUpdate(editItems)
                    },
                    onMoveUp = {
                        val fullIdx = editItems.indexOf(item)
                        val prevPillIdx = editItems.indexOfLast { it == pillItems[idx - 1] }
                        editItems = editItems.toMutableList().apply {
                            removeAt(fullIdx)
                            add(prevPillIdx, item)
                        }
                        onUpdate(editItems)
                    },
                    onMoveDown = {
                        val fullIdx = editItems.indexOf(item)
                        val nextPillIdx = editItems.indexOfLast { it == pillItems[idx + 1] }
                        editItems = editItems.toMutableList().apply {
                            removeAt(fullIdx)
                            add(nextPillIdx, item)
                        }
                        onUpdate(editItems)
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Section header: Carousels
            Text(
                "Carousels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val carouselItems = editItems.filter { LibraryItemIds.isCarousel(it.id) }
            carouselItems.forEachIndexed { idx, item ->
                LayoutItemRow(
                    name = LibraryItemIds.displayName(item.id),
                    visible = item.visible,
                    canMoveUp = idx > 0,
                    canMoveDown = idx < carouselItems.size - 1,
                    onToggle = { checked ->
                        editItems = editItems.map { if (it.id == item.id) it.copy(visible = checked) else it }
                        onUpdate(editItems)
                    },
                    onMoveUp = {
                        val fullIdx = editItems.indexOf(item)
                        val prevIdx = editItems.indexOfLast { it == carouselItems[idx - 1] }
                        editItems = editItems.toMutableList().apply {
                            removeAt(fullIdx)
                            add(prevIdx, item)
                        }
                        onUpdate(editItems)
                    },
                    onMoveDown = {
                        val fullIdx = editItems.indexOf(item)
                        val nextIdx = editItems.indexOfLast { it == carouselItems[idx + 1] }
                        editItems = editItems.toMutableList().apply {
                            removeAt(fullIdx)
                            add(nextIdx, item)
                        }
                        onUpdate(editItems)
                    },
                )
            }
            } // end scrollable Column
        }
    }
}

@Composable
private fun LayoutItemRow(
    name: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Column {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = visible,
            onCheckedChange = onToggle,
        )
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

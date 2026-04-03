package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.vibrdrome.app.network.Album
import com.vibrdrome.app.network.AlbumListType
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.network.SubsonicError
import com.vibrdrome.app.ui.AppState
import com.vibrdrome.app.ui.components.AlbumCard
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    listType: AlbumListType,
    title: String,
    genre: String? = null,
    fromYear: Int? = null,
    toYear: Int? = null,
    client: SubsonicClient,
    onAlbumClick: (albumId: String) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val appState: AppState = koinInject()
    val folderId by appState.selectedFolderId.collectAsState()
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(true) }
    val pageSize = 40
    val listState = rememberLazyListState()

    // Infinite scroll trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= albums.size - 5 && hasMore && !isLoading
        }
    }

    LaunchedEffect(folderId) {
        isLoading = true
        try {
            val result = client.getAlbumList(listType, size = pageSize, genre = genre, fromYear = fromYear, toYear = toYear, musicFolderId = folderId)
            albums = result
            hasMore = result.size >= pageSize
        } catch (e: Throwable) {
            error = SubsonicError.userMessage(e)
        }
        isLoading = false
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            isLoading = true
            try {
                val result = client.getAlbumList(
                    listType,
                    size = pageSize,
                    offset = albums.size,
                    genre = genre,
                    fromYear = fromYear,
                    toYear = toYear,
                    musicFolderId = folderId,
                )
                albums = albums + result
                hasMore = result.size >= pageSize
            } catch (_: Throwable) {
                hasMore = false
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                isLoading && albums.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                error != null && albums.isEmpty() -> {
                    Text(
                        text = error ?: "",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )
                }
                else -> {
                    LazyColumn(state = listState) {
                        items(albums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                coverArtUrl = album.coverArt?.let { client.coverArtURL(it, size = 112) },
                                modifier = Modifier
                                    .clickable { onAlbumClick(album.id) }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        if (isLoading && albums.isNotEmpty()) {
                            item {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

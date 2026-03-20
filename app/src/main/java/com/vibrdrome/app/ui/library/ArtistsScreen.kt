package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.network.Artist
import com.vibrdrome.app.network.ArtistIndex
import com.vibrdrome.app.network.SubsonicClient
import com.vibrdrome.app.network.SubsonicError
import com.vibrdrome.app.ui.components.AlbumArtView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    client: SubsonicClient,
    onArtistClick: (artistId: String) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    var indexes by remember { mutableStateOf<List<ArtistIndex>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Try cache first
        try {
            val cached = client.cachedResponse(
                com.vibrdrome.app.network.SubsonicEndpoint.GetArtists(),
                ttlMs = 1_800_000,
            )
            if (cached != null) indexes = cached.artists?.index ?: emptyList()
        } catch (_: Exception) {}

        isLoading = indexes.isEmpty()
        try {
            indexes = client.getArtists()
        } catch (e: Throwable) {
            if (indexes.isEmpty()) error = SubsonicError.userMessage(e)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Artists") },
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
            if (isLoading && indexes.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (error != null && indexes.isEmpty()) {
                Text(
                    text = error ?: "",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            } else {
                LazyColumn {
                    indexes.forEach { index ->
                        item(key = "header_${index.name}") {
                            Text(
                                text = index.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(index.artist ?: emptyList(), key = { it.id }) { artist ->
                            ArtistRow(
                                artist = artist,
                                coverArtUrl = artist.coverArt?.let { client.coverArtURL(it, size = 88) },
                                onClick = { onArtistClick(artist.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(
    artist: Artist,
    coverArtUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AlbumArtView(coverArtUrl = coverArtUrl, size = 44.dp, cornerRadius = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            artist.albumCount?.let { count ->
                Text(
                    text = "$count album${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

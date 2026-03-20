package com.vibrdrome.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import com.vibrdrome.app.network.Genre
import com.vibrdrome.app.network.SubsonicClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenresScreen(
    client: SubsonicClient,
    onGenreClick: (genre: String) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            genres = client.getGenres().sortedBy { it.value }
        } catch (_: Throwable) {}
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Genres") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(genres, key = { it.value }) { genre ->
                    ListItem(
                        headlineContent = { Text(genre.value) },
                        supportingContent = {
                            val meta = buildList {
                                genre.albumCount?.let { add("$it albums") }
                                genre.songCount?.let { add("$it songs") }
                            }
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "))
                        },
                        modifier = Modifier.clickable { onGenreClick(genre.value) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

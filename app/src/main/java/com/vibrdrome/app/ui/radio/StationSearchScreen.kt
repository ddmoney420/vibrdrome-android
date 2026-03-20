package com.vibrdrome.app.ui.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.PlaybackManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject

@Serializable
private data class RadioBrowserStation(
    val name: String = "",
    val url: String = "",
    val url_resolved: String = "",
    val homepage: String = "",
    val favicon: String = "",
    val country: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSearchScreen(
    onNavigateBack: () -> Unit,
) {
    val playbackManager: PlaybackManager = koinInject()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RadioBrowserStation>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val json = remember { Json { ignoreUnknownKeys = true } }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        delay(500)
        try {
            val client = HttpClient(Android)
            val response = client.get(
                "https://de1.api.radio-browser.info/json/stations/byname/$query?limit=30&order=clickcount&reverse=true"
            )
            results = json.decodeFromString(response.bodyAsText())
        } catch (_: Throwable) {
            results = emptyList()
        }
        isSearching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search radio stations...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isSearching && results.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                results.isNotEmpty() -> {
                    LazyColumn {
                        items(results) { station ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val url = station.url_resolved.ifEmpty { station.url }
                                        playbackManager.playRadioStream(station.name, url)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = station.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val meta = buildList {
                                        if (station.country.isNotEmpty()) add(station.country)
                                        if (station.codec.isNotEmpty()) add(station.codec)
                                        if (station.bitrate > 0) add("${station.bitrate}kbps")
                                    }
                                    if (meta.isNotEmpty()) {
                                        Text(
                                            text = meta.joinToString(" \u00b7 "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    val url = station.url_resolved.ifEmpty { station.url }
                                    playbackManager.playRadioStream(station.name, url)
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                }
                            }
                            HorizontalDivider()
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
                query.isEmpty() -> {
                    Text(
                        text = "Search radio-browser.info for stations",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

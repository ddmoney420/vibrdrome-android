package com.vibrdrome.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.persistence.AlbumPlayCount
import com.vibrdrome.app.persistence.ArtistPlayCount
import com.vibrdrome.app.persistence.DayActivity
import com.vibrdrome.app.persistence.GenrePlayCount
import com.vibrdrome.app.persistence.ListeningStatsDao
import com.vibrdrome.app.persistence.SkippedSong
import org.koin.compose.koinInject

private enum class TimePeriod(val label: String, val daysBack: Int) {
    WEEK("This Week", 7),
    MONTH("This Month", 30),
    ALL_TIME("All Time", 3650),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onNavigateBack: () -> Unit) {
    val dao: ListeningStatsDao = koinInject()
    var selectedTab by remember { mutableIntStateOf(0) }
    val period = TimePeriod.entries[selectedTab]
    val sinceMs = System.currentTimeMillis() - period.daysBack * 86_400_000L

    // Stats data
    var topArtists by remember { mutableStateOf<List<ArtistPlayCount>>(emptyList()) }
    var topAlbums by remember { mutableStateOf<List<AlbumPlayCount>>(emptyList()) }
    var topGenres by remember { mutableStateOf<List<GenrePlayCount>>(emptyList()) }
    var totalMs by remember { mutableStateOf(0L) }
    var uniqueTracks by remember { mutableStateOf(0) }
    var activeDays by remember { mutableStateOf(0) }
    var totalSessions by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf(0) }
    var mostSkipped by remember { mutableStateOf<List<SkippedSong>>(emptyList()) }
    var heatmap by remember { mutableStateOf<List<DayActivity>>(emptyList()) }

    LaunchedEffect(selectedTab) {
        topArtists = dao.topArtists(sinceMs)
        topAlbums = dao.topAlbums(sinceMs)
        topGenres = dao.topGenres(sinceMs)
        totalMs = dao.totalListeningMs(sinceMs) ?: 0
        uniqueTracks = dao.uniqueTracksPlayed(sinceMs)
        activeDays = dao.activeDays(sinceMs)
        totalSessions = dao.totalSessions(sinceMs)
        streak = calculateStreak(dao)
        mostSkipped = dao.mostSkippedSongIds(sinceMs)
        heatmap = dao.activityHeatmap(sinceMs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listening Stats") },
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
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                TimePeriod.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Summary cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard("Hours", "%.1f".format(totalMs / 3_600_000.0), Modifier.weight(1f))
                    StatCard("Tracks", "$uniqueTracks", Modifier.weight(1f))
                    StatCard("Days", "$activeDays", Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard("Sessions", "$totalSessions", Modifier.weight(1f))
                    StatCard("Streak", "$streak day${if (streak != 1) "s" else ""}", Modifier.weight(1f))
                    StatCard("Avg/Day", "%.1f hr".format(
                        if (activeDays > 0) totalMs / 3_600_000.0 / activeDays else 0.0
                    ), Modifier.weight(1f))
                }

                // Top Artists
                if (topArtists.isNotEmpty()) {
                    Text("Top Artists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    topArtists.forEachIndexed { i, artist ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${i + 1}. ${artist.artistName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${artist.playCount} plays | ${"%.1f".format(artist.totalDurationMs / 3_600_000.0)}h",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Top Albums
                if (topAlbums.isNotEmpty()) {
                    Text("Top Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    topAlbums.forEachIndexed { i, album ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${i + 1}. ${album.albumName}", style = MaterialTheme.typography.bodyMedium)
                                album.artistName?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                "${album.playCount} plays",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Top Genres
                if (topGenres.isNotEmpty()) {
                    Text("Top Genres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    topGenres.forEach { genre ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(genre.genre, style = MaterialTheme.typography.bodyMedium)
                            Text("${genre.playCount}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Most Skipped
                if (mostSkipped.isNotEmpty()) {
                    Text("Most Skipped", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    mostSkipped.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(s.artistName ?: s.song_id, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${s.skipCount} skips", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Discovery Rate
                if (uniqueTracks > 0) {
                    Text("Discovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$uniqueTracks unique tracks played",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Activity Heatmap (text-based grid)
                if (heatmap.isNotEmpty()) {
                    Text("Activity Heatmap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    val maxCount = heatmap.maxOf { it.sessionCount }
                    Column {
                        for (day in 1..7) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(days[day - 1], style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(32.dp))
                                // Show 6 time blocks (4-hour chunks): 0-3, 4-7, 8-11, 12-15, 16-19, 20-23
                                for (block in 0..5) {
                                    val hourStart = block * 4
                                    val count = heatmap.filter { it.dayOfWeek == day && it.hourOfDay in hourStart until hourStart + 4 }
                                        .sumOf { it.sessionCount }
                                    val intensity = if (maxCount > 0) count.toFloat() / maxCount else 0f
                                    val color = MaterialTheme.colorScheme.primary.copy(alpha = (intensity * 0.8f + 0.1f).coerceIn(0.1f, 0.9f))
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.weight(1f).height(16.dp).padding(1.dp),
                                    ) {
                                        drawRect(color)
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                        ) {
                            for (label in listOf("12a", "4a", "8a", "12p", "4p", "8p")) {
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Empty state
                if (totalSessions == 0) {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "No listening data yet. Start playing some music!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private suspend fun calculateStreak(dao: ListeningStatsDao): Int {
    val days = dao.allActiveDays() // sorted descending
    if (days.isEmpty()) return 0

    var streak = 1
    for (i in 0 until days.size - 1) {
        try {
            val date1 = java.time.LocalDate.parse(days[i])
            val date2 = java.time.LocalDate.parse(days[i + 1])
            if (java.time.temporal.ChronoUnit.DAYS.between(date2, date1) == 1L) {
                streak++
            } else {
                break
            }
        } catch (_: Exception) {
            break
        }
    }
    return streak
}

package com.vibrdrome.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.downloads.DownloadManager

@Composable
fun DownloadBadge(
    songId: String,
    downloadManager: DownloadManager,
    modifier: Modifier = Modifier,
) {
    val activeDownloads by downloadManager.activeDownloads.collectAsState()
    val downloadProgress = activeDownloads[songId]
    var isDownloaded by remember(songId) { mutableStateOf(false) }

    LaunchedEffect(songId) {
        isDownloaded = downloadManager.isDownloaded(songId)
    }

    when {
        downloadProgress != null -> {
            CircularProgressIndicator(
                progress = { downloadProgress },
                modifier = modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        isDownloaded -> {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = "Downloaded",
                modifier = modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
        }
    }
}

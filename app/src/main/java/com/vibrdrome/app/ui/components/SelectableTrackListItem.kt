package com.vibrdrome.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.network.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableTrackListItem(
    song: Song,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelect: (String) -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = true,
    onGoToAlbum: ((String) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
) {
    if (isSelectionMode) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.combinedClickable(
                onClick = { onSelect(song.id) },
                onLongClick = onLongPress,
            ),
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelect(song.id) },
                modifier = Modifier.padding(start = 4.dp),
            )
            TrackListItem(
                song = song,
                onClick = { onSelect(song.id) },
                showTrackNumber = showTrackNumber,
                onGoToAlbum = onGoToAlbum,
                onGoToArtist = onGoToArtist,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(
            modifier = modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        ) {
            TrackListItem(
                song = song,
                onClick = onClick,
                showTrackNumber = showTrackNumber,
                onGoToAlbum = onGoToAlbum,
                onGoToArtist = onGoToArtist,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

package com.vibrdrome.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistMosaicArt(
    coverArtUrls: List<String>,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val halfSize = size / 2

    when {
        coverArtUrls.isEmpty() -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        coverArtUrls.size == 1 -> {
            AlbumArtView(
                coverArtUrl = coverArtUrls[0],
                size = size,
                cornerRadius = 8.dp,
                modifier = modifier,
            )
        }
        coverArtUrls.size < 4 -> {
            Row(modifier = modifier.size(size).clip(shape)) {
                AlbumArtView(coverArtUrl = coverArtUrls[0], size = halfSize, cornerRadius = 0.dp)
                AlbumArtView(coverArtUrl = coverArtUrls.getOrElse(1) { coverArtUrls[0] }, size = halfSize, cornerRadius = 0.dp)
            }
        }
        else -> {
            Column(modifier = modifier.size(size).clip(shape)) {
                Row {
                    AlbumArtView(coverArtUrl = coverArtUrls[0], size = halfSize, cornerRadius = 0.dp)
                    AlbumArtView(coverArtUrl = coverArtUrls[1], size = halfSize, cornerRadius = 0.dp)
                }
                Row {
                    AlbumArtView(coverArtUrl = coverArtUrls[2], size = halfSize, cornerRadius = 0.dp)
                    AlbumArtView(coverArtUrl = coverArtUrls[3], size = halfSize, cornerRadius = 0.dp)
                }
            }
        }
    }
}

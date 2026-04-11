package com.vibrdrome.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val LOSSLESS_FORMATS = setOf("flac", "alac", "wav", "aiff", "dsf", "dsd", "ape", "wv")

@Composable
fun FormatBadge(
    suffix: String?,
    bitRate: Int? = null,
    modifier: Modifier = Modifier,
) {
    if (suffix == null) return

    val isLossless = suffix.lowercase() in LOSSLESS_FORMATS
    val label = buildString {
        append(suffix.uppercase())
        if (bitRate != null && bitRate > 0) {
            append(" · ${bitRate}kbps")
        }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isLossless) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isLossless) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

package com.vibrdrome.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.ui.player.NowPlayingToolbarConfig
import com.vibrdrome.app.ui.player.ToolbarAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingToolbarEditor(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var actions by remember { mutableStateOf(NowPlayingToolbarConfig.load(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            itemsIndexed(actions.sortedBy { it.order }, key = { _, a -> a.id }) { _, action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Switch(
                        checked = action.visible,
                        onCheckedChange = { visible ->
                            actions = actions.map {
                                if (it.id == action.id) it.copy(visible = visible) else it
                            }
                            NowPlayingToolbarConfig.save(context, actions)
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

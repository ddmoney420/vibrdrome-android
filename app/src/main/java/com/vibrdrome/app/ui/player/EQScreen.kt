package com.vibrdrome.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.audio.EQEngine
import com.vibrdrome.app.audio.EQPresets
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQScreen(
    onNavigateBack: () -> Unit,
) {
    val eqEngine: EQEngine = koinInject()
    val gains by eqEngine.gains.collectAsState()
    val isEnabled by eqEngine.isEnabled.collectAsState()
    val presetName by eqEngine.currentPresetName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equalizer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { eqEngine.setEnabled(it) },
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Preset chips
            Text(
                text = presetName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(EQPresets.allPresets) { preset ->
                    FilterChip(
                        selected = presetName == preset.name,
                        onClick = { eqEngine.applyPreset(preset) },
                        label = { Text(preset.name) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // EQ sliders (vertical)
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                for (band in 0 until EQPresets.BAND_COUNT) {
                    EQBandSlider(
                        label = EQPresets.bandLabels[band],
                        gain = gains[band],
                        onGainChange = { eqEngine.setBandGain(band, it) },
                        enabled = isEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // dB scale labels
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                Text("+12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EQBandSlider(
    label: String,
    gain: Float,
    onGainChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        // Gain value
        Text(
            text = if (gain >= 0) "+${gain.toInt()}" else "${gain.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Vertical slider via rotation
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .width(48.dp),
        ) {
            Slider(
                value = gain,
                onValueChange = onGainChange,
                valueRange = EQPresets.MIN_GAIN..EQPresets.MAX_GAIN,
                enabled = enabled,
                modifier = Modifier
                    .width(200.dp)
                    .rotate(-90f),
            )
        }

        // Frequency label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
    }
}

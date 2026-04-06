package com.vibrdrome.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val allPresetsList by eqEngine.allPresets.collectAsState()
    val deviceName by eqEngine.currentDeviceName.collectAsState()
    val context = LocalContext.current

    // File picker for AutoEQ / APO import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@let
                val fileName = uri.lastPathSegment?.substringAfterLast("/")?.substringBeforeLast(".") ?: "Imported"
                val result = eqEngine.importProfile(content, fileName)
                if (result != null) {
                    Toast.makeText(context, "Imported: $result", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not parse EQ profile", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                    // Import AutoEQ / APO profile
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import EQ Profile")
                    }
                    // Save current as custom preset
                    IconButton(onClick = { eqEngine.saveCustomPreset(presetName.ifEmpty { "Custom" }) }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Preset")
                    }
                    Text(
                        if (isEnabled) "ON" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            // Current preset + device
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presetName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                deviceName?.let { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                eqEngine.saveDeviceProfile()
                                Toast.makeText(context, "Saved EQ for $name", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            // Preset chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(allPresetsList) { preset ->
                    FilterChip(
                        selected = presetName == preset.name,
                        onClick = { eqEngine.applyPreset(preset) },
                        label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // dB scale
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text("+12 dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0 dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-12 dB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Band sliders
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                for (band in 0 until EQPresets.BAND_COUNT) {
                    EQBand(
                        label = EQPresets.bandLabels[band],
                        gain = gains[band],
                        enabled = isEnabled,
                        onGainChange = { eqEngine.setBandGain(band, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EQBand(
    label: String,
    gain: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val disabled = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp),
    ) {
        // Gain value
        Text(
            text = if (gain >= 0) "+${gain.toInt()}" else "${gain.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) primary else disabled,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        // Custom vertical bar slider
        Box(
            modifier = Modifier
                .weight(1f)
                .width(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surface.copy(alpha = 0.5f))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val y = change.position.y
                        val height = size.height.toFloat()
                        val normalized = 1f - (y / height).coerceIn(0f, 1f)
                        val newGain = EQPresets.MIN_GAIN + normalized * (EQPresets.MAX_GAIN - EQPresets.MIN_GAIN)
                        onGainChange(newGain)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val range = EQPresets.MAX_GAIN - EQPresets.MIN_GAIN
                val normalized = (gain - EQPresets.MIN_GAIN) / range
                val centerY = h * 0.5f
                val knobY = h * (1f - normalized)

                // Track background
                drawRoundRect(
                    color = if (enabled) surface else disabled.copy(alpha = 0.2f),
                    cornerRadius = CornerRadius(16f),
                )

                // Center line
                drawLine(
                    color = if (enabled) primary.copy(alpha = 0.3f) else disabled,
                    start = Offset(4f, centerY),
                    end = Offset(w - 4f, centerY),
                    strokeWidth = 1.5f,
                )

                // Fill bar from center to current value
                val barColor = if (enabled) primary else disabled
                if (gain > 0) {
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.4f),
                        topLeft = Offset(6f, knobY),
                        size = Size(w - 12f, centerY - knobY),
                        cornerRadius = CornerRadius(8f),
                    )
                } else if (gain < 0) {
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.4f),
                        topLeft = Offset(6f, centerY),
                        size = Size(w - 12f, knobY - centerY),
                        cornerRadius = CornerRadius(8f),
                    )
                }

                // Knob
                drawRoundRect(
                    color = if (enabled) primary else disabled,
                    topLeft = Offset(4f, knobY - 8f),
                    size = Size(w - 8f, 16f),
                    cornerRadius = CornerRadius(8f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Frequency label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else disabled,
        )
    }
}

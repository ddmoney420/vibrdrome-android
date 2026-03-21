package com.vibrdrome.app.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.visualizer.ProjectMBridge
import com.vibrdrome.app.visualizer.ProjectMRenderer
import com.vibrdrome.app.visualizer.ShaderPresets
import com.vibrdrome.app.visualizer.VisualizerRenderer
import kotlin.math.abs

@Composable
fun VisualizerScreen(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val isPlaying by playbackManager.isPlaying.collectAsState()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Audio permission needed for visualizer",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                androidx.compose.material3.Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                ) { Text("Grant Permission") }
                IconButton(onClick = onNavigateBack, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
        return
    }

    // Mode: false = custom shaders, true = projectM Milkdrop
    var useMilkdrop by remember { mutableStateOf(false) }

    if (useMilkdrop) {
        MilkdropVisualizer(playbackManager, onNavigateBack, onSwitchMode = { useMilkdrop = false })
    } else {
        CustomShaderVisualizer(playbackManager, onNavigateBack, onSwitchMode = { useMilkdrop = true })
    }
}

@Composable
private fun CustomShaderVisualizer(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val isPlaying by playbackManager.isPlaying.collectAsState()
    var presetIndex by remember { mutableIntStateOf(0) }
    val renderer = remember { VisualizerRenderer() }

    DisposableEffect(Unit) {
        val viz = setupVisualizer(playbackManager) { waveform, _, bass, mid, treble, energy ->
            renderer.waveformData = waveform
            renderer.energy = energy
            renderer.bass = bass
            renderer.mid = mid
            renderer.treble = treble
        }
        onDispose {
            try { viz?.enabled = false } catch (_: Exception) {}
            try { viz?.release() } catch (_: Exception) {}
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragAccumulator) > 100) {
                            val presets = ShaderPresets.allPresets
                            presetIndex = if (dragAccumulator > 0)
                                (presetIndex - 1 + presets.size) % presets.size
                            else
                                (presetIndex + 1) % presets.size
                            renderer.setPreset(presets[presetIndex])
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, d -> dragAccumulator += d },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        VisualizerOverlay(
            presetName = ShaderPresets.allPresets[presetIndex].name,
            modeName = "Vibrdrome",
            onClose = onNavigateBack,
            onSwitchMode = onSwitchMode,
        )
    }
}

@Composable
private fun MilkdropVisualizer(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val context = LocalContext.current
    val isPlaying by playbackManager.isPlaying.collectAsState()
    val bridge = remember { ProjectMBridge() }
    val presetPath = remember { bridge.extractPresets(context) }
    val renderer = remember { ProjectMRenderer(bridge, presetPath) }
    var presetName by remember { mutableStateOf("Milkdrop") }

    DisposableEffect(Unit) {
        val viz = setupVisualizer(playbackManager) { waveform, _, _, _, _, _ ->
            renderer.waveformData = waveform
        }
        onDispose {
            try { viz?.enabled = false } catch (_: Exception) {}
            try { viz?.release() } catch (_: Exception) {}
            renderer.release()
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragAccumulator) > 100) {
                            if (dragAccumulator > 0) renderer.previousPreset()
                            else renderer.nextPreset()
                            presetName = renderer.currentPresetName
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, d -> dragAccumulator += d },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    renderer.randomPreset()
                    presetName = renderer.currentPresetName
                })
            },
    ) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        VisualizerOverlay(
            presetName = presetName,
            modeName = "Milkdrop",
            onClose = onNavigateBack,
            onSwitchMode = onSwitchMode,
        )
    }
}

@Composable
private fun VisualizerOverlay(
    presetName: String,
    modeName: String,
    onClose: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
            }
            TextButton(onClick = onSwitchMode) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                Text(" $modeName", color = Color.White.copy(alpha = 0.7f))
            }
        }

        Text(
            text = presetName,
            color = Color.White.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        )
    }
}

private fun setupVisualizer(
    playbackManager: PlaybackManager,
    onData: (waveform: ByteArray, fft: ByteArray, bass: Float, mid: Float, treble: Float, energy: Float) -> Unit,
): Visualizer? {
    return try {
        Visualizer(playbackManager.player.audioSessionId).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(256)
            setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, sr: Int) {
                        data ?: return
                        val len = data.size
                        var totalE = 0f; var bassE = 0f; var midE = 0f; var trebleE = 0f
                        for (i in data.indices) {
                            val s = kotlin.math.abs(data[i].toFloat() - 128f) / 128f
                            totalE += s
                            when {
                                i < len / 4 -> bassE += s
                                i < len * 3 / 4 -> midE += s
                                else -> trebleE += s
                            }
                        }
                        onData(
                            data.copyOf(), ByteArray(0),
                            (bassE / (len / 4) * 1.5f).coerceIn(0f, 1f),
                            (midE / (len / 2)).coerceIn(0f, 1f),
                            (trebleE / (len / 4) * 2f).coerceIn(0f, 1f),
                            (totalE / len).coerceIn(0f, 1f),
                        )
                    }

                    override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, sr: Int) {}
                },
                Visualizer.getMaxCaptureRate(),
                true, false,
            )
            enabled = true
        }
    } catch (_: Exception) {
        null
    }
}

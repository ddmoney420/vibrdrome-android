package com.vibrdrome.app.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    var presetIndex by remember { mutableIntStateOf(0) }
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
                ) {
                    Text("Grant Permission")
                }
                IconButton(onClick = onNavigateBack, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
        return
    }

    val renderer = remember { VisualizerRenderer() }

    // Audio capture
    DisposableEffect(isPlaying) {
        val audioSessionId = playbackManager.player.audioSessionId
        val viz = try {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(256)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?, data: ByteArray?, samplingRate: Int
                        ) {
                            data ?: return
                            renderer.waveformData = data.copyOf()
                            // Compute energy bands
                            val len = data.size
                            var totalEnergy = 0f
                            var bassEnergy = 0f
                            var midEnergy = 0f
                            var trebleEnergy = 0f
                            for (i in data.indices) {
                                val v = abs(data[i].toFloat() - 128f) / 128f
                                totalEnergy += v
                                when {
                                    i < len / 4 -> bassEnergy += v
                                    i < len * 3 / 4 -> midEnergy += v
                                    else -> trebleEnergy += v
                                }
                            }
                            renderer.energy = (totalEnergy / len).coerceIn(0f, 1f)
                            renderer.bass = (bassEnergy / (len / 4) * 1.5f).coerceIn(0f, 1f)
                            renderer.mid = (midEnergy / (len / 2)).coerceIn(0f, 1f)
                            renderer.treble = (trebleEnergy / (len / 4) * 2f).coerceIn(0f, 1f)
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?, data: ByteArray?, samplingRate: Int
                        ) {}
                    },
                    Visualizer.getMaxCaptureRate(),
                    true,
                    false,
                )
                enabled = true
            }
        } catch (_: Exception) {
            null
        }

        onDispose {
            viz?.enabled = false
            viz?.release()
        }
    }

    // Swipe to change preset
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
                            presetIndex = if (dragAccumulator > 0) {
                                (presetIndex - 1 + presets.size) % presets.size
                            } else {
                                (presetIndex + 1) % presets.size
                            }
                            renderer.setPreset(presets[presetIndex])
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    },
                )
            },
    ) {
        // OpenGL surface
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

        // Close button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
        }

        // Preset name
        Text(
            text = ShaderPresets.allPresets[presetIndex].name,
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        )
    }
}

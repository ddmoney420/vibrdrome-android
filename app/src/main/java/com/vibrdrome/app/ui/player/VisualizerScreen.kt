package com.vibrdrome.app.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vibrdrome.app.audio.PlaybackManager
import kotlin.math.abs
import kotlin.math.sin

private val presetNames = listOf("Waveform", "Bars", "Circular", "Pulse", "Mirror Wave", "Aurora")

private val presetColors = listOf(
    listOf(Color(0xFF6750A4), Color(0xFF9C27B0), Color(0xFFE040FB)),
    listOf(Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF4CAF50)),
    listOf(Color(0xFFFF5722), Color(0xFFFF9800), Color(0xFFFFEB3B)),
    listOf(Color(0xFFE91E63), Color(0xFFF44336), Color(0xFFFF5722)),
    listOf(Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50)),
    listOf(Color(0xFF9C27B0), Color(0xFF3F51B5), Color(0xFF2196F3)),
)

@Composable
fun VisualizerScreen(
    playbackManager: PlaybackManager,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val isPlaying by playbackManager.isPlaying.collectAsState()
    var presetIndex by remember { mutableIntStateOf(0) }
    var waveform by remember { mutableStateOf(ByteArray(0)) }
    var fft by remember { mutableStateOf(ByteArray(0)) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Request permission if needed
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

    // Set up Visualizer
    DisposableEffect(isPlaying) {
        val audioSessionId = playbackManager.player.audioSessionId
        val visualizer = try {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?, data: ByteArray?, samplingRate: Int
                        ) {
                            data?.let { waveform = it.copyOf() }
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?, data: ByteArray?, samplingRate: Int
                        ) {
                            data?.let { fft = it.copyOf() }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true,
                )
                enabled = true
            }
        } catch (_: Exception) {
            null
        }

        onDispose {
            visualizer?.enabled = false
            visualizer?.release()
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
                            presetIndex = if (dragAccumulator > 0) {
                                (presetIndex - 1 + presetNames.size) % presetNames.size
                            } else {
                                (presetIndex + 1) % presetNames.size
                            }
                        }
                        dragAccumulator = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    },
                )
            },
    ) {
        val colors = presetColors[presetIndex]

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerY = h / 2

            when (presetIndex) {
                0 -> drawWaveform(waveform, w, h, colors)
                1 -> drawBars(fft, w, h, colors)
                2 -> drawCircular(waveform, w, h, colors)
                3 -> drawPulse(waveform, w, h, colors)
                4 -> drawMirrorWave(waveform, w, h, colors)
                5 -> drawAurora(waveform, fft, w, h, colors)
            }
        }

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
            text = presetNames[presetIndex],
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(
    data: ByteArray, w: Float, h: Float, colors: List<Color>,
) {
    if (data.isEmpty()) return
    val path = Path()
    val step = w / data.size
    data.forEachIndexed { i, b ->
        val x = i * step
        val y = h / 2 + (b.toFloat() / 128f) * h / 3
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Brush.horizontalGradient(colors), style = Stroke(width = 3f, cap = StrokeCap.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(
    data: ByteArray, w: Float, h: Float, colors: List<Color>,
) {
    if (data.size < 4) return
    val barCount = 32
    val barWidth = w / barCount * 0.7f
    val gap = w / barCount * 0.3f
    for (i in 0 until barCount) {
        val fftIndex = (i * data.size / barCount / 2).coerceIn(0, data.size - 1)
        val magnitude = abs(data[fftIndex].toFloat()) / 128f
        val barHeight = magnitude * h * 0.8f
        val x = i * (barWidth + gap)
        val color = colors[(i * colors.size / barCount).coerceIn(0, colors.lastIndex)]
        drawRect(
            color = color,
            topLeft = Offset(x, h - barHeight),
            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircular(
    data: ByteArray, w: Float, h: Float, colors: List<Color>,
) {
    if (data.isEmpty()) return
    val cx = w / 2; val cy = h / 2
    val baseRadius = minOf(w, h) * 0.25f
    val path = Path()
    data.forEachIndexed { i, b ->
        val angle = (i.toFloat() / data.size) * Math.PI.toFloat() * 2
        val r = baseRadius + (b.toFloat() / 128f) * baseRadius * 0.6f
        val x = cx + r * kotlin.math.cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, Brush.sweepGradient(colors, center = Offset(cx, cy)),
        style = Stroke(width = 2.5f, cap = StrokeCap.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPulse(
    data: ByteArray, w: Float, h: Float, colors: List<Color>,
) {
    if (data.isEmpty()) return
    val energy = data.map { abs(it.toInt()) }.average().toFloat() / 128f
    val cx = w / 2; val cy = h / 2
    val maxR = minOf(w, h) * 0.4f
    for (ring in 0..4) {
        val r = maxR * energy * (1f - ring * 0.15f)
        if (r > 5f) {
            drawCircle(
                color = colors[ring % colors.size].copy(alpha = 0.6f - ring * 0.1f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2f + ring),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMirrorWave(
    data: ByteArray, w: Float, h: Float, colors: List<Color>,
) {
    if (data.isEmpty()) return
    val step = w / data.size
    val topPath = Path()
    val bottomPath = Path()
    data.forEachIndexed { i, b ->
        val x = i * step
        val amp = (b.toFloat() / 128f) * h / 4
        val topY = h / 2 - amp
        val bottomY = h / 2 + amp
        if (i == 0) { topPath.moveTo(x, topY); bottomPath.moveTo(x, bottomY) }
        else { topPath.lineTo(x, topY); bottomPath.lineTo(x, bottomY) }
    }
    drawPath(topPath, Brush.horizontalGradient(colors), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    drawPath(bottomPath, Brush.horizontalGradient(colors.reversed()), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(
    waveform: ByteArray, fft: ByteArray, w: Float, h: Float, colors: List<Color>,
) {
    if (waveform.isEmpty()) return
    val energy = waveform.map { abs(it.toInt()) }.average().toFloat() / 128f
    // Layered aurora bands
    for (layer in 0..2) {
        val path = Path()
        val yBase = h * (0.3f + layer * 0.15f)
        for (x in 0..w.toInt() step 4) {
            val wIdx = (x * waveform.size / w.toInt()).coerceIn(0, waveform.size - 1)
            val amp = (waveform[wIdx].toFloat() / 128f) * h * 0.15f * energy
            val y = yBase + amp + sin(x * 0.02f + layer * 2f) * 30f * energy
            if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
        }
        drawPath(
            path,
            colors[layer % colors.size].copy(alpha = 0.4f + energy * 0.3f),
            style = Stroke(width = 8f + layer * 4f, cap = StrokeCap.Round),
        )
    }
}

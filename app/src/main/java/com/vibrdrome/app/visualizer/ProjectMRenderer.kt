package com.vibrdrome.app.visualizer

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ProjectMRenderer(
    private val bridge: ProjectMBridge,
    private val presetPath: String,
) : GLSurfaceView.Renderer {

    private var handle: Long = 0
    private var presetFiles: List<String> = emptyList()
    private var currentPresetIndex = 0

    @Volatile var waveformData: ByteArray = ByteArray(0)

    // Pending preset load — queued from UI thread, executed on GL thread
    @Volatile private var pendingPresetIndex: Int = -1
    @Volatile private var pendingSmooth: Boolean = false

    val currentPresetName: String
        get() = if (presetFiles.isNotEmpty()) {
            java.io.File(presetFiles[currentPresetIndex]).nameWithoutExtension
        } else "Milkdrop"

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Scan for presets
        val dir = java.io.File(presetPath)
        presetFiles = if (dir.exists()) {
            dir.listFiles()
                ?.filter { it.extension == "milk" || it.extension == "prjm" }
                ?.map { it.absolutePath }
                ?.sorted()
                ?: emptyList()
        } else emptyList()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (handle == 0L) {
            handle = bridge.nativeCreate(width, height)
            if (handle != 0L) {
                // Disable projectM's internal auto-switching — we manage presets from Kotlin
                bridge.nativeSetPresetDuration(handle, 0.0)
                bridge.nativeSetPresetLocked(handle, true)
                // Load first preset directly on GL thread
                loadPresetOnGLThread(currentPresetIndex, false)
            }
        } else {
            bridge.nativeResize(handle, width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (handle == 0L || released) return

        // Process pending preset change on GL thread
        val pending = pendingPresetIndex
        if (pending >= 0) {
            pendingPresetIndex = -1
            loadPresetOnGLThread(pending, pendingSmooth)
        }

        val data = waveformData
        if (data.isNotEmpty()) {
            bridge.nativeAddAudioData(handle, data)
        }

        bridge.nativeRenderFrame(handle)
    }

    fun nextPreset() {
        if (presetFiles.isEmpty()) return
        currentPresetIndex = (currentPresetIndex + 1) % presetFiles.size
        pendingSmooth = true
        pendingPresetIndex = currentPresetIndex
    }

    fun previousPreset() {
        if (presetFiles.isEmpty()) return
        currentPresetIndex = (currentPresetIndex - 1 + presetFiles.size) % presetFiles.size
        pendingSmooth = true
        pendingPresetIndex = currentPresetIndex
    }

    fun randomPreset() {
        if (presetFiles.isEmpty()) return
        currentPresetIndex = (0 until presetFiles.size).random()
        pendingSmooth = true
        pendingPresetIndex = currentPresetIndex
    }

    /** Must be called on the GL thread only. */
    private fun loadPresetOnGLThread(index: Int, smooth: Boolean) {
        if (handle == 0L || presetFiles.isEmpty()) return
        val safeIndex = index.coerceIn(0, presetFiles.size - 1)
        try {
            bridge.nativeLoadPreset(handle, presetFiles[safeIndex], smooth)
        } catch (_: Exception) {
            // If preset fails, try the next one
            if (presetFiles.size > 1) {
                val fallback = (safeIndex + 1) % presetFiles.size
                currentPresetIndex = fallback
                try {
                    bridge.nativeLoadPreset(handle, presetFiles[fallback], smooth)
                } catch (_: Exception) {}
            }
        }
    }

    @Volatile
    private var released = false

    fun release() {
        released = true
        if (handle != 0L) {
            try { bridge.nativeDestroy(handle) } catch (_: Exception) {}
            handle = 0
        }
    }
}

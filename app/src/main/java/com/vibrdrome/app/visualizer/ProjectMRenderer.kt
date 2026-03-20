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
                bridge.nativeSetPresetDuration(handle, 15.0)
                loadCurrentPreset(true)
            }
        } else {
            bridge.nativeResize(handle, width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (handle == 0L) return

        val data = waveformData
        if (data.isNotEmpty()) {
            bridge.nativeAddAudioData(handle, data)
        }

        bridge.nativeRenderFrame(handle)
    }

    fun nextPreset() {
        if (presetFiles.isEmpty()) return
        currentPresetIndex = (currentPresetIndex + 1) % presetFiles.size
        loadCurrentPreset(true)
    }

    fun previousPreset() {
        if (presetFiles.isEmpty()) return
        currentPresetIndex = (currentPresetIndex - 1 + presetFiles.size) % presetFiles.size
        loadCurrentPreset(true)
    }

    fun randomPreset() {
        if (presetFiles.isEmpty()) return
        currentPresetIndex = (0 until presetFiles.size).random()
        loadCurrentPreset(true)
    }

    private fun loadCurrentPreset(smooth: Boolean) {
        if (handle == 0L || presetFiles.isEmpty()) return
        bridge.nativeLoadPreset(handle, presetFiles[currentPresetIndex], smooth)
    }

    fun release() {
        if (handle != 0L) {
            bridge.nativeDestroy(handle)
            handle = 0
        }
    }
}

package com.vibrdrome.app.visualizer

import android.content.Context
import android.content.res.AssetManager

/**
 * JNI bridge to libprojectM-4.
 *
 * All native methods operate on a single projectM instance created on the GL thread.
 * Audio data is fed from the Visualizer API on a separate thread.
 */
class ProjectMBridge {

    companion object {
        init {
            System.loadLibrary("projectm_jni")
        }
    }

    /** Create the projectM instance. Must be called on the GL thread after EGL context is ready. */
    external fun nativeCreate(width: Int, height: Int): Long

    /** Destroy the projectM instance. */
    external fun nativeDestroy(handle: Long)

    /** Render one frame. Must be called on the GL thread. */
    external fun nativeRenderFrame(handle: Long)

    /** Resize the rendering viewport. */
    external fun nativeResize(handle: Long, width: Int, height: Int)

    /** Feed PCM audio data (unsigned 8-bit, mono). */
    external fun nativeAddAudioData(handle: Long, data: ByteArray)

    /** Load a preset from a file path. */
    external fun nativeLoadPreset(handle: Long, path: String, smooth: Boolean)

    /** Load a preset from raw data string. */
    external fun nativeLoadPresetData(handle: Long, data: String, smooth: Boolean)

    /** Get the number of seconds until the next preset auto-switch. */
    external fun nativeGetPresetDuration(handle: Long): Double

    /** Set the number of seconds between preset auto-switches. 0 = no auto-switch. */
    external fun nativeSetPresetDuration(handle: Long, seconds: Double)

    /** Trigger an immediate switch to a random preset (from loaded preset path). */
    external fun nativeSelectRandomPreset(handle: Long, smooth: Boolean)

    /** Set the preset search path for random preset switching. */
    external fun nativeSetPresetPath(handle: Long, path: String)

    /** Lock/unlock preset switching. */
    external fun nativeSetPresetLocked(handle: Long, locked: Boolean)

    /**
     * Extract bundled presets from assets to internal storage.
     * Returns the path where presets were extracted.
     */
    fun extractPresets(context: Context): String {
        val presetDir = java.io.File(context.filesDir, "projectm_presets")
        val assetPresets = try { context.assets.list("presets")?.size ?: 0 } catch (_: Exception) { 0 }
        val existingPresets = presetDir.listFiles()?.size ?: 0
        // Re-extract if count changed (preset bundle was updated)
        if (presetDir.exists() && existingPresets == assetPresets && assetPresets > 0) {
            return presetDir.absolutePath
        }
        // Clear old presets and re-extract
        if (presetDir.exists()) presetDir.deleteRecursively()
        presetDir.mkdirs()
        try {
            copyAssetsFolder(context.assets, "presets", presetDir.absolutePath)
        } catch (_: Exception) {
            // No bundled presets
        }
        return presetDir.absolutePath
    }

    private fun copyAssetsFolder(assets: AssetManager, src: String, dst: String) {
        val list = assets.list(src) ?: return
        if (list.isEmpty()) {
            // It's a file
            assets.open(src).use { input ->
                java.io.File(dst).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            java.io.File(dst).mkdirs()
            for (item in list) {
                copyAssetsFolder(assets, "$src/$item", "$dst/$item")
            }
        }
    }
}

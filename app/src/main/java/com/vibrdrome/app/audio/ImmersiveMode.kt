package com.vibrdrome.app.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immersive Mode — bundles visualizer, haptics, and crossfade into a single toggle.
 * When activated: opens visualizer, enables haptics, enables crossfade.
 * When deactivated: returns each feature to its individual saved setting.
 */
class ImmersiveMode(context: Context) {
    private val prefs = context.getSharedPreferences("immersive_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // Saved states before immersive mode was enabled
    private var savedHapticState = false
    private var savedCrossfadeState = false

    fun activate(
        playbackManager: PlaybackManager,
        hapticEngine: HapticEngine,
    ) {
        // Save current states
        savedHapticState = hapticEngine.enabled.value
        savedCrossfadeState = playbackManager.crossfadeEnabled.value

        // Enable everything
        hapticEngine.setEnabled(true)
        playbackManager.setCrossfadeEnabled(true)

        _enabled.value = true
    }

    fun deactivate(
        playbackManager: PlaybackManager,
        hapticEngine: HapticEngine,
    ) {
        // Restore saved states
        hapticEngine.setEnabled(savedHapticState)
        playbackManager.setCrossfadeEnabled(savedCrossfadeState)

        _enabled.value = false
    }

    fun toggle(
        playbackManager: PlaybackManager,
        hapticEngine: HapticEngine,
    ) {
        if (_enabled.value) {
            deactivate(playbackManager, hapticEngine)
        } else {
            activate(playbackManager, hapticEngine)
        }
    }
}

package com.vibrdrome.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Haptic feedback engine that pulses the vibration motor in sync with bass energy
 * from the visualizer. Designed to feel musical, not mechanical.
 *
 * Settings toggles (all persisted):
 * - Enabled (default: off)
 * - Intensity: subtle (40 amplitude), medium (120), strong (200)
 * - Auto-disabled when Bluetooth audio active (latency mismatch)
 * - Auto-disabled when casting
 */
enum class HapticIntensity(val label: String, val baseAmplitude: Int, val durationMs: Long) {
    SUBTLE("Subtle", 80, 25),
    MEDIUM("Medium", 180, 35),
    STRONG("Strong", 255, 50),
}

class HapticEngine(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("haptic_prefs", Context.MODE_PRIVATE)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        mgr.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _enabled = MutableStateFlow(prefs.getBoolean("haptic_enabled", false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _intensity = MutableStateFlow(
        HapticIntensity.entries.getOrElse(prefs.getInt("haptic_intensity", 1)) { HapticIntensity.MEDIUM }
    )
    val intensity: StateFlow<HapticIntensity> = _intensity.asStateFlow()

    // Beat detection state
    private var runningAvgBass = 0f
    private var sampleCount = 0
    private var lastPulseTime = 0L
    private val minPulseIntervalMs = 150L // Rate limit: no faster than ~6.6 Hz

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean("haptic_enabled", enabled).apply()
    }

    fun setIntensity(intensity: HapticIntensity) {
        _intensity.value = intensity
        prefs.edit().putInt("haptic_intensity", intensity.ordinal).apply()
    }

    /**
     * Feed bass energy from the visualizer. Triggers a haptic pulse when a beat is detected.
     *
     * @param bassEnergy Normalized bass energy (0.0–1.0) from visualizer
     * @param isCasting Whether playback is routed to a Cast device
     */
    fun onBassEnergy(bassEnergy: Float, isCasting: Boolean) {
        if (!_enabled.value || isCasting) return

        // Auto-disable on Bluetooth (latency makes haptics feel wrong)
        if (isBluetoothAudioActive()) return

        // Update running average
        sampleCount++
        runningAvgBass = runningAvgBass + (bassEnergy - runningAvgBass) / sampleCount.coerceAtMost(30)

        // Beat detection: bass exceeds 1.5x running average
        val threshold = runningAvgBass * 1.5f
        val now = System.currentTimeMillis()

        if (bassEnergy > threshold && bassEnergy > 0.08f && now - lastPulseTime >= minPulseIntervalMs) {
            pulse(bassEnergy)
            lastPulseTime = now
        }
    }

    private fun pulse(bassEnergy: Float) {
        if (!vibrator.hasVibrator()) return

        val preset = _intensity.value
        // Scale amplitude with bass energy — use full range of the preset
        val amplitude = (preset.baseAmplitude * (0.7f + bassEnergy * 0.3f)).toInt().coerceIn(1, 255)
        val duration = preset.durationMs

        // minSdk is 26 (API O), so VibrationEffect is always available
        vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    }

    private fun isBluetoothAudioActive(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    }

    /**
     * Reset beat detection state. Call on track change.
     */
    fun reset() {
        runningAvgBass = 0f
        sampleCount = 0
    }
}

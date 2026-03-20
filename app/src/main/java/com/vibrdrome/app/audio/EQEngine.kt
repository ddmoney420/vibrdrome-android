package com.vibrdrome.app.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EQEngine(private val store: EQCoefficientsStore, context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)

    private val _gains = MutableStateFlow(FloatArray(EQPresets.BAND_COUNT) { 0f })
    val gains: StateFlow<FloatArray> = _gains.asStateFlow()

    private val _currentPresetName = MutableStateFlow("Flat")
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    init {
        // Restore saved state
        _isEnabled.value = prefs.getBoolean("eq_enabled", false)
        store.isEnabled = _isEnabled.value
        val savedPreset = prefs.getString("eq_preset", null)
        if (savedPreset != null) {
            val preset = EQPresets.allPresets.find { it.name == savedPreset }
            if (preset != null) {
                _gains.value = preset.gains.copyOf()
                _currentPresetName.value = preset.name
                store.update(preset.gains, store.sampleRate)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        store.isEnabled = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
    }

    fun toggleEnabled() = setEnabled(!_isEnabled.value)

    fun setGains(gains: FloatArray) {
        _gains.value = gains.copyOf()
        store.update(gains, store.sampleRate)
    }

    fun setBandGain(band: Int, gain: Float) {
        val newGains = _gains.value.copyOf()
        newGains[band] = gain.coerceIn(EQPresets.MIN_GAIN, EQPresets.MAX_GAIN)
        _gains.value = newGains
        store.update(newGains, store.sampleRate)
        _currentPresetName.value = "Custom"
    }

    fun applyPreset(preset: EQPreset) {
        setGains(preset.gains.copyOf())
        _currentPresetName.value = preset.name
        prefs.edit().putString("eq_preset", preset.name).apply()
    }

    fun reset() {
        applyPreset(EQPresets.flat)
    }
}

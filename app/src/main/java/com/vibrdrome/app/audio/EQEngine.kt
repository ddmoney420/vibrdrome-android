package com.vibrdrome.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EQEngine(private val store: EQCoefficientsStore) {

    private val _gains = MutableStateFlow(FloatArray(EQPresets.BAND_COUNT) { 0f })
    val gains: StateFlow<FloatArray> = _gains.asStateFlow()

    private val _currentPresetName = MutableStateFlow("Flat")
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        store.isEnabled = enabled
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
    }

    fun reset() {
        applyPreset(EQPresets.flat)
    }
}

package com.vibrdrome.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EQEngine(private val store: EQCoefficientsStore, private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    /** Must be called after all fields are initialized. Called from the property initializer block below. */
    private fun restoreLateState() {
        // Restore custom preset if not found in built-ins
        val savedPreset = prefs.getString("eq_preset", null)
        if (savedPreset != null && _currentPresetName.value == "Flat") {
            val custom = _customPresets.value.find { it.name == savedPreset }
            if (custom != null) {
                _gains.value = custom.gains.copyOf()
                _currentPresetName.value = custom.name
                store.update(custom.gains, store.sampleRate)
            }
        }
        if (_deviceProfilesEnabled.value) {
            startDeviceTracking()
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

    // MARK: - Custom Presets (saved by user or imported from AutoEQ/APO)

    private val _customPresets = MutableStateFlow<List<EQPreset>>(loadCustomPresets())
    val customPresets: StateFlow<List<EQPreset>> = _customPresets.asStateFlow()

    /** All presets: built-in + custom/imported. Reactive — updates when custom presets change. */
    val allPresets: StateFlow<List<EQPreset>> = _customPresets
        .map { custom -> EQPresets.allPresets + custom }
        .stateIn(scope, SharingStarted.Eagerly, EQPresets.allPresets + _customPresets.value)

    fun saveCustomPreset(name: String) {
        val preset = EQPreset(name, _gains.value.copyOf())
        val updated = _customPresets.value.filter { it.name != name } + preset
        _customPresets.value = updated
        persistCustomPresets(updated)
        _currentPresetName.value = name
        prefs.edit().putString("eq_preset", name).apply()
    }

    fun deleteCustomPreset(name: String) {
        val updated = _customPresets.value.filter { it.name != name }
        _customPresets.value = updated
        persistCustomPresets(updated)
    }

    /**
     * Import an EQ profile from AutoEQ CSV or Equalizer APO config text.
     * Returns the preset name on success, null on failure.
     */
    fun importProfile(content: String, name: String): String? {
        val preset = AutoEQImporter.parse(content, name) ?: return null
        val updated = _customPresets.value.filter { it.name != name } + preset
        _customPresets.value = updated
        persistCustomPresets(updated)
        applyPreset(preset)
        return preset.name
    }

    /**
     * Export current EQ settings as Equalizer APO format text.
     */
    fun exportAsAPO(): String {
        val sb = StringBuilder()
        sb.appendLine("Preamp: 0 dB")
        for (i in _gains.value.indices) {
            val freq = EQPresets.frequencies[i].toInt()
            val gain = "%.1f".format(_gains.value[i])
            sb.appendLine("Filter ${i + 1}: ON PK Fc $freq Hz Gain $gain dB Q ${EQPresets.DEFAULT_Q}")
        }
        return sb.toString()
    }

    private fun persistCustomPresets(presets: List<EQPreset>) {
        val data = presets.joinToString("|") { p ->
            "${p.name}:${p.gains.joinToString(",")}"
        }
        prefs.edit().putString("custom_presets", data).apply()
    }

    // MARK: - Per-Device EQ Profiles

    private val _currentDeviceName = MutableStateFlow<String?>(null)
    val currentDeviceName: StateFlow<String?> = _currentDeviceName.asStateFlow()

    private val _deviceProfilesEnabled = MutableStateFlow(prefs.getBoolean("device_profiles_enabled", false))
    val deviceProfilesEnabled: StateFlow<Boolean> = _deviceProfilesEnabled.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Second init block — runs after all fields above are initialized
    init { restoreLateState() }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            checkCurrentDevice()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            checkCurrentDevice()
        }
    }

    fun startDeviceTracking() {
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        checkCurrentDevice()
    }

    fun stopDeviceTracking() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    private fun checkCurrentDevice() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val active = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }

        val deviceName = active?.productName?.toString() ?: "Speaker"
        val deviceKey = "${active?.type ?: 0}_${deviceName}"

        _currentDeviceName.value = deviceName

        if (_deviceProfilesEnabled.value) {
            val savedProfile = loadDeviceProfile(deviceKey)
            if (savedProfile != null) {
                setGains(savedProfile)
                _currentPresetName.value = "Device: $deviceName"
            }
        }
    }

    fun setDeviceProfilesEnabled(enabled: Boolean) {
        _deviceProfilesEnabled.value = enabled
        prefs.edit().putBoolean("device_profiles_enabled", enabled).apply()
        if (enabled) {
            startDeviceTracking()
        } else {
            stopDeviceTracking()
        }
    }

    fun saveDeviceProfile() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val active = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }

        val deviceName = active?.productName?.toString() ?: "Speaker"
        val deviceKey = "${active?.type ?: 0}_${deviceName}"

        val gainsStr = _gains.value.joinToString(",")
        prefs.edit().putString("device_eq_$deviceKey", gainsStr).apply()
    }

    private fun loadDeviceProfile(deviceKey: String): FloatArray? {
        val gainsStr = prefs.getString("device_eq_$deviceKey", null) ?: return null
        val gains = gainsStr.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        return if (gains.size == EQPresets.BAND_COUNT) gains else null
    }

    private fun loadCustomPresets(): List<EQPreset> {
        val data = prefs.getString("custom_presets", null) ?: return emptyList()
        return data.split("|").mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val name = parts[0]
            val gains = parts[1].split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
            if (gains.size == EQPresets.BAND_COUNT) EQPreset(name, gains) else null
        }
    }
}

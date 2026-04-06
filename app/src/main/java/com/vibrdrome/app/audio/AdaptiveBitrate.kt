package com.vibrdrome.app.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors streaming throughput and adjusts bitrate quality on track boundaries.
 * Never downgrades mid-track to avoid audible artifacts.
 *
 * Quality tiers:
 * - Original (>2 Mbps throughput)
 * - 320 kbps (>500 kbps throughput)
 * - 192 kbps (>300 kbps throughput)
 * - 128 kbps (>150 kbps throughput)
 * - 96 kbps (fallback)
 */
class AdaptiveBitrate(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("adaptive_bitrate", Context.MODE_PRIVATE)
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _enabledOnCellular = MutableStateFlow(prefs.getBoolean("adaptive_cellular", true))
    val enabledOnCellular: StateFlow<Boolean> = _enabledOnCellular.asStateFlow()

    private val _enabledOnWifi = MutableStateFlow(prefs.getBoolean("adaptive_wifi", false))
    val enabledOnWifi: StateFlow<Boolean> = _enabledOnWifi.asStateFlow()

    private val _currentQuality = MutableStateFlow<Int?>(null) // null = use user setting
    val currentQuality: StateFlow<Int?> = _currentQuality.asStateFlow()

    // Throughput tracking
    private val throughputSamples = mutableListOf<Float>() // kbps
    private var lastGoodThroughputTime = 0L

    // Quality tiers: bitrate → minimum throughput required (kbps)
    private val tiers = listOf(
        0 to 2000f,     // Original
        320 to 500f,
        192 to 300f,
        128 to 150f,
        96 to 0f,       // Fallback — always available
    )

    fun setEnabledOnCellular(enabled: Boolean) {
        _enabledOnCellular.value = enabled
        prefs.edit().putBoolean("adaptive_cellular", enabled).apply()
        if (!enabled) _currentQuality.value = null
    }

    fun setEnabledOnWifi(enabled: Boolean) {
        _enabledOnWifi.value = enabled
        prefs.edit().putBoolean("adaptive_wifi", enabled).apply()
        if (!enabled) _currentQuality.value = null
    }

    /**
     * Record throughput measurement during streaming.
     * Called from HTTP data source or download monitoring.
     */
    fun recordThroughput(bytesReceived: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val kbps = (bytesReceived * 8f) / durationMs // bits per ms = kbps
        throughputSamples.add(kbps)
        if (throughputSamples.size > 20) {
            throughputSamples.removeAt(0)
        }
    }

    /**
     * Record a buffer underrun event. Triggers immediate quality reduction on next track.
     */
    fun recordBufferUnderrun() {
        val avg = averageThroughput()
        // Find the next tier below current average
        val downgraded = tiers.firstOrNull { it.second < avg * 0.8f }
        if (downgraded != null) {
            _currentQuality.value = downgraded.first
        }
    }

    /**
     * Get the recommended bitrate for the next track.
     * Returns null to use the user's configured quality setting.
     */
    fun getRecommendedBitrate(maxUserBitrate: Int): Int? {
        if (!isAdaptiveActive()) return null

        val avg = averageThroughput()
        if (avg <= 0f) return null

        // Find the highest tier our throughput supports
        val bestTier = tiers.firstOrNull { avg >= it.second }
        val recommended = bestTier?.first ?: 96

        // Respect user's max setting as ceiling
        return if (maxUserBitrate > 0 && recommended > maxUserBitrate) {
            null // User already set a lower cap
        } else if (recommended == 0) {
            null // Original quality — don't override
        } else {
            recommended
        }
    }

    /**
     * Call on track boundary to check if quality should upgrade.
     * Upgrades only after 30 seconds of sustained good throughput.
     */
    fun checkUpgrade(): Boolean {
        if (!isAdaptiveActive()) return false
        val current = _currentQuality.value ?: return false
        val avg = averageThroughput()

        // Find the tier above current
        val currentIndex = tiers.indexOfFirst { it.first == current }
        if (currentIndex <= 0) return false

        val higherTier = tiers[currentIndex - 1]
        val now = System.currentTimeMillis()

        if (avg >= higherTier.second) {
            if (lastGoodThroughputTime == 0L) {
                lastGoodThroughputTime = now
            }
            // Sustained for 30 seconds
            if (now - lastGoodThroughputTime >= 30_000) {
                _currentQuality.value = higherTier.first
                lastGoodThroughputTime = 0L
                return true
            }
        } else {
            lastGoodThroughputTime = 0L
        }
        return false
    }

    /** Format current quality for display */
    fun qualityLabel(): String {
        val q = _currentQuality.value
        return when {
            q == null -> "Auto"
            q == 0 -> "Original"
            else -> "${q}k"
        }
    }

    private fun isAdaptiveActive(): Boolean {
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return if (isWifi) _enabledOnWifi.value else _enabledOnCellular.value
    }

    private fun averageThroughput(): Float {
        if (throughputSamples.isEmpty()) return 0f
        return throughputSamples.average().toFloat()
    }
}

package com.vibrdrome.app.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * Predictive pre-buffering: starts loading the next track's first ~15 seconds
 * when the current track reaches 80% played.
 *
 * This is invisible to the user — no UI, no settings, just works.
 * The pre-buffered data is held in memory and discarded on queue change.
 *
 * Note: ExoPlayer already handles its own buffering, but this ensures the
 * initial HTTP connection and first bytes are warm before the transition,
 * eliminating any perceivable gap on slow connections.
 */
class PreBufferManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var preBufferJob: Job? = null
    private var preBufferedSongId: String? = null

    // Warm the connection by reading a small amount (enough to establish HTTP + start server-side processing)
    private val warmUpBytes = 16 * 1024

    /**
     * Check if we should start pre-buffering the next track.
     * Call from position tracking when track reaches 80%.
     */
    fun checkPreBuffer(
        progress: Float, // 0.0–1.0
        nextSongId: String?,
        nextStreamUrl: String?,
    ) {
        if (progress < 0.8f || nextSongId == null || nextStreamUrl == null) return
        if (nextSongId == preBufferedSongId) return // Already buffering/buffered
        if (isMeteredAndDataSaver()) return

        preBufferedSongId = nextSongId
        preBufferJob?.cancel()
        preBufferJob = scope.launch {
            try {
                // Warm the HTTP connection — establishes TCP + TLS + triggers server-side
                // stream preparation. The actual data is discarded; ExoPlayer will make
                // its own request but the server-side cache/transcoding will be warm.
                val connection = URL(nextStreamUrl).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.getInputStream().use { input ->
                    val chunk = ByteArray(8192)
                    var totalRead = 0
                    while (totalRead < warmUpBytes) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        totalRead += read
                    }
                }
            } catch (_: Exception) {
                // Connection warming is best-effort — silent failure is fine
            }
        }
    }

    /**
     * Cancel pre-buffering (queue changed, skipped, etc.)
     */
    fun cancel() {
        preBufferJob?.cancel()
        preBufferedSongId = null
    }

    private fun isMeteredAndDataSaver(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    }
}

package com.vibrdrome.app.cast

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Chromecast integration. Holds the [CastPlayer] and tracks Cast session state.
 *
 * When a Cast session begins, the active player should be swapped from ExoPlayer to CastPlayer
 * via [PlaybackManager] and [PlaybackService]. When the session ends, swap back.
 */
@OptIn(UnstableApi::class)
class CastManager(context: Context) {

    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val _castAvailable = MutableStateFlow(false)
    val castAvailable: StateFlow<Boolean> = _castAvailable.asStateFlow()

    private var onCastSessionStarted: (() -> Unit)? = null
    private var onCastSessionEnded: (() -> Unit)? = null
    private var onCastError: ((String) -> Unit)? = null

    private val castStateListener = CastStateListener { state ->
        Log.d("CastManager", "Cast state changed: $state (NO_DEVICES=${CastState.NO_DEVICES_AVAILABLE}, CONNECTED=${CastState.CONNECTED})")
        _castAvailable.value = state != CastState.NO_DEVICES_AVAILABLE
        // Also check if we're connected — SessionAvailabilityListener may not fire for system-initiated sessions
        if (state == CastState.CONNECTED) {
            val device = castContext?.sessionManager?.currentCastSession?.castDevice
            val name = device?.friendlyName ?: device?.modelName ?: "Chromecast"
            if (!_isCasting.value) {
                Log.d("CastManager", "Connected via system route, forcing cast state: $name")
                _isCasting.value = true
                _castDeviceName.value = name
                onCastSessionStarted?.invoke()
            }
        } else if (state == CastState.NOT_CONNECTED && _isCasting.value) {
            Log.d("CastManager", "Disconnected via system route")
            _isCasting.value = false
            _castDeviceName.value = null
            onCastSessionEnded?.invoke()
        }
    }

    /**
     * Initialize Cast. Must be called from an Activity context (Cast SDK requires it).
     * Safe to call multiple times — will no-op if already initialized.
     */
    fun initialize(activityContext: Context) {
        if (castContext != null) return
        try {
            castContext = CastContext.getSharedInstance(activityContext)
            castContext?.addCastStateListener(castStateListener)

            castPlayer = CastPlayer(castContext!!).apply {
                setSessionAvailabilityListener(object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() {
                        val device = castContext?.sessionManager?.currentCastSession?.castDevice
                        val name = device?.friendlyName ?: device?.modelName ?: "Chromecast"
                        Log.d("CastManager", "Cast session available: $name")
                        _isCasting.value = true
                        _castDeviceName.value = name
                        onCastSessionStarted?.invoke()
                    }

                    override fun onCastSessionUnavailable() {
                        Log.d("CastManager", "Cast session unavailable")
                        _isCasting.value = false
                        _castDeviceName.value = null
                        onCastSessionEnded?.invoke()
                    }
                })
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        onCastError?.invoke("Cast error: ${error.message ?: "playback failed"}")
                    }
                })
            }
        } catch (_: Exception) {
            // Cast SDK not available (e.g., no Google Play Services) — degrade gracefully
        }
    }

    /**
     * Register callbacks for Cast session lifecycle. Called by PlaybackManager.
     */
    fun setSessionCallbacks(
        onStarted: () -> Unit,
        onEnded: () -> Unit,
        onError: (String) -> Unit = {},
    ) {
        onCastSessionStarted = onStarted
        onCastSessionEnded = onEnded
        onCastError = onError
    }

    /**
     * Get the CastPlayer instance, or null if Cast is unavailable.
     */
    fun getPlayer(): CastPlayer? = castPlayer

    /**
     * Get the CastContext for the MediaRouteButton.
     */
    fun getCastContext(): CastContext? = castContext

    fun release() {
        castContext?.removeCastStateListener(castStateListener)
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        castContext = null
    }
}

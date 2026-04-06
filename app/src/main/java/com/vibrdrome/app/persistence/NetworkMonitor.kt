package com.vibrdrome.app.persistence

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.vibrdrome.app.ui.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Monitors network connectivity and processes the offline action queue
 * when connectivity is restored.
 */
class NetworkMonitor(
    context: Context,
    private val offlineActionQueue: OfflineActionQueue,
    private val appState: AppState,
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isOnline = MutableStateFlow(checkConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingActionCount = MutableStateFlow(0)
    val pendingActionCount: StateFlow<Int> = _pendingActionCount.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            processOfflineQueue()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkConnectivity()
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        _isOnline.value = checkConnectivity()

        // Track pending count
        scope.launch {
            offlineActionQueue.pendingActions.collect { actions ->
                _pendingActionCount.value = actions.size
            }
        }

        // Process any pending actions on start
        if (_isOnline.value) {
            processOfflineQueue()
        }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }

    private fun processOfflineQueue() {
        scope.launch {
            val count = offlineActionQueue.pendingCount()
            if (count > 0) {
                offlineActionQueue.processAll(appState.subsonicClient)
            }
        }
    }

    private fun checkConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

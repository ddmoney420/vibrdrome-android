package com.vibrdrome.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.vibrdrome.app.network.ResponseCache
import com.vibrdrome.app.network.SubsonicClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedServer(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
)

class AppState(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vibrdrome_prefs", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = createSecurePrefs(context)
    private val responseCache = ResponseCache(context)

    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _requiresReAuth = MutableStateFlow(false)
    val requiresReAuth: StateFlow<Boolean> = _requiresReAuth.asStateFlow()

    private val _serverURL = MutableStateFlow("")
    val serverURL: StateFlow<String> = _serverURL.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Global error channel for snackbar display
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun showError(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private val _servers = MutableStateFlow<List<SavedServer>>(emptyList())
    val servers: StateFlow<List<SavedServer>> = _servers.asStateFlow()

    private var activeServerId: String? = null

    var subsonicClient: SubsonicClient = SubsonicClient(
        baseURL = "https://localhost",
        username = "",
        password = "",
        responseCache = responseCache,
        onRequiresReAuth = { _requiresReAuth.value = true },
    )
        private set

    private val _themeMode = MutableStateFlow(prefs.getString("themeMode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("themeMode", mode).apply()
    }

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadServers()
        loadSavedCredentials()
    }

    fun configure(url: String, username: String, password: String) {
        val normalizedURL = url.trimEnd('/')
        if (Uri.parse(normalizedURL).host == null) {
            _errorMessage.value = "Invalid server URL"
            _isConfigured.value = false
            return
        }
        _serverURL.value = normalizedURL
        _username.value = username
        subsonicClient.updateCredentials(normalizedURL, username, password)
        _isConfigured.value = true
        _errorMessage.value = null
    }

    private fun loadSavedCredentials() {
        // Try active server first
        val activeId = activeServerId
        if (activeId != null) {
            val server = _servers.value.find { it.id == activeId }
            val password = securePrefs.getString("server_$activeId", null)
            if (server != null && password != null) {
                configure(server.url, server.username, password)
                return
            }
        }

        // Fall back to legacy single-server credentials
        val url = prefs.getString(PrefsKeys.SERVER_URL, null)
        val username = prefs.getString(PrefsKeys.USERNAME, null)
        val password = securePrefs.getString("serverPassword", null)
        if (url != null && username != null && password != null) {
            configure(url, username, password)
            // Migrate to multi-server format
            if (_servers.value.isEmpty()) {
                val config = SavedServer(
                    id = java.util.UUID.randomUUID().toString(),
                    name = extractServerName(url),
                    url = url,
                    username = username,
                )
                _servers.value = listOf(config)
                activeServerId = config.id
                securePrefs.edit().putString("server_${config.id}", password).apply()
                saveServers()
            }
        }
    }

    fun saveCredentials(url: String, username: String, password: String) {
        configure(url, username, password)
        if (!_isConfigured.value) return

        val activeId = activeServerId
        if (activeId != null) {
            val updated = _servers.value.map {
                if (it.id == activeId) it.copy(url = _serverURL.value, username = username) else it
            }
            _servers.value = updated
            securePrefs.edit().putString("server_$activeId", password).apply()
        } else {
            val config = SavedServer(
                id = java.util.UUID.randomUUID().toString(),
                name = extractServerName(_serverURL.value),
                url = _serverURL.value,
                username = username,
            )
            _servers.value = _servers.value + config
            activeServerId = config.id
            securePrefs.edit().putString("server_${config.id}", password).apply()
        }
        saveServers()

        // Keep legacy keys in sync
        prefs.edit()
            .putString(PrefsKeys.SERVER_URL, _serverURL.value)
            .putString(PrefsKeys.USERNAME, username)
            .apply()
        securePrefs.edit().putString("serverPassword", password).apply()
    }

    fun reAuthenticate(password: String) {
        if (_serverURL.value.isEmpty() || _username.value.isEmpty()) return
        saveCredentials(_serverURL.value, _username.value, password)
        _requiresReAuth.value = false
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(PrefsKeys.SERVER_URL)
            .remove(PrefsKeys.USERNAME)
            .apply()
        securePrefs.edit().remove("serverPassword").apply()
        _isConfigured.value = false
        _requiresReAuth.value = false
        _serverURL.value = ""
        _username.value = ""
        subsonicClient.updateCredentials("https://localhost", "", "")
    }

    // MARK: - Multi-Server

    fun addServer(name: String, url: String, username: String, password: String) {
        val normalizedURL = url.trimEnd('/')
        val config = SavedServer(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            url = normalizedURL,
            username = username,
        )
        _servers.value = _servers.value + config
        securePrefs.edit().putString("server_${config.id}", password).apply()
        saveServers()
        switchToServer(config.id)
    }

    fun switchToServer(id: String) {
        val server = _servers.value.find { it.id == id } ?: return
        val password = securePrefs.getString("server_$id", null) ?: return
        activeServerId = id
        prefs.edit().putString(PrefsKeys.ACTIVE_SERVER_ID, id).apply()
        configure(server.url, server.username, password)
    }

    fun deleteServer(id: String) {
        _servers.value = _servers.value.filter { it.id != id }
        securePrefs.edit().remove("server_$id").apply()
        saveServers()
        if (activeServerId == id) {
            val first = _servers.value.firstOrNull()
            if (first != null) {
                switchToServer(first.id)
            } else {
                activeServerId = null
                clearCredentials()
            }
        }
    }

    private fun loadServers() {
        val data = prefs.getString(PrefsKeys.SAVED_SERVERS, null)
        if (data != null) {
            try {
                _servers.value = json.decodeFromString(data)
            } catch (_: Exception) { }
        }
        activeServerId = prefs.getString(PrefsKeys.ACTIVE_SERVER_ID, null)
    }

    private fun saveServers() {
        prefs.edit()
            .putString(PrefsKeys.SAVED_SERVERS, json.encodeToString(_servers.value))
            .putString(PrefsKeys.ACTIVE_SERVER_ID, activeServerId)
            .apply()
    }

    private fun extractServerName(url: String): String {
        return Uri.parse(url).host ?: "My Server"
    }

    companion object {
        private fun createSecurePrefs(context: Context): SharedPreferences {
            val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                "vibrdrome_secure_prefs",
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

object PrefsKeys {
    const val SERVER_URL = "serverURL"
    const val USERNAME = "username"
    const val SAVED_SERVERS = "savedServers"
    const val ACTIVE_SERVER_ID = "activeServerId"
}

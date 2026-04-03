package com.vibrdrome.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.vibrdrome.app.network.MusicFolder
import com.vibrdrome.app.network.ResponseCache
import com.vibrdrome.app.network.SubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

@Serializable
data class LibraryItem(
    val id: String,
    val visible: Boolean = true,
)

object LibraryItemIds {
    // Pills
    const val GENRES = "genres"
    const val RADIO = "radio"
    const val ARTISTS = "artists"
    const val FAVORITES = "favorites"
    const val ALBUMS = "albums"
    const val FOLDERS = "folders"
    const val SONGS = "songs"
    const val DOWNLOADS = "downloads"
    const val PLAYLISTS = "playlists"
    const val RECENTLY_ADDED = "recently_added"
    const val GENERATIONS = "generations"
    const val RECENTLY_PLAYED = "recently_played"
    const val RANDOM_MIX = "random_mix"
    const val RANDOM_ALBUM = "random_album"
    // Carousels
    const val CAROUSEL_RECENT = "carousel_recent"
    const val CAROUSEL_FREQUENT = "carousel_frequent"
    const val CAROUSEL_RANDOM = "carousel_random"

    val DEFAULT_ORDER = listOf(
        GENRES, RADIO, ARTISTS, FAVORITES, ALBUMS, FOLDERS, SONGS, DOWNLOADS,
        PLAYLISTS, RECENTLY_ADDED, GENERATIONS, RECENTLY_PLAYED, RANDOM_MIX, RANDOM_ALBUM,
        CAROUSEL_RECENT, CAROUSEL_FREQUENT, CAROUSEL_RANDOM,
    )

    fun isCarousel(id: String) = id.startsWith("carousel_")

    fun displayName(id: String): String = when (id) {
        GENRES -> "Genres"
        RADIO -> "Radio"
        ARTISTS -> "Artists"
        FAVORITES -> "Favorites"
        ALBUMS -> "Albums"
        FOLDERS -> "Folders"
        SONGS -> "Songs"
        DOWNLOADS -> "Downloads"
        PLAYLISTS -> "Playlists"
        RECENTLY_ADDED -> "Recently Added"
        GENERATIONS -> "Generations"
        RECENTLY_PLAYED -> "Recently Played"
        RANDOM_MIX -> "Random Mix"
        RANDOM_ALBUM -> "Random Album"
        CAROUSEL_RECENT -> "Recently Added Carousel"
        CAROUSEL_FREQUENT -> "Most Played Carousel"
        CAROUSEL_RANDOM -> "Random Picks Carousel"
        else -> id
    }
}

class AppState(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vibrdrome_prefs", Context.MODE_PRIVATE)
    @Volatile
    private var _securePrefs: SharedPreferences? = null
    private val securePrefs: SharedPreferences
        get() = _securePrefs ?: createSecurePrefs(context).also { _securePrefs = it }
    private val responseCache = ResponseCache(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    // Stream quality (0 = Original, or maxBitRate value: 320, 256, 192, 128, 96)
    private val _streamQualityWifi = MutableStateFlow(prefs.getInt("stream_quality_wifi", 0))
    val streamQualityWifi: StateFlow<Int> = _streamQualityWifi.asStateFlow()

    private val _streamQualityCellular = MutableStateFlow(prefs.getInt("stream_quality_cellular", 128))
    val streamQualityCellular: StateFlow<Int> = _streamQualityCellular.asStateFlow()

    fun setStreamQualityWifi(bitRate: Int) {
        _streamQualityWifi.value = bitRate
        prefs.edit().putInt("stream_quality_wifi", bitRate).apply()
    }

    fun setStreamQualityCellular(bitRate: Int) {
        _streamQualityCellular.value = bitRate
        prefs.edit().putInt("stream_quality_cellular", bitRate).apply()
    }

    /** Returns the effective maxBitRate based on current network, or 0 for Original quality. */
    fun getEffectiveStreamQuality(): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isWifi = cm?.activeNetwork?.let {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: true // Default to WiFi quality if we can't detect
        return if (isWifi) _streamQualityWifi.value else _streamQualityCellular.value
    }

    // Music library folder selection
    private val _musicFolders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val musicFolders: StateFlow<List<MusicFolder>> = _musicFolders.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    /** null = "All Libraries" */
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    val selectedFolderName: String
        get() {
            val id = _selectedFolderId.value ?: return "All Libraries"
            return _musicFolders.value.find { it.id == id }?.name ?: "All Libraries"
        }

    fun selectMusicFolder(folderId: String?) {
        _selectedFolderId.value = folderId
        val key = activeServerId?.let { "folder_$it" } ?: "selectedFolderId"
        if (folderId != null) {
            prefs.edit().putString(key, folderId).apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }

    fun loadMusicFolders() {
        scope.launch {
            try {
                val folders = subsonicClient.getMusicFolders()
                _musicFolders.value = folders
                // Restore saved selection for this server
                val key = activeServerId?.let { "folder_$it" } ?: "selectedFolderId"
                val savedId = prefs.getString(key, null)
                if (savedId != null && folders.any { it.id == savedId }) {
                    _selectedFolderId.value = savedId
                } else {
                    _selectedFolderId.value = null
                }
            } catch (_: Exception) {
                _musicFolders.value = emptyList()
            }
        }
    }

    // Library layout customization
    private val _libraryLayout = MutableStateFlow(loadLibraryLayout())
    val libraryLayout: StateFlow<List<LibraryItem>> = _libraryLayout.asStateFlow()

    private fun loadLibraryLayout(): List<LibraryItem> {
        val data = prefs.getString("library_layout", null)
        if (data != null) {
            try {
                val saved = Json.decodeFromString<List<LibraryItem>>(data)
                // Merge with defaults: keep saved order + visibility, append any new items
                val savedIds = saved.map { it.id }.toSet()
                val newItems = LibraryItemIds.DEFAULT_ORDER
                    .filter { it !in savedIds }
                    .map { LibraryItem(it) }
                return saved + newItems
            } catch (_: Exception) {}
        }
        return LibraryItemIds.DEFAULT_ORDER.map { LibraryItem(it) }
    }

    fun updateLibraryLayout(items: List<LibraryItem>) {
        _libraryLayout.value = items
        prefs.edit().putString("library_layout", Json.encodeToString(items)).apply()
    }

    fun resetLibraryLayout() {
        val defaults = LibraryItemIds.DEFAULT_ORDER.map { LibraryItem(it) }
        updateLibraryLayout(defaults)
    }

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadServers()
        // EncryptedSharedPreferences.create() does hardware-backed crypto key
        // generation which can block for seconds — move off the main thread
        scope.launch {
            _securePrefs = createSecurePrefs(context)
            loadSavedCredentials()
        }
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
        loadMusicFolders()
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
        val QUALITY_OPTIONS = listOf(
            0 to "Original",
            320 to "High (320 kbps)",
            256 to "Medium (256 kbps)",
            192 to "Standard (192 kbps)",
            128 to "Low (128 kbps)",
            96 to "Very Low (96 kbps)",
        )

        fun qualityLabel(bitRate: Int): String =
            QUALITY_OPTIONS.find { it.first == bitRate }?.second ?: "Original"

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

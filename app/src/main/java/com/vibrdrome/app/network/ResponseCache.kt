package com.vibrdrome.app.network

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

class ResponseCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "APIResponses").also { it.mkdirs() }
    private val timestamps = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    fun cacheKey(endpoint: SubsonicEndpoint): String {
        val params = endpoint.queryItems.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        return "${endpoint.path}?$params"
    }

    suspend fun data(key: String, ttlMs: Long): ByteArray? = mutex.withLock {
        val file = fileFor(key)
        if (!file.exists()) return null

        val ts = timestamps[key] ?: file.lastModified()
        if (System.currentTimeMillis() - ts > ttlMs) return null
        if (timestamps[key] == null) timestamps[key] = ts

        return file.readBytes()
    }

    suspend fun store(data: ByteArray, key: String) = mutex.withLock {
        val file = fileFor(key)
        file.writeBytes(data)
        timestamps[key] = System.currentTimeMillis()
    }

    suspend fun remove(key: String) = mutex.withLock {
        fileFor(key).delete()
        timestamps.remove(key)
    }

    suspend fun clearAll() = mutex.withLock {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        timestamps.clear()
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(key.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.json")
    }
}

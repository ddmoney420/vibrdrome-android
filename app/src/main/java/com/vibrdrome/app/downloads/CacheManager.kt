package com.vibrdrome.app.downloads

import android.content.Context
import com.vibrdrome.app.persistence.DownloadDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheManager(
    context: Context,
    private val dao: DownloadDao,
) {
    private val cacheDir = File(context.cacheDir, "APIResponses")
    private val downloadDir = File(context.filesDir, "downloads")

    var maxCacheSizeMb: Long = 500

    suspend fun currentCacheSizeMb(): Long = withContext(Dispatchers.IO) {
        currentCacheSizeBytes() / (1024 * 1024)
    }

    suspend fun currentCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val apiCache = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val downloads = downloadDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        apiCache + downloads
    }

    suspend fun currentDownloadSizeMb(): Long = withContext(Dispatchers.IO) {
        (dao.totalSize() ?: 0) / (1024 * 1024)
    }

    suspend fun evictApiCache() = withContext(Dispatchers.IO) {
        if (cacheDir.exists()) {
            cacheDir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?.forEach { it.delete() }
        }
    }

    suspend fun evictLRU() = withContext(Dispatchers.IO) {
        val maxBytes = maxCacheSizeMb * 1024 * 1024
        val files = downloadDir.listFiles()?.toMutableList() ?: return@withContext

        // Sort by last accessed (oldest first)
        files.sortBy { it.lastModified() }

        var totalSize = files.sumOf { it.length() }

        for (file in files) {
            if (totalSize <= maxBytes) break
            val fileSize = file.length()
            file.delete()

            // Remove from DB
            val songId = file.nameWithoutExtension
            dao.delete(songId)

            totalSize -= fileSize
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        // Clear API cache
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }

        // Clear downloads
        if (downloadDir.exists()) {
            downloadDir.deleteRecursively()
            downloadDir.mkdirs()
        }

        // Clear DB records
        // (handled by caller via DownloadDao)
    }
}

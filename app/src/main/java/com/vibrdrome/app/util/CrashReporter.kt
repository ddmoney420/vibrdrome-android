package com.vibrdrome.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val CRASH_DIR = "crash_logs"
    private const val TAG = "CrashReporter"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val deviceInfo = buildString {
                    appendLine("Timestamp: $timestamp")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("App: ${context.packageName} ${getVersionName(context)}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString())
                }
                val dir = File(context.filesDir, CRASH_DIR)
                dir.mkdirs()
                File(dir, "crash_$timestamp.txt").writeText(deviceInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLatestCrashLog(context: Context): String? {
        val dir = File(context.filesDir, CRASH_DIR)
        if (!dir.exists()) return null
        val latest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
        return latest.readText()
    }

    fun clearCrashLogs(context: Context) {
        val dir = File(context.filesDir, CRASH_DIR)
        dir.listFiles()?.forEach { it.delete() }
    }

    fun hasCrashLog(context: Context): Boolean {
        val dir = File(context.filesDir, CRASH_DIR)
        return dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
    }

    private fun getVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }
}

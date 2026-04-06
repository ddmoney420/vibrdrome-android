package com.vibrdrome.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import com.vibrdrome.app.MainActivity
import com.vibrdrome.app.R
import com.vibrdrome.app.audio.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URL

/**
 * Traditional AppWidgetProvider — reliable widget updates via RemoteViews.
 * Replaces the Glance-based widget which had rendering issues on some devices.
 */
class NowPlayingWidgetProvider : AppWidgetProvider(), KoinComponent {

    private val playbackManager: PlaybackManager by inject()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                playbackManager.togglePlayPause()
                refreshAll(context)
            }
            ACTION_NEXT -> {
                playbackManager.next()
                // Delay refresh for track change to settle
                CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                    kotlinx.coroutines.delay(500)
                    refreshAll(context)
                }
            }
            ACTION_PREVIOUS -> {
                playbackManager.previous()
                CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                    kotlinx.coroutines.delay(500)
                    refreshAll(context)
                }
            }
        }
    }

    private fun updateWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
        val song = playbackManager.currentSong.value
        val isPlaying = playbackManager.isPlaying.value
        val artUrl = playbackManager.currentCoverArtUrl.value

        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)

        // Track info
        views.setTextViewText(R.id.widget_title, song?.title ?: "Not playing")
        views.setTextViewText(R.id.widget_artist, song?.artist ?: "")

        // Play/pause icon
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        )

        // Button intents
        views.setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.widget_prev, actionIntent(context, ACTION_PREVIOUS))

        // Tap widget body → open app
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openIntent)

        // Set album art async
        if (artUrl != null) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                val bitmap = loadBitmap(artUrl)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_art, bitmap)
                } else {
                    views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
                }
                awm.updateAppWidget(widgetId, views)
            }
        } else {
            views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
            awm.updateAppWidget(widgetId, views)
        }
    }

    private fun actionIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(action).apply {
            component = ComponentName(context, NowPlayingWidgetProvider::class.java)
        }
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun loadBitmap(url: String): Bitmap? {
        return try {
            val stream = URL(url).openStream()
            val full = BitmapFactory.decodeStream(stream)
            stream.close()
            if (full != null) Bitmap.createScaledBitmap(full, 200, 200, true) else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.vibrdrome.app.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.vibrdrome.app.widget.NEXT"
        const val ACTION_PREVIOUS = "com.vibrdrome.app.widget.PREVIOUS"

        /** Call this to refresh all widget instances from anywhere in the app. */
        fun refreshAll(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, NowPlayingWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val intent = Intent(context, NowPlayingWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
            Log.d("WidgetProvider", "Refreshing ${ids.size} widgets")
        }
    }
}

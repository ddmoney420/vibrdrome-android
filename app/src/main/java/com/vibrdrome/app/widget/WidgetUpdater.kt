package com.vibrdrome.app.widget

import android.content.Context
import android.util.Log
import com.vibrdrome.app.audio.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Observes PlaybackManager state and triggers widget refresh on changes.
 */
class WidgetUpdater(
    private val context: Context,
    private val playbackManager: PlaybackManager,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun start() {
        scope.launch {
            playbackManager.currentSong.collect {
                Log.d("WidgetUpdater", "Song changed: ${it?.title}")
                NowPlayingWidgetProvider.refreshAll(context)
            }
        }
        scope.launch {
            playbackManager.isPlaying.collect {
                Log.d("WidgetUpdater", "Playing changed: $it")
                NowPlayingWidgetProvider.refreshAll(context)
            }
        }
    }
}

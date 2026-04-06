package com.vibrdrome.app

import android.app.Application
import com.vibrdrome.app.audio.HapticEngine
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.SleepTimer
import com.vibrdrome.app.persistence.NetworkMonitor
import com.vibrdrome.app.di.appModule
import com.vibrdrome.app.widget.WidgetUpdater
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VibrdromeApp : Application() {
    private val playbackManager: PlaybackManager by inject()
    private val sleepTimer: SleepTimer by inject()
    private val widgetUpdater: WidgetUpdater by inject()
    private val hapticEngine: HapticEngine by inject()
    private val networkMonitor: NetworkMonitor by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VibrdromeApp)
            modules(appModule)
        }
        widgetUpdater.start()
        playbackManager.onTrackChanged = { hapticEngine.reset() }
        networkMonitor.start()
    }

    override fun onTerminate() {
        networkMonitor.stop()
        playbackManager.release()
        sleepTimer.release()
        super.onTerminate()
    }
}

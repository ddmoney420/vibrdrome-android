package com.vibrdrome.app

import android.app.Application
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.SleepTimer
import com.vibrdrome.app.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VibrdromeApp : Application() {
    private val playbackManager: PlaybackManager by inject()
    private val sleepTimer: SleepTimer by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VibrdromeApp)
            modules(appModule)
        }
    }

    override fun onTerminate() {
        playbackManager.release()
        sleepTimer.release()
        super.onTerminate()
    }
}

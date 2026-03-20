package com.vibrdrome.app

import android.app.Application
import com.vibrdrome.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VibrdromeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@VibrdromeApp)
            modules(appModule)
        }
    }
}

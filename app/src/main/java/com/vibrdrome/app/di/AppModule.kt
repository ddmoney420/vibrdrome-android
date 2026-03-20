package com.vibrdrome.app.di

import androidx.room.Room
import com.vibrdrome.app.audio.EQCoefficientsStore
import com.vibrdrome.app.audio.EQEngine
import com.vibrdrome.app.audio.PlaybackManager
import com.vibrdrome.app.audio.RadioManager
import com.vibrdrome.app.audio.SleepTimer
import com.vibrdrome.app.persistence.OfflineActionQueue
import com.vibrdrome.app.downloads.CacheManager
import com.vibrdrome.app.downloads.DownloadManager
import com.vibrdrome.app.persistence.AppDatabase
import com.vibrdrome.app.ui.AppState
import org.koin.dsl.module

val appModule = module {
    single { AppState(get()) }
    single {
        Room.databaseBuilder(get(), AppDatabase::class.java, "vibrdrome.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }
    single { get<AppDatabase>().playbackStateDao() }
    single { get<AppDatabase>().downloadDao() }
    single { get<AppDatabase>().pendingActionDao() }
    single { OfflineActionQueue(get()) }
    single { EQCoefficientsStore() }
    single { SleepTimer() }
    single { EQEngine(get(), get()) }
    single { PlaybackManager(get(), get(), get(), get(), get(), get()) }
    single { DownloadManager(get(), get(), get()) }
    single { RadioManager() }
    single { CacheManager(get(), get()) }
}

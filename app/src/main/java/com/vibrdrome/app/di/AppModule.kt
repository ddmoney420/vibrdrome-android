package com.vibrdrome.app.di

import com.vibrdrome.app.ui.AppState
import org.koin.dsl.module

val appModule = module {
    single { AppState(get()) }
}

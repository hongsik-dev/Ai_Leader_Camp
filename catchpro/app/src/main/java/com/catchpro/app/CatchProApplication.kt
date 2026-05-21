package com.catchpro.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CatchProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FlavorNaverMapInitializer.initialize(this)
    }
}

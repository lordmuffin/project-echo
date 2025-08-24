package com.projectecho.watch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WatchApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
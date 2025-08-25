package com.projectecho

import android.app.Application
import android.util.Log
import com.projectecho.phone.BuildConfig
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Project Echo Phone module.
 * Handles app-level initialization and configuration.
 */
@HiltAndroidApp
class ProjectEchoApplication : Application() {
    companion object {
        private const val TAG = "ProjectEchoPhoneApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Project Echo Phone application started")
        
        // Initialize app-level components if needed
        initializeLogging()
    }
    
    private fun initializeLogging() {
        // Configure logging for release builds
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Debug logging enabled")
        }
    }
}
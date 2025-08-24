package com.projectecho

import android.app.Application
import android.util.Log

/**
 * Application class for Project Echo.
 * Handles app-level initialization and configuration.
 */
class ProjectEchoApplication : Application() {
    companion object {
        private const val TAG = "ProjectEchoApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Project Echo application started")
        
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
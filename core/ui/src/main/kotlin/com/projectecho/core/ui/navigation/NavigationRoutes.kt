package com.projectecho.core.ui.navigation

/**
 * Centralized navigation routes for type-safe navigation across the app.
 * Provides consistent route naming and prevents typos in navigation code.
 */
object NavigationRoutes {
    
    // Main application routes
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val RECORDINGS_LIST = "recordings_list"
    const val RECORDING_DETAIL = "recording_detail"
    
    // Watch-specific routes
    const val WATCH_HOME = "watch_home"
    const val WATCH_SETTINGS = "watch_settings"
    const val WATCH_RECORDINGS = "watch_recordings"
    
    // Audio recording routes
    const val RECORDING_SCREEN = "recording_screen"
    const val RECORDING_PLAYBACK = "recording_playback"
    
    // Permission and onboarding routes
    const val PERMISSIONS = "permissions"
    const val ONBOARDING = "onboarding"
    
    // Nested navigation graphs
    const val AUTH_GRAPH = "auth_graph"
    const val MAIN_GRAPH = "main_graph"
    const val SETTINGS_GRAPH = "settings_graph"
}
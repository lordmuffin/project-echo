package com.projectecho.watch.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.projectecho.core.ui.navigation.NavigationRoutes
import com.projectecho.core.ui.navigation.NavigationArgs
import com.projectecho.core.ui.navigation.navigateWithArgs
import com.projectecho.watch.presentation.recording.WatchRecordingScreen

@Composable
fun WatchNavigation(
    navController: NavHostController = rememberSwipeDismissableNavController()
) {
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = NavigationRoutes.WATCH_HOME
    ) {
        composable(NavigationRoutes.WATCH_HOME) {
            WatchRecordingScreen(
                onNavigateToSettings = {
                    navController.navigateWithArgs(
                        NavigationArgs.SettingsArgs()
                    )
                },
                onNavigateToRecordings = {
                    navController.navigateWithArgs(
                        NavigationArgs.RecordingsListArgs()
                    )
                }
            )
        }
        
        composable("${NavigationRoutes.SETTINGS}?section={section}") {
            // Settings screen will be implemented later
            // Type-safe args can be extracted using NavigationArgs.SettingsArgs.fromSavedStateHandle()
        }
        
        composable("${NavigationRoutes.RECORDINGS_LIST}?sortBy={sortBy}&filterBy={filterBy}&searchQuery={searchQuery}") {
            // Recordings list screen will be implemented later
            // Type-safe args can be extracted using NavigationArgs.RecordingsListArgs.fromSavedStateHandle()
        }
    }
}
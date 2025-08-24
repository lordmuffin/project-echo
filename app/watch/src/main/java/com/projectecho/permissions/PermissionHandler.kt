package com.projectecho.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles microphone permission requests gracefully for Wear OS.
 * Provides clear user feedback and handles permission denial scenarios.
 * 
 * Features:
 * - Graceful permission request flow
 * - User-friendly permission explanations  
 * - State management for permission status
 * - Retry mechanisms for denied permissions
 * - Wear OS optimized UI interactions
 */
class PermissionHandler(private val context: Context) {
    companion object {
        private const val TAG = "PermissionHandler"
        const val MICROPHONE_PERMISSION_REQUEST_CODE = 1001
        private const val PERMISSION_RATIONALE_SHOWN_KEY = "microphone_rationale_shown"
    }
    
    // Permission state
    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    
    // Shared preferences for tracking rationale
    private val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)
    
    /**
     * Check if microphone permission is granted
     */
    fun hasAudioPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        _permissionState.value = _permissionState.value.copy(
            hasMicrophonePermission = hasPermission
        )
        
        return hasPermission
    }
    
    /**
     * Request microphone permission with proper flow
     */
    fun requestAudioPermission(activity: Activity) {
        if (hasAudioPermission()) {
            Log.d(TAG, "Microphone permission already granted")
            return
        }
        
        // Check if we should show rationale
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.RECORD_AUDIO
        )
        
        val rationaleShownBefore = prefs.getBoolean(PERMISSION_RATIONALE_SHOWN_KEY, false)
        
        _permissionState.value = _permissionState.value.copy(
            isRequestingPermission = true,
            shouldShowRationale = shouldShowRationale || !rationaleShownBefore,
            permissionDeniedPermanently = !shouldShowRationale && rationaleShownBefore
        )
        
        if (shouldShowRationale || !rationaleShownBefore) {
            // Show rationale first, then request permission
            showPermissionRationale(activity)
        } else {
            // Request permission directly
            requestPermissionDirect(activity)
        }
    }
    
    /**
     * Show permission rationale to user
     */
    private fun showPermissionRationale(activity: Activity) {
        _permissionState.value = _permissionState.value.copy(
            showingRationale = true,
            rationaleMessage = "This app needs microphone access to record audio. " +
                    "Grant permission to start recording your voice notes and meetings."
        )
        
        // Mark that we've shown the rationale
        prefs.edit().putBoolean(PERMISSION_RATIONALE_SHOWN_KEY, true).apply()
    }
    
    /**
     * Proceed with permission request after rationale
     */
    fun proceedWithPermissionRequest(activity: Activity) {
        _permissionState.value = _permissionState.value.copy(showingRationale = false)
        requestPermissionDirect(activity)
    }
    
    /**
     * Cancel permission request
     */
    fun cancelPermissionRequest() {
        _permissionState.value = _permissionState.value.copy(
            isRequestingPermission = false,
            showingRationale = false,
            permissionRequestCancelled = true
        )
    }
    
    /**
     * Request permission directly from system
     */
    private fun requestPermissionDirect(activity: Activity) {
        Log.d(TAG, "Requesting microphone permission")
        
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MICROPHONE_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * Handle permission request result
     */
    fun onPermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode != MICROPHONE_PERMISSION_REQUEST_CODE) {
            return
        }
        
        val isGranted = grantResults.isNotEmpty() && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Permission result: ${if (isGranted) "granted" else "denied"}")
        
        _permissionState.value = _permissionState.value.copy(
            isRequestingPermission = false,
            hasMicrophonePermission = isGranted,
            permissionJustGranted = isGranted,
            permissionJustDenied = !isGranted,
            permissionDeniedPermanently = !isGranted && !ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity, Manifest.permission.RECORD_AUDIO
            )
        )
        
        if (isGranted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }
    
    /**
     * Handle permission granted
     */
    private fun onPermissionGranted() {
        Log.d(TAG, "Microphone permission granted")
        _permissionState.value = _permissionState.value.copy(
            statusMessage = "Microphone access granted. You can now start recording."
        )
    }
    
    /**
     * Handle permission denied
     */
    private fun onPermissionDenied() {
        Log.d(TAG, "Microphone permission denied")
        
        val isPermanentDenial = _permissionState.value.permissionDeniedPermanently
        
        _permissionState.value = _permissionState.value.copy(
            statusMessage = if (isPermanentDenial) {
                "Microphone access permanently denied. Enable in Settings > Apps > Permissions."
            } else {
                "Microphone access denied. Recording features will not work."
            }
        )
    }
    
    /**
     * Clear permission just granted/denied flags
     */
    fun clearPermissionFlags() {
        _permissionState.value = _permissionState.value.copy(
            permissionJustGranted = false,
            permissionJustDenied = false,
            permissionRequestCancelled = false
        )
    }
    
    /**
     * Open app settings for manual permission grant
     */
    fun openAppSettings(activity: Activity) {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            
            _permissionState.value = _permissionState.value.copy(
                statusMessage = "Enable microphone permission in settings to start recording."
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            _permissionState.value = _permissionState.value.copy(
                statusMessage = "Please enable microphone permission in your device settings."
            )
        }
    }
    
    /**
     * Check if permission is critically needed for functionality
     */
    fun isPermissionCritical(): Boolean {
        return !hasAudioPermission()
    }
    
    /**
     * Get user-friendly permission status message
     */
    fun getPermissionStatusMessage(): String {
        return when {
            hasAudioPermission() -> "Microphone access granted"
            _permissionState.value.permissionDeniedPermanently -> 
                "Microphone access disabled. Enable in settings to record."
            _permissionState.value.isRequestingPermission -> 
                "Requesting microphone permission..."
            else -> "Microphone permission required to record audio"
        }
    }
    
    /**
     * Reset permission state
     */
    fun reset() {
        _permissionState.value = PermissionState()
    }
}

/**
 * Permission state data class
 */
data class PermissionState(
    val hasMicrophonePermission: Boolean = false,
    val isRequestingPermission: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val showingRationale: Boolean = false,
    val permissionJustGranted: Boolean = false,
    val permissionJustDenied: Boolean = false,
    val permissionDeniedPermanently: Boolean = false,
    val permissionRequestCancelled: Boolean = false,
    val rationaleMessage: String = "",
    val statusMessage: String = ""
)
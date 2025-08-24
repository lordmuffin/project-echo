package com.projectecho.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectecho.audio.AudioFocusManager
import com.projectecho.audio.AudioRecordManager
import com.projectecho.audio.HealthMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Controller for managing recording UI state and user interactions.
 * Implements Story 1: Quick Recording Start with one-tap recording and haptic feedback.
 * 
 * Features:
 * - One-tap recording start/stop
 * - Haptic feedback for user confirmation
 * - Real-time recording state management
 * - Health monitoring integration
 * - Error handling with user notifications
 */
class RecordingController(private val context: Context) : ViewModel() {
    companion object {
        private const val TAG = "RecordingController"
        private const val RECORDINGS_DIR = "recordings"
        
        // Haptic feedback patterns
        private const val START_VIBRATION_MS = 50L
        private const val STOP_VIBRATION_MS = 100L
        private const val ERROR_VIBRATION_MS = 200L
    }
    
    // Audio components
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var audioRecordManager: AudioRecordManager
    private var isInitialized = false
    
    // Haptic feedback
    private val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    // UI State
    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()
    
    // Recording state
    private var currentRecordingFile: File? = null
    private val recordingsDir by lazy {
        File(context.filesDir, RECORDINGS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    init {
        initializeAudioComponents()
    }
    
    /**
     * Initialize audio recording components for instant readiness (Story 1)
     */
    private fun initializeAudioComponents() {
        try {
            audioFocusManager = AudioFocusManager(context).apply {
                setOnFocusLost {
                    viewModelScope.launch {
                        handleFocusLoss(permanent = true)
                    }
                }
                setOnFocusLostTransient {
                    viewModelScope.launch {
                        handleFocusLoss(permanent = false)
                    }
                }
                setOnFocusGained {
                    viewModelScope.launch {
                        handleFocusGained()
                    }
                }
            }
            
            audioRecordManager = AudioRecordManager(context, audioFocusManager).apply {
                setOnRecordingStarted {
                    _uiState.value = _uiState.value.copy(
                        isRecording = true,
                        isPaused = false,
                        statusMessage = "Recording started"
                    )
                }
                
                setOnRecordingStopped {
                    _uiState.value = _uiState.value.copy(
                        isRecording = false,
                        isPaused = false,
                        statusMessage = "Recording stopped"
                    )
                }
                
                setOnError { error ->
                    handleRecordingError(error)
                }
                
                setOnHealthUpdate { metrics ->
                    updateHealthMetrics(metrics)
                }
            }
            
            isInitialized = true
            Log.d(TAG, "Audio components initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio components", e)
            _uiState.value = _uiState.value.copy(
                statusMessage = "Failed to initialize: ${e.message}",
                hasError = true
            )
        }
    }
    
    /**
     * One-tap recording toggle (Story 1: Quick Recording Start)
     */
    fun toggleRecording() {
        viewModelScope.launch {
            if (!isInitialized) {
                showError("Audio system not initialized")
                return@launch
            }
            
            try {
                if (audioRecordManager.isRecording()) {
                    stopRecording()
                } else {
                    startRecording()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling recording", e)
                showError("Recording error: ${e.message}")
            }
        }
    }
    
    /**
     * Start recording with haptic feedback
     */
    private suspend fun startRecording() {
        try {
            // Provide haptic feedback for start
            provideHapticFeedback(HapticType.START)
            
            // Create recording file
            currentRecordingFile = createRecordingFile()
            
            // Update UI state
            _uiState.value = _uiState.value.copy(
                isStarting = true,
                statusMessage = "Starting recording..."
            )
            
            // Start recording
            val success = audioRecordManager.startRecording(currentRecordingFile!!)
            
            if (success) {
                Log.d(TAG, "Recording started successfully")
                _uiState.value = _uiState.value.copy(
                    isStarting = false,
                    recordingStartTime = System.currentTimeMillis()
                )
            } else {
                Log.e(TAG, "Failed to start recording")
                _uiState.value = _uiState.value.copy(
                    isStarting = false,
                    statusMessage = "Failed to start recording",
                    hasError = true
                )
                provideHapticFeedback(HapticType.ERROR)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _uiState.value = _uiState.value.copy(
                isStarting = false,
                statusMessage = "Error starting recording: ${e.message}",
                hasError = true
            )
            provideHapticFeedback(HapticType.ERROR)
        }
    }
    
    /**
     * Stop recording with haptic feedback
     */
    private suspend fun stopRecording() {
        try {
            // Provide haptic feedback for stop
            provideHapticFeedback(HapticType.STOP)
            
            // Update UI state
            _uiState.value = _uiState.value.copy(
                isStopping = true,
                statusMessage = "Stopping recording..."
            )
            
            // Stop recording
            val success = audioRecordManager.stopRecording()
            
            if (success) {
                Log.d(TAG, "Recording stopped successfully")
                val duration = System.currentTimeMillis() - _uiState.value.recordingStartTime
                _uiState.value = _uiState.value.copy(
                    isStopping = false,
                    lastRecordingDuration = duration,
                    statusMessage = "Recording saved (${formatDuration(duration)})"
                )
            } else {
                Log.e(TAG, "Failed to stop recording")
                _uiState.value = _uiState.value.copy(
                    isStopping = false,
                    statusMessage = "Error stopping recording",
                    hasError = true
                )
                provideHapticFeedback(HapticType.ERROR)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            _uiState.value = _uiState.value.copy(
                isStopping = false,
                statusMessage = "Error stopping recording: ${e.message}",
                hasError = true
            )
            provideHapticFeedback(HapticType.ERROR)
        }
    }
    
    /**
     * Pause recording (maintains buffer state)
     */
    fun pauseRecording() {
        if (audioRecordManager.isRecording() && !audioRecordManager.isPaused()) {
            audioRecordManager.pauseRecording()
            _uiState.value = _uiState.value.copy(
                isPaused = true,
                statusMessage = "Recording paused"
            )
            provideHapticFeedback(HapticType.START)
            Log.d(TAG, "Recording paused")
        }
    }
    
    /**
     * Resume recording
     */
    fun resumeRecording() {
        if (audioRecordManager.isRecording() && audioRecordManager.isPaused()) {
            audioRecordManager.resumeRecording()
            _uiState.value = _uiState.value.copy(
                isPaused = false,
                statusMessage = "Recording resumed"
            )
            provideHapticFeedback(HapticType.START)
            Log.d(TAG, "Recording resumed")
        }
    }
    
    /**
     * Handle audio focus loss
     */
    private suspend fun handleFocusLoss(permanent: Boolean) {
        if (audioRecordManager.isRecording()) {
            if (permanent) {
                Log.d(TAG, "Permanent audio focus loss, stopping recording")
                stopRecording()
                showError("Recording stopped: Another app is using audio")
            } else {
                Log.d(TAG, "Temporary audio focus loss, pausing recording")
                pauseRecording()
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Recording paused: Audio interruption"
                )
            }
        }
    }
    
    /**
     * Handle audio focus gained
     */
    private suspend fun handleFocusGained() {
        if (audioRecordManager.isRecording() && audioRecordManager.isPaused()) {
            Log.d(TAG, "Audio focus regained, can resume recording")
            _uiState.value = _uiState.value.copy(
                statusMessage = "Audio focus regained, tap to resume"
            )
        }
    }
    
    /**
     * Handle recording errors
     */
    private fun handleRecordingError(error: String) {
        Log.e(TAG, "Recording error: $error")
        _uiState.value = _uiState.value.copy(
            isRecording = false,
            isPaused = false,
            isStarting = false,
            isStopping = false,
            statusMessage = "Error: $error",
            hasError = true
        )
        provideHapticFeedback(HapticType.ERROR)
    }
    
    /**
     * Update health metrics in UI state
     */
    private fun updateHealthMetrics(metrics: HealthMetrics) {
        _uiState.value = _uiState.value.copy(
            healthMetrics = metrics,
            bytesRecorded = metrics.bytesRecorded
        )
    }
    
    /**
     * Show error message
     */
    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            statusMessage = message,
            hasError = true
        )
        provideHapticFeedback(HapticType.ERROR)
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(hasError = false)
    }
    
    /**
     * Provide haptic feedback based on action type
     */
    private fun provideHapticFeedback(type: HapticType) {
        try {
            val duration = when (type) {
                HapticType.START -> START_VIBRATION_MS
                HapticType.STOP -> STOP_VIBRATION_MS
                HapticType.ERROR -> ERROR_VIBRATION_MS
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to provide haptic feedback", e)
        }
    }
    
    /**
     * Create a new recording file with timestamp
     */
    private fun createRecordingFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())
        return File(recordingsDir, "recording_$timestamp.wav")
    }
    
    /**
     * Format duration for display
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
    }
    
    /**
     * Get current recording duration
     */
    fun getCurrentDuration(): Long {
        return if (_uiState.value.isRecording) {
            System.currentTimeMillis() - _uiState.value.recordingStartTime
        } else {
            0L
        }
    }
    
    /**
     * Clean up resources
     */
    override fun onCleared() {
        super.onCleared()
        if (::audioRecordManager.isInitialized) {
            audioRecordManager.release()
        }
        Log.d(TAG, "RecordingController cleared")
    }
}

/**
 * UI state data class
 */
data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isStarting: Boolean = false,
    val isStopping: Boolean = false,
    val statusMessage: String = "Ready to record",
    val hasError: Boolean = false,
    val recordingStartTime: Long = 0L,
    val lastRecordingDuration: Long = 0L,
    val bytesRecorded: Long = 0L,
    val healthMetrics: HealthMetrics? = null
)

/**
 * Haptic feedback types
 */
private enum class HapticType {
    START, STOP, ERROR
}
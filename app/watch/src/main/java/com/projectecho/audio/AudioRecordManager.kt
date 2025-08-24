package com.projectecho.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Robust AudioRecord manager optimized for Wear OS devices.
 * Provides reliable, high-quality audio capture with proper buffer management
 * and error handling to prevent audio glitches during extended recording sessions.
 * 
 * Features:
 * - 44.1kHz sample rate, 16-bit PCM mono
 * - 10x minimum buffer size for stability  
 * - Graceful audio focus handling
 * - Error recovery for recording interruptions
 * - Health monitoring and metrics logging
 */
class AudioRecordManager(
    private val context: Context,
    private val audioFocusManager: AudioFocusManager
) {
    companion object {
        private const val TAG = "AudioRecordManager"
        
        // Audio configuration optimized for Wear OS
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
        
        // Buffer configuration for stability
        private const val BUFFER_SIZE_MULTIPLIER = 10
        
        // Health monitoring constants
        private const val HEALTH_CHECK_INTERVAL_MS = 1000L
        private const val MAX_BUFFER_OVERRUN_COUNT = 3
        private const val RECOVERY_DELAY_MS = 100L
    }
    
    // Audio recording components
    private var audioRecord: AudioRecord? = null
    private var recordingBuffer: CircularAudioBuffer? = null
    private var isRecording = AtomicBoolean(false)
    private var isPaused = AtomicBoolean(false)
    
    // Recording state
    private var recordingJob: Job? = null
    private var healthMonitorJob: Job? = null
    private var currentOutputFile: File? = null
    
    // Health monitoring
    private val bytesRecorded = AtomicLong(0)
    private val bufferOverrunCount = AtomicLong(0)
    private val lastHealthCheck = AtomicLong(0)
    
    // Callbacks
    private var onRecordingStarted: (() -> Unit)? = null
    private var onRecordingStopped: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onHealthUpdate: ((HealthMetrics) -> Unit)? = null
    
    // Configuration
    private val minBufferSize by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    }
    
    private val optimalBufferSize by lazy {
        minBufferSize * BUFFER_SIZE_MULTIPLIER
    }
    
    init {
        Log.d(TAG, "AudioRecordManager initialized with buffer size: $optimalBufferSize bytes")
        initializeAudioRecord()
    }
    
    /**
     * Initialize AudioRecord with optimal configuration for Wear OS
     */
    private fun initializeAudioRecord() {
        try {
            if (!hasAudioPermission()) {
                throw SecurityException("Audio recording permission not granted")
            }
            
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw IllegalStateException("Invalid audio configuration")
            }
            
            @SuppressLint("MissingPermission") // Permission is checked above and declared in manifest
            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                optimalBufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    throw IllegalStateException("Failed to initialize AudioRecord")
                }
            }
            
            recordingBuffer = CircularAudioBuffer(optimalBufferSize * 2)
            Log.d(TAG, "AudioRecord initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            onError?.invoke("Failed to initialize audio recording: ${e.message}")
        }
    }
    
    /**
     * Start recording with immediate readiness (Story 1: Quick Recording Start)
     */
    suspend fun startRecording(outputFile: File): Boolean {
        return withContext(Dispatchers.Main) {
            if (isRecording.get()) {
                Log.w(TAG, "Recording already in progress")
                return@withContext false
            }
            
            if (!hasAudioPermission()) {
                onError?.invoke("Microphone permission not granted")
                return@withContext false
            }
            
            try {
                currentOutputFile = outputFile
                
                // Request audio focus before starting
                if (!audioFocusManager.requestAudioFocus()) {
                    onError?.invoke("Failed to acquire audio focus")
                    return@withContext false
                }
                
                // Ensure AudioRecord is properly initialized
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    initializeAudioRecord()
                }
                
                audioRecord?.let { record ->
                    record.startRecording()
                    
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        throw IllegalStateException("Failed to start recording")
                    }
                    
                    isRecording.set(true)
                    isPaused.set(false)
                    bytesRecorded.set(0)
                    bufferOverrunCount.set(0)
                    
                    // Start recording coroutine
                    recordingJob = CoroutineScope(Dispatchers.IO).launch {
                        recordAudioLoop()
                    }
                    
                    // Start health monitoring
                    healthMonitorJob = CoroutineScope(Dispatchers.IO).launch {
                        monitorRecordingHealth()
                    }
                    
                    onRecordingStarted?.invoke()
                    Log.d(TAG, "Recording started successfully")
                    return@withContext true
                }
                
                return@withContext false
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                onError?.invoke("Failed to start recording: ${e.message}")
                cleanup()
                return@withContext false
            }
        }
    }
    
    /**
     * Stop recording and save to file
     */
    suspend fun stopRecording(): Boolean {
        return withContext(Dispatchers.Main) {
            if (!isRecording.get()) {
                Log.w(TAG, "No recording in progress")
                return@withContext false
            }
            
            try {
                isRecording.set(false)
                
                // Stop recording jobs
                recordingJob?.cancel()
                healthMonitorJob?.cancel()
                
                // Stop AudioRecord
                audioRecord?.let { record ->
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                }
                
                // Save buffered audio to file
                val savedBytes = saveBufferToFile()
                
                // Release audio focus
                audioFocusManager.abandonAudioFocus()
                
                onRecordingStopped?.invoke()
                Log.d(TAG, "Recording stopped, saved $savedBytes bytes")
                
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                onError?.invoke("Error stopping recording: ${e.message}")
                return@withContext false
            } finally {
                cleanup()
            }
        }
    }
    
    /**
     * Pause recording (maintains buffer and AudioRecord state)
     */
    fun pauseRecording() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true)
            Log.d(TAG, "Recording paused")
        }
    }
    
    /**
     * Resume recording
     */
    fun resumeRecording() {
        if (isRecording.get() && isPaused.get()) {
            isPaused.set(false)
            Log.d(TAG, "Recording resumed")
        }
    }
    
    /**
     * Main audio recording loop (Story 2: Long Recording Stability)
     */
    private suspend fun recordAudioLoop() {
        val buffer = ByteArray(minBufferSize)
        
        while (isRecording.get()) {
            try {
                if (isPaused.get()) {
                    delay(50) // Short delay when paused
                    continue
                }
                
                audioRecord?.let { record ->
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    
                    when {
                        bytesRead > 0 -> {
                            // Successfully read audio data
                            recordingBuffer?.write(buffer, 0, bytesRead)
                            bytesRecorded.addAndGet(bytesRead.toLong())
                        }
                        bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.e(TAG, "Invalid operation during recording")
                            handleRecordingError("Invalid recording operation")
                            return
                        }
                        bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                            Log.e(TAG, "Bad value during recording")
                            handleRecordingError("Bad recording parameters")
                            return
                        }
                        else -> {
                            Log.w(TAG, "Unexpected read result: $bytesRead")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
                handleRecordingError("Recording loop error: ${e.message}")
                return
            }
        }
    }
    
    /**
     * Monitor recording health and handle errors (Story 2: Long Recording Stability)
     */
    private suspend fun monitorRecordingHealth() {
        while (isRecording.get()) {
            delay(HEALTH_CHECK_INTERVAL_MS)
            
            try {
                val currentTime = System.currentTimeMillis()
                val currentBytes = bytesRecorded.get()
                val overrunCount = bufferOverrunCount.get()
                
                // Check for buffer overruns
                recordingBuffer?.let { buffer ->
                    if (buffer.isNearCapacity()) {
                        bufferOverrunCount.incrementAndGet()
                        Log.w(TAG, "Buffer nearing capacity, overrun count: ${bufferOverrunCount.get()}")
                        
                        if (bufferOverrunCount.get() >= MAX_BUFFER_OVERRUN_COUNT) {
                            handleRecordingError("Too many buffer overruns")
                            return
                        }
                    }
                }
                
                // Create health metrics
                val metrics = HealthMetrics(
                    bytesRecorded = currentBytes,
                    bufferOverruns = overrunCount,
                    isHealthy = overrunCount < MAX_BUFFER_OVERRUN_COUNT,
                    timestamp = currentTime
                )
                
                onHealthUpdate?.invoke(metrics)
                lastHealthCheck.set(currentTime)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in health monitoring", e)
            }
        }
    }
    
    /**
     * Handle recording errors with recovery attempts
     */
    private suspend fun handleRecordingError(error: String) {
        Log.e(TAG, "Recording error: $error")
        
        try {
            // Attempt recovery
            delay(RECOVERY_DELAY_MS)
            
            if (isRecording.get()) {
                // Save partial recording
                savePartialRecording()
                
                // Try to reinitialize
                audioRecord?.let { record ->
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                }
                
                initializeAudioRecord()
                
                audioRecord?.let { record ->
                    record.startRecording()
                    Log.d(TAG, "Recovery attempt completed")
                    return
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed", e)
        }
        
        // Recovery failed, stop recording
        onError?.invoke(error)
        stopRecording()
    }
    
    /**
     * Save partial recording on unexpected termination (Story 2)
     */
    private fun savePartialRecording() {
        try {
            currentOutputFile?.let { file ->
                val partialFile = File(file.parent, "${file.nameWithoutExtension}_partial.wav")
                saveBufferToFile(partialFile)
                Log.d(TAG, "Partial recording saved to: ${partialFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save partial recording", e)
        }
    }
    
    /**
     * Save audio buffer to file
     */
    private fun saveBufferToFile(outputFile: File? = null): Long {
        val file = outputFile ?: currentOutputFile ?: return 0L
        
        return try {
            recordingBuffer?.let { buffer ->
                FileOutputStream(file).use { output ->
                    // Write WAV header
                    writeWavHeader(output, buffer.size().toLong())
                    
                    // Write audio data
                    val audioData = buffer.readAll()
                    output.write(audioData)
                    output.flush()
                    
                    Log.d(TAG, "Audio saved to: ${file.absolutePath}, size: ${audioData.size}")
                    audioData.size.toLong()
                }
            } ?: 0L
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save audio file", e)
            onError?.invoke("Failed to save recording: ${e.message}")
            0L
        }
    }
    
    /**
     * Write WAV file header
     */
    private fun writeWavHeader(output: FileOutputStream, audioDataLength: Long) {
        val header = ByteArray(44)
        val totalDataLen = audioDataLength + 36
        val bitrate = SAMPLE_RATE * 1 * 16
        
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        header[16] = 16  // PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        header[20] = 1   // PCM format
        header[21] = 0
        
        header[22] = 1   // Mono
        header[23] = 0
        
        header[24] = (SAMPLE_RATE and 0xff).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        
        header[28] = (bitrate / 8 and 0xff).toByte()
        header[29] = ((bitrate / 8 shr 8) and 0xff).toByte()
        header[30] = ((bitrate / 8 shr 16) and 0xff).toByte()
        header[31] = ((bitrate / 8 shr 24) and 0xff).toByte()
        
        header[32] = 2   // Block align
        header[33] = 0
        
        header[34] = 16  // Bits per sample
        header[35] = 0
        
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        header[40] = (audioDataLength and 0xff).toByte()
        header[41] = ((audioDataLength shr 8) and 0xff).toByte()
        header[42] = ((audioDataLength shr 16) and 0xff).toByte()
        header[43] = ((audioDataLength shr 24) and 0xff).toByte()
        
        output.write(header)
    }
    
    /**
     * Check if audio recording permission is granted
     */
    private fun hasAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        recordingJob?.cancel()
        healthMonitorJob?.cancel()
        
        audioRecord?.let { record ->
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                record.release()
            }
        }
        audioRecord = null
        
        recordingBuffer?.clear()
        currentOutputFile = null
    }
    
    // Getters
    fun isRecording(): Boolean = isRecording.get()
    fun isPaused(): Boolean = isPaused.get()
    fun getBytesRecorded(): Long = bytesRecorded.get()
    
    // Callback setters
    fun setOnRecordingStarted(callback: () -> Unit) { onRecordingStarted = callback }
    fun setOnRecordingStopped(callback: () -> Unit) { onRecordingStopped = callback }
    fun setOnError(callback: (String) -> Unit) { onError = callback }
    fun setOnHealthUpdate(callback: (HealthMetrics) -> Unit) { onHealthUpdate = callback }
    
    /**
     * Release all resources
     */
    fun release() {
        if (isRecording.get()) {
            runBlocking { stopRecording() }
        }
        cleanup()
        Log.d(TAG, "AudioRecordManager released")
    }
}

/**
 * Health metrics for monitoring recording quality
 */
data class HealthMetrics(
    val bytesRecorded: Long,
    val bufferOverruns: Long,
    val isHealthy: Boolean,
    val timestamp: Long
)
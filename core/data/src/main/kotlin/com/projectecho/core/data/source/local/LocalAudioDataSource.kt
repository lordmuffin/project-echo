package com.projectecho.core.data.source.local

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.projectecho.core.common.result.Result
import com.projectecho.core.domain.model.AudioLevel
import com.projectecho.core.domain.model.AudioRecording
import com.projectecho.core.domain.model.PlaybackState
import com.projectecho.core.domain.model.RecordingConfig
import com.projectecho.core.domain.model.RecordingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local data source for audio recording and playback operations.
 * Handles AudioRecord API, file I/O, and audio level monitoring.
 */
@Singleton
class LocalAudioDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    private val _playbackPosition = MutableStateFlow(0L)
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingFile: File? = null
    private var currentConfig: RecordingConfig? = null
    
    // Audio recording parameters
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSize = minBufferSize * 4 // 4x minimum for stability

    fun getRecordingState(): Flow<RecordingState> = _recordingState.asStateFlow()
    fun getPlaybackState(): Flow<PlaybackState> = _playbackState.asStateFlow()
    fun getPlaybackPosition(): Flow<Long> = _playbackPosition.asStateFlow()

    @SuppressLint("MissingPermission")
    suspend fun startRecording(config: RecordingConfig): Result<Unit> {
        try {
            if (isRecording) {
                return Result.Error(IllegalStateException("Recording already in progress"))
            }

            currentConfig = config
            
            // Create recording file
            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }
            
            recordingFile = File(recordingsDir, "recording_${System.currentTimeMillis()}.wav")
            
            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return Result.Error(IllegalStateException("Failed to initialize AudioRecord"))
            }
            
            audioRecord?.startRecording()
            isRecording = true
            _recordingState.value = RecordingState.Recording
            
            // Start recording in background thread
            startRecordingThread()
            
            return Result.Success(Unit)
            
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("Failed to start recording", e)
            return Result.Error(e)
        }
    }

    suspend fun stopRecording(): Result<AudioRecording> {
        try {
            if (!isRecording) {
                return Result.Error(IllegalStateException("No recording in progress"))
            }
            
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            _recordingState.value = RecordingState.Idle
            
            val file = recordingFile ?: return Result.Error(IllegalStateException("No recording file"))
            
            // Create AudioRecording domain model
            val recording = AudioRecording(
                id = UUID.randomUUID().toString(),
                title = "Recording ${Date()}",
                filePath = file.absolutePath,
                duration = calculateDuration(file),
                timestamp = System.currentTimeMillis(),
                size = file.length(),
                format = com.projectecho.core.domain.model.AudioFormat.WAV,
                sampleRate = sampleRate,
                channels = 1,
                bitDepth = 16
            )
            
            return Result.Success(recording)
            
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("Failed to stop recording", e)
            return Result.Error(e)
        }
    }

    suspend fun pauseRecording(): Result<Unit> {
        try {
            if (!isRecording) {
                return Result.Error(IllegalStateException("No recording in progress"))
            }
            
            audioRecord?.stop()
            _recordingState.value = RecordingState.Paused
            
            return Result.Success(Unit)
            
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("Failed to pause recording", e)
            return Result.Error(e)
        }
    }

    suspend fun resumeRecording(): Result<Unit> {
        try {
            if (_recordingState.value !is RecordingState.Paused) {
                return Result.Error(IllegalStateException("Recording is not paused"))
            }
            
            audioRecord?.startRecording()
            _recordingState.value = RecordingState.Recording
            
            return Result.Success(Unit)
            
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("Failed to resume recording", e)
            return Result.Error(e)
        }
    }

    suspend fun cancelRecording(): Result<Unit> {
        try {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            // Delete the recording file
            recordingFile?.delete()
            recordingFile = null
            
            _recordingState.value = RecordingState.Idle
            
            return Result.Success(Unit)
            
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("Failed to cancel recording", e)
            return Result.Error(e)
        }
    }

    fun getAudioLevels(): Flow<AudioLevel> = callbackFlow {
        while (isRecording && audioRecord != null) {
            try {
                val buffer = ShortArray(bufferSize)
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readResult > 0) {
                    val amplitude = calculateAmplitude(buffer, readResult)
                    val rms = calculateRMS(buffer, readResult)
                    val peak = buffer.take(readResult).maxOfOrNull { kotlin.math.abs(it.toInt()) }?.toFloat() ?: 0f
                    
                    val audioLevel = AudioLevel(
                        amplitude = amplitude,
                        rms = rms,
                        peak = peak / Short.MAX_VALUE
                    )
                    
                    trySend(audioLevel)
                }
                
                kotlinx.coroutines.delay(50) // Update every 50ms
                
            } catch (e: Exception) {
                // Handle error
                close(e)
            }
        }
        
        awaitClose { }
    }

    suspend fun deleteRecording(id: String): Result<Unit> {
        try {
            // Implementation would find and delete the local file
            // This is a simplified version
            return Result.Success(Unit)
        } catch (e: Exception) {
            return Result.Error(e)
        }
    }

    // Playback methods (simplified implementations)
    suspend fun playRecording(recording: AudioRecording): Result<Unit> {
        _playbackState.value = PlaybackState.Playing
        return Result.Success(Unit)
    }

    suspend fun stopPlayback(): Result<Unit> {
        _playbackState.value = PlaybackState.Idle
        _playbackPosition.value = 0L
        return Result.Success(Unit)
    }

    suspend fun pausePlayback(): Result<Unit> {
        _playbackState.value = PlaybackState.Paused
        return Result.Success(Unit)
    }

    suspend fun resumePlayback(): Result<Unit> {
        _playbackState.value = PlaybackState.Playing
        return Result.Success(Unit)
    }

    suspend fun seekTo(position: Long): Result<Unit> {
        _playbackPosition.value = position
        return Result.Success(Unit)
    }

    suspend fun getRecordingAudioStream(id: String): java.io.InputStream? {
        try {
            // Find recording file by ID - simplified implementation
            val recordingsDir = File(context.filesDir, "recordings")
            val files = recordingsDir.listFiles() ?: return null
            
            // In a real implementation, you'd look up the file path from the database
            // For now, we'll try to find a file that might match
            val file = files.firstOrNull { it.name.contains(id) }
                ?: files.firstOrNull() // Fallback to first available file
                
            return file?.inputStream()
        } catch (e: Exception) {
            return null
        }
    }

    private fun startRecordingThread() {
        Thread {
            val buffer = ShortArray(bufferSize)
            val outputStream = try {
                FileOutputStream(recordingFile)
            } catch (e: IOException) {
                _recordingState.value = RecordingState.Error("Failed to create output stream", e)
                return@Thread
            }

            try {
                while (isRecording && audioRecord != null) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readResult > 0) {
                        // Convert to bytes and write to file
                        val bytes = ByteArray(readResult * 2)
                        var index = 0
                        for (i in 0 until readResult) {
                            val sample = buffer[i]
                            bytes[index++] = (sample.toInt() and 0xff).toByte()
                            bytes[index++] = ((sample.toInt() shr 8) and 0xff).toByte()
                        }
                        outputStream.write(bytes)
                    }
                }
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Error("Recording error", e)
            } finally {
                try {
                    outputStream.close()
                } catch (e: IOException) {
                    // Handle close error
                }
            }
        }.start()
    }

    private fun calculateAmplitude(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += kotlin.math.abs(buffer[i].toDouble())
        }
        return (sum / length / Short.MAX_VALUE).toFloat()
    }

    private fun calculateRMS(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return kotlin.math.sqrt(sum / length).toFloat() / Short.MAX_VALUE
    }

    private fun calculateDuration(file: File): Long {
        // Simplified duration calculation
        // In production, would parse WAV header or use MediaMetadataRetriever
        val fileSize = file.length()
        val bytesPerSecond = sampleRate * 2 // 16-bit mono
        return (fileSize * 1000 / bytesPerSecond)
    }
}
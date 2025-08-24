package com.projectecho.core.domain.repository

import com.projectecho.core.common.result.Result
import com.projectecho.core.domain.model.AudioLevel
import com.projectecho.core.domain.model.AudioRecording
import com.projectecho.core.domain.model.PlaybackState
import com.projectecho.core.domain.model.RecordingConfig
import com.projectecho.core.domain.model.RecordingState
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Repository interface for audio operations.
 * Provides abstraction over data sources for audio recording and playback.
 */
interface AudioRepository {
    
    /**
     * Start recording with the given configuration.
     */
    suspend fun startRecording(config: RecordingConfig): Result<Unit>
    
    /**
     * Stop the current recording and save it.
     */
    suspend fun stopRecording(): Result<AudioRecording>
    
    /**
     * Pause the current recording.
     */
    suspend fun pauseRecording(): Result<Unit>
    
    /**
     * Resume the paused recording.
     */
    suspend fun resumeRecording(): Result<Unit>
    
    /**
     * Cancel the current recording without saving.
     */
    suspend fun cancelRecording(): Result<Unit>
    
    /**
     * Get the current recording state.
     */
    fun getRecordingState(): Flow<RecordingState>
    
    /**
     * Get real-time audio levels during recording.
     */
    fun getAudioLevels(): Flow<AudioLevel>
    
    /**
     * Get all saved recordings.
     */
    fun getAllRecordings(): Flow<List<AudioRecording>>
    
    /**
     * Get a specific recording by ID.
     */
    suspend fun getRecordingById(id: String): Result<AudioRecording?>
    
    /**
     * Delete a recording.
     */
    suspend fun deleteRecording(id: String): Result<Unit>
    
    /**
     * Play an audio recording.
     */
    suspend fun playRecording(recording: AudioRecording): Result<Unit>
    
    /**
     * Stop playback.
     */
    suspend fun stopPlayback(): Result<Unit>
    
    /**
     * Pause playback.
     */
    suspend fun pausePlayback(): Result<Unit>
    
    /**
     * Resume playback.
     */
    suspend fun resumePlayback(): Result<Unit>
    
    /**
     * Seek to a specific position in the recording.
     */
    suspend fun seekTo(position: Long): Result<Unit>
    
    /**
     * Get the current playback state.
     */
    fun getPlaybackState(): Flow<PlaybackState>
    
    /**
     * Get the current playback position.
     */
    fun getPlaybackPosition(): Flow<Long>
    
    // Wearable sync extensions
    
    /**
     * Get recording by ID (suspend version for sync operations)
     */
    suspend fun getRecording(id: String): AudioRecording?
    
    /**
     * Get all recordings synchronously (for sync operations) 
     */
    suspend fun getAllRecordingsSync(): List<AudioRecording>
    
    /**
     * Get audio data as InputStream for streaming
     */
    suspend fun getRecordingAudioStream(id: String): InputStream?
    
    /**
     * Update recording title
     */
    suspend fun updateRecordingTitle(id: String, title: String): Result<Unit>
    
    /**
     * Update recording tags
     */
    suspend fun updateRecordingTags(id: String, tags: List<String>): Result<Unit>
    
    /**
     * Update recording description
     */
    suspend fun updateRecordingDescription(id: String, description: String): Result<Unit>
}
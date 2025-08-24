package com.projectecho.core.domain.model

import kotlinx.coroutines.flow.Flow

/**
 * Domain model representing an audio recording.
 */
data class AudioRecording(
    val id: String,
    val title: String,
    val filePath: String,
    val duration: Long, // in milliseconds
    val timestamp: Long, // creation timestamp
    val size: Long, // file size in bytes
    val format: AudioFormat = AudioFormat.PCM,
    val sampleRate: Int = 44100,
    val channels: Int = 1, // mono by default for voice recordings
    val bitDepth: Int = 16,
    // Additional wearable sync fields
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = timestamp,
    val updatedAt: Long = timestamp,
    val fileSize: Long = size,
    val bitRate: Int = sampleRate * bitDepth * channels
)

/**
 * Supported audio formats.
 */
enum class AudioFormat {
    PCM,
    MP3,
    AAC,
    WAV
}

/**
 * Recording state for real-time recording operations.
 */
sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    object Paused : RecordingState()
    data class Error(val message: String, val cause: Throwable? = null) : RecordingState()
}

/**
 * Playback state for audio playback operations.
 */
sealed class PlaybackState {
    object Idle : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    object Completed : PlaybackState()
    data class Error(val message: String, val cause: Throwable? = null) : PlaybackState()
}

/**
 * Audio level information for visual feedback.
 */
data class AudioLevel(
    val amplitude: Float, // Current amplitude (0.0 to 1.0)
    val rms: Float, // Root mean square value
    val peak: Float, // Peak value
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Recording configuration.
 */
data class RecordingConfig(
    val format: AudioFormat = AudioFormat.PCM,
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val maxDuration: Long = 0L, // 0 = unlimited
    val enableNoiseReduction: Boolean = true,
    val enableAutoGainControl: Boolean = true,
    val enableEchoCancellation: Boolean = true
)
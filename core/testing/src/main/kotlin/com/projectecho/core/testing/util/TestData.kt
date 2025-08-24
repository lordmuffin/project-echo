package com.projectecho.core.testing.util

import com.projectecho.core.domain.model.*
import com.projectecho.core.database.entity.AudioRecordingEntity
import java.util.*

/**
 * Test data factory for creating consistent test objects across the application.
 * Provides both domain models and database entities for comprehensive testing.
 */
object TestData {

    /**
     * Create a sample AudioRecording for testing.
     */
    fun createAudioRecording(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Recording",
        filePath: String = "/test/path/recording.wav",
        duration: Long = 60000L,
        timestamp: Long = System.currentTimeMillis(),
        size: Long = 2048000L,
        format: AudioFormat = AudioFormat.WAV,
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitDepth: Int = 16
    ) = AudioRecording(
        id = id,
        title = title,
        filePath = filePath,
        duration = duration,
        timestamp = timestamp,
        size = size,
        format = format,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth
    )

    /**
     * Create a sample AudioRecordingEntity for database testing.
     */
    fun createAudioRecordingEntity(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Recording Entity",
        filePath: String = "/test/path/entity.wav",
        duration: Long = 45000L,
        timestamp: Long = System.currentTimeMillis(),
        size: Long = 1536000L,
        format: String = "WAV",
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitDepth: Int = 16
    ) = AudioRecordingEntity(
        id = id,
        title = title,
        filePath = filePath,
        duration = duration,
        timestamp = timestamp,
        size = size,
        format = format,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth
    )

    /**
     * Create a list of sample recordings for bulk testing.
     */
    fun createAudioRecordingList(count: Int = 5): List<AudioRecording> {
        return (1..count).map { i ->
            createAudioRecording(
                title = "Test Recording $i",
                filePath = "/test/path/recording_$i.wav",
                duration = 30000L + (i * 5000),
                timestamp = System.currentTimeMillis() + (i * 60000)
            )
        }
    }

    /**
     * Create a sample RecordingConfig for testing.
     */
    fun createRecordingConfig(
        format: AudioFormat = AudioFormat.PCM,
        sampleRate: Int = 44100,
        channels: Int = 1,
        bitDepth: Int = 16,
        maxDuration: Long = 0L,
        enableNoiseReduction: Boolean = true,
        enableAutoGainControl: Boolean = true,
        enableEchoCancellation: Boolean = true
    ) = RecordingConfig(
        format = format,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        maxDuration = maxDuration,
        enableNoiseReduction = enableNoiseReduction,
        enableAutoGainControl = enableAutoGainControl,
        enableEchoCancellation = enableEchoCancellation
    )

    /**
     * Create a sample AudioLevel for testing.
     */
    fun createAudioLevel(
        amplitude: Float = 0.5f,
        rms: Float = 0.3f,
        peak: Float = 0.8f,
        timestamp: Long = System.currentTimeMillis()
    ) = AudioLevel(
        amplitude = amplitude,
        rms = rms,
        peak = peak,
        timestamp = timestamp
    )

    /**
     * Create test data for different recording states.
     */
    fun createRecordingStates(): List<RecordingState> = listOf(
        RecordingState.Idle,
        RecordingState.Recording,
        RecordingState.Paused,
        RecordingState.Error("Test error message", RuntimeException("Test cause"))
    )

    /**
     * Create test data for different playback states.
     */
    fun createPlaybackStates(): List<PlaybackState> = listOf(
        PlaybackState.Idle,
        PlaybackState.Playing,
        PlaybackState.Paused,
        PlaybackState.Completed,
        PlaybackState.Error("Test playback error", RuntimeException("Test playback cause"))
    )

    /**
     * Create recordings with various formats for format testing.
     */
    fun createRecordingsWithDifferentFormats(): List<AudioRecording> {
        return AudioFormat.values().map { format ->
            createAudioRecording(
                title = "Test ${format.name} Recording",
                filePath = "/test/path/recording.${format.name.lowercase()}",
                format = format
            )
        }
    }

    /**
     * Create recordings with various sample rates for quality testing.
     */
    fun createRecordingsWithDifferentSampleRates(): List<AudioRecording> {
        val sampleRates = listOf(8000, 16000, 22050, 44100, 48000, 96000)
        return sampleRates.map { sampleRate ->
            createAudioRecording(
                title = "Test ${sampleRate}Hz Recording",
                sampleRate = sampleRate
            )
        }
    }

    /**
     * Create large recording for performance testing.
     */
    fun createLargeRecording(): AudioRecording = createAudioRecording(
        title = "Large Performance Test Recording",
        duration = 3600000L, // 1 hour
        size = 1024L * 1024L * 100L, // 100MB
        timestamp = System.currentTimeMillis()
    )

    /**
     * Create recordings for database constraint testing.
     */
    fun createRecordingsForConstraintTesting(): List<AudioRecording> = listOf(
        // Empty title
        createAudioRecording(title = ""),
        // Very long title
        createAudioRecording(title = "A".repeat(1000)),
        // Zero duration
        createAudioRecording(duration = 0L),
        // Negative values (should be handled by validation)
        createAudioRecording(duration = -1L, size = -1L),
        // Future timestamp
        createAudioRecording(timestamp = System.currentTimeMillis() + 86400000L),
        // Very old timestamp
        createAudioRecording(timestamp = 0L)
    )
}
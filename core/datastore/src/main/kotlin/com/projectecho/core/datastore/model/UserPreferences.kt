package com.projectecho.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * User preferences data model for type-safe storage.
 * Uses Kotlin Serialization for JSON-based preferences storage.
 */
@Serializable
data class UserPreferences(
    val recordingSettings: RecordingSettings = RecordingSettings(),
    val uiSettings: UiSettings = UiSettings(),
    val syncSettings: SyncSettings = SyncSettings(),
    val privacySettings: PrivacySettings = PrivacySettings()
)

@Serializable
data class RecordingSettings(
    val defaultFormat: AudioFormat = AudioFormat.PCM,
    val defaultSampleRate: Int = 44100,
    val defaultBitDepth: Int = 16,
    val defaultChannels: Int = 1,
    val enableNoiseReduction: Boolean = true,
    val enableAutoGainControl: Boolean = true,
    val enableEchoCancellation: Boolean = true,
    val maxRecordingDuration: Long = 0L, // 0 = unlimited
    val autoSaveEnabled: Boolean = true,
    val compressionLevel: CompressionLevel = CompressionLevel.MEDIUM
)

@Serializable
data class UiSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val enableHapticFeedback: Boolean = true,
    val showRecordingWaveform: Boolean = true,
    val enableScreenTimeout: Boolean = false,
    val fontSize: FontSize = FontSize.MEDIUM,
    val language: String = "en"
)

@Serializable
data class SyncSettings(
    val enableCloudSync: Boolean = false,
    val syncOnWifiOnly: Boolean = true,
    val autoUploadRecordings: Boolean = false,
    val keepLocalCopies: Boolean = true,
    val syncFrequency: SyncFrequency = SyncFrequency.MANUAL,
    val maxUploadSize: Long = 100 * 1024 * 1024L // 100MB
)

@Serializable
data class PrivacySettings(
    val enableAnalytics: Boolean = true,
    val enableCrashReporting: Boolean = true,
    val shareUsageData: Boolean = false,
    val enableLocationTagging: Boolean = false,
    val requireAuthForPlayback: Boolean = false
)

@Serializable
enum class AudioFormat {
    PCM, MP3, AAC, WAV
}

@Serializable
enum class CompressionLevel {
    LOW, MEDIUM, HIGH
}

@Serializable
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

@Serializable
enum class FontSize {
    SMALL, MEDIUM, LARGE
}

@Serializable
enum class SyncFrequency {
    MANUAL, HOURLY, DAILY, WEEKLY
}
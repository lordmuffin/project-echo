package com.projectecho.core.wearable.model

import kotlinx.serialization.Serializable

/**
 * Data items that can be synchronized via DataClient
 */
@Serializable
data class RecordingMetadata(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val duration: Long = 0L,
    val fileSize: Long = 0L,
    val format: String = "m4a",
    val sampleRate: Int = 44100,
    val bitRate: Int = 128000,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

@Serializable
data class DevicePreferences(
    val autoSyncEnabled: Boolean = true,
    val syncOnWifiOnly: Boolean = false,
    val maxFileSize: Long = 100 * 1024 * 1024L, // 100MB
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val retryAttempts: Int = 3,
    val syncPriority: SyncPriority = SyncPriority.NORMAL
)

@Serializable
data class SyncConfiguration(
    val batchSize: Int = 1024 * 1024, // 1MB chunks
    val maxConcurrentTransfers: Int = 3,
    val timeoutMs: Long = 30000L,
    val retryDelayMs: Long = 5000L,
    val compressionEnabled: Boolean = true,
    val encryptionEnabled: Boolean = true
)

@Serializable
enum class SyncStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
enum class AudioQuality {
    LOW,      // 64kbps
    MEDIUM,   // 128kbps
    HIGH,     // 256kbps
    LOSSLESS  // 1411kbps
}

// Data paths for Wearable Data Layer
object WearableDataPaths {
    const val RECORDING_METADATA = "/recording_metadata"
    const val DEVICE_PREFERENCES = "/device_preferences"
    const val SYNC_CONFIGURATION = "/sync_configuration"
    const val DEVICE_STATUS = "/device_status"
    const val RECORDING_LIST = "/recording_list"
}

// Message paths for MessageClient
object WearableMessagePaths {
    const val RECORDING_CONTROL = "/recording_control"
    const val RECORDING_STATUS = "/recording_status"
    const val METADATA_SYNC = "/metadata_sync"
    const val AUDIO_SYNC_REQUEST = "/audio_sync_request"
    const val AUDIO_SYNC_COMPLETE = "/audio_sync_complete"
    const val DEVICE_CONNECTION = "/device_connection"
}
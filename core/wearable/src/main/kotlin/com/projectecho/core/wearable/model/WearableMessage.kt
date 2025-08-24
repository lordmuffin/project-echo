package com.projectecho.core.wearable.model

import kotlinx.serialization.Serializable

/**
 * Base sealed interface for all wearable messages
 */
sealed interface WearableMessage {
    val timestamp: Long
}

/**
 * Recording control messages
 */
@Serializable
sealed interface RecordingControlMessage : WearableMessage {
    
    @Serializable
    data class StartRecording(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String,
        val title: String? = null
    ) : RecordingControlMessage
    
    @Serializable
    data class StopRecording(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String
    ) : RecordingControlMessage
    
    @Serializable
    data class PauseRecording(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String
    ) : RecordingControlMessage
    
    @Serializable
    data class ResumeRecording(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String
    ) : RecordingControlMessage
}

/**
 * Recording status messages
 */
@Serializable
sealed interface RecordingStatusMessage : WearableMessage {
    
    @Serializable
    data class RecordingStarted(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String,
        val title: String? = null,
        val deviceId: String
    ) : RecordingStatusMessage
    
    @Serializable
    data class RecordingStopped(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String,
        val duration: Long,
        val fileSize: Long,
        val deviceId: String
    ) : RecordingStatusMessage
    
    @Serializable
    data class RecordingError(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String,
        val error: String,
        val deviceId: String
    ) : RecordingStatusMessage
}

/**
 * Metadata sync messages
 */
@Serializable
sealed interface MetadataSyncMessage : WearableMessage {
    
    @Serializable
    data class UpdateMetadata(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String,
        val title: String? = null,
        val tags: List<String> = emptyList(),
        val description: String? = null
    ) : MetadataSyncMessage
    
    @Serializable
    data class MetadataUpdated(
        override val timestamp: Long = System.currentTimeMillis(),
        val recordingId: String,
        val success: Boolean,
        val deviceId: String
    ) : MetadataSyncMessage
}

/**
 * Audio data sync messages
 */
@Serializable
data class AudioDataSyncRequest(
    override val timestamp: Long = System.currentTimeMillis(),
    val recordingId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val priority: SyncPriority = SyncPriority.NORMAL
) : WearableMessage

@Serializable
data class AudioDataSyncComplete(
    override val timestamp: Long = System.currentTimeMillis(),
    val recordingId: String,
    val success: Boolean,
    val transferredBytes: Long,
    val deviceId: String
) : WearableMessage

@Serializable
enum class SyncPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Device connection status
 */
@Serializable
data class DeviceConnectionStatus(
    override val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val isConnected: Boolean,
    val batteryLevel: Int? = null,
    val storageAvailable: Long? = null
) : WearableMessage
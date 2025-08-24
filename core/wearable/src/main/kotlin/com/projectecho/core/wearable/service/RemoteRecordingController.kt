package com.projectecho.core.wearable.service

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.model.RecordingControlMessage
import com.projectecho.core.wearable.model.RecordingStatusMessage
import kotlinx.coroutines.flow.Flow

/**
 * Service for controlling recording on remote devices (phone controlling watch recording)
 */
interface RemoteRecordingController {
    
    /**
     * Start recording on a specific device
     */
    suspend fun startRecordingOnDevice(deviceId: String, title: String? = null): Result<String>
    
    /**
     * Start recording on all connected devices
     */
    suspend fun startRecordingOnAllDevices(title: String? = null): Result<List<String>>
    
    /**
     * Stop recording on a specific device
     */
    suspend fun stopRecordingOnDevice(deviceId: String, recordingId: String): Result<Unit>
    
    /**
     * Stop recording on all devices
     */
    suspend fun stopRecordingOnAllDevices(): Result<Unit>
    
    /**
     * Pause recording on a specific device
     */
    suspend fun pauseRecordingOnDevice(deviceId: String, recordingId: String): Result<Unit>
    
    /**
     * Resume recording on a specific device
     */
    suspend fun resumeRecordingOnDevice(deviceId: String, recordingId: String): Result<Unit>
    
    /**
     * Get all active recording sessions across devices
     */
    suspend fun getActiveRecordingSessions(): Result<List<RemoteRecordingSession>>
    
    /**
     * Observe recording status from all connected devices
     */
    fun observeRemoteRecordingStatus(): Flow<RemoteRecordingStatus>
    
    /**
     * Get list of devices capable of recording
     */
    suspend fun getRecordingCapableDevices(): Result<List<RemoteDevice>>
    
    /**
     * Check if a device is currently recording
     */
    suspend fun isDeviceRecording(deviceId: String): Boolean
}

/**
 * Represents a recording session on a remote device
 */
data class RemoteRecordingSession(
    val recordingId: String,
    val deviceId: String,
    val deviceName: String,
    val title: String?,
    val startTime: Long,
    val duration: Long,
    val status: RecordingSessionStatus
)

enum class RecordingSessionStatus {
    RECORDING,
    PAUSED,
    STOPPED,
    ERROR
}

/**
 * Status update from a remote recording session
 */
data class RemoteRecordingStatus(
    val deviceId: String,
    val deviceName: String,
    val recordingId: String?,
    val status: RecordingSessionStatus,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Information about a remote device
 */
data class RemoteDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val isConnected: Boolean,
    val batteryLevel: Int? = null,
    val storageAvailable: Long? = null,
    val capabilities: List<String> = emptyList()
)

enum class DeviceType {
    PHONE,
    WATCH,
    TABLET,
    UNKNOWN
}
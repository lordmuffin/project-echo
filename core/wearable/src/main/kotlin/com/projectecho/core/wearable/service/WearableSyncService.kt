package com.projectecho.core.wearable.service

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.model.*
import kotlinx.coroutines.flow.Flow

/**
 * High-level service for coordinating data synchronization between phone and watch
 */
interface WearableSyncService {
    
    /**
     * Recording synchronization
     */
    suspend fun syncRecordingMetadata(recordingId: String): Result<Unit>
    suspend fun syncAllRecordings(): Result<Unit>
    suspend fun syncRecordingAudioData(recordingId: String): Result<Unit>
    
    /**
     * Metadata synchronization
     */
    suspend fun updateRecordingTitle(recordingId: String, title: String): Result<Unit>
    suspend fun updateRecordingTags(recordingId: String, tags: List<String>): Result<Unit>
    suspend fun updateRecordingDescription(recordingId: String, description: String): Result<Unit>
    
    /**
     * Device preferences synchronization
     */
    suspend fun syncDevicePreferences(): Result<Unit>
    suspend fun updatePreferences(preferences: DevicePreferences): Result<Unit>
    
    /**
     * Recording control
     */
    suspend fun startRemoteRecording(nodeId: String, title: String? = null): Result<String>
    suspend fun stopRemoteRecording(nodeId: String, recordingId: String): Result<Unit>
    suspend fun pauseRemoteRecording(nodeId: String, recordingId: String): Result<Unit>
    suspend fun resumeRemoteRecording(nodeId: String, recordingId: String): Result<Unit>
    
    /**
     * Status observation
     */
    fun observeSyncStatus(): Flow<SyncStatus>
    fun observeRecordingUpdates(): Flow<RecordingMetadata>
    fun observeRemoteRecordingStatus(): Flow<Pair<String, RecordingStatusMessage>>
    
    /**
     * Connection management
     */
    suspend fun isConnectedToDevices(): Boolean
    fun observeDeviceConnections(): Flow<List<String>>
    
    /**
     * Error handling and offline support
     */
    suspend fun retryFailedSyncs(): Result<Unit>
    suspend fun clearSyncQueue(): Result<Unit>
    fun observeSyncErrors(): Flow<SyncError>
}
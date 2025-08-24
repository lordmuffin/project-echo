package com.projectecho.core.wearable.client

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.model.RecordingMetadata
import com.projectecho.core.wearable.model.DevicePreferences
import com.projectecho.core.wearable.model.SyncConfiguration
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing data synchronization between phone and watch
 * Uses Google's Wearable Data Layer API DataClient
 */
interface WearableDataClient {
    
    /**
     * Recording metadata operations
     */
    suspend fun putRecordingMetadata(metadata: RecordingMetadata): Result<Unit>
    suspend fun getRecordingMetadata(recordingId: String): Result<RecordingMetadata?>
    suspend fun getAllRecordingMetadata(): Result<List<RecordingMetadata>>
    suspend fun deleteRecordingMetadata(recordingId: String): Result<Unit>
    
    /**
     * Device preferences operations
     */
    suspend fun putDevicePreferences(preferences: DevicePreferences): Result<Unit>
    suspend fun getDevicePreferences(): Result<DevicePreferences?>
    
    /**
     * Sync configuration operations
     */
    suspend fun putSyncConfiguration(config: SyncConfiguration): Result<Unit>
    suspend fun getSyncConfiguration(): Result<SyncConfiguration?>
    
    /**
     * Real-time data change observation
     */
    fun observeRecordingMetadataChanges(): Flow<RecordingMetadata>
    fun observeDevicePreferencesChanges(): Flow<DevicePreferences>
    fun observeSyncConfigurationChanges(): Flow<SyncConfiguration>
    
    /**
     * Connection status
     */
    suspend fun isConnectedToWearable(): Boolean
    fun observeConnectionStatus(): Flow<Boolean>
    
    /**
     * Data synchronization
     */
    suspend fun syncAllData(): Result<Unit>
    suspend fun clearAllData(): Result<Unit>
}
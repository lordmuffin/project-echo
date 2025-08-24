package com.projectecho.core.wearable.client

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface for real-time messaging between phone and watch
 * Uses Google's Wearable Data Layer API MessageClient
 */
interface WearableMessageClient {
    
    /**
     * Recording control messaging
     */
    suspend fun sendRecordingControlMessage(
        nodeId: String,
        message: RecordingControlMessage
    ): Result<Unit>
    
    suspend fun broadcastRecordingControlMessage(
        message: RecordingControlMessage
    ): Result<Unit>
    
    /**
     * Recording status messaging
     */
    suspend fun sendRecordingStatusMessage(
        nodeId: String,
        message: RecordingStatusMessage
    ): Result<Unit>
    
    suspend fun broadcastRecordingStatusMessage(
        message: RecordingStatusMessage
    ): Result<Unit>
    
    /**
     * Metadata sync messaging
     */
    suspend fun sendMetadataSyncMessage(
        nodeId: String,
        message: MetadataSyncMessage
    ): Result<Unit>
    
    suspend fun broadcastMetadataSyncMessage(
        message: MetadataSyncMessage
    ): Result<Unit>
    
    /**
     * Audio sync messaging
     */
    suspend fun sendAudioSyncRequest(
        nodeId: String,
        request: AudioDataSyncRequest
    ): Result<Unit>
    
    suspend fun sendAudioSyncComplete(
        nodeId: String,
        complete: AudioDataSyncComplete
    ): Result<Unit>
    
    /**
     * Device connection messaging
     */
    suspend fun sendDeviceConnectionStatus(
        nodeId: String,
        status: DeviceConnectionStatus
    ): Result<Unit>
    
    suspend fun broadcastDeviceConnectionStatus(
        status: DeviceConnectionStatus
    ): Result<Unit>
    
    /**
     * Message observation
     */
    fun observeRecordingControlMessages(): Flow<Pair<String, RecordingControlMessage>>
    fun observeRecordingStatusMessages(): Flow<Pair<String, RecordingStatusMessage>>
    fun observeMetadataSyncMessages(): Flow<Pair<String, MetadataSyncMessage>>
    fun observeAudioSyncRequests(): Flow<Pair<String, AudioDataSyncRequest>>
    fun observeAudioSyncComplete(): Flow<Pair<String, AudioDataSyncComplete>>
    fun observeDeviceConnectionStatus(): Flow<Pair<String, DeviceConnectionStatus>>
    
    /**
     * Node management
     */
    suspend fun getConnectedNodes(): Result<List<String>>
    suspend fun getCapableNodes(capability: String): Result<List<String>>
}
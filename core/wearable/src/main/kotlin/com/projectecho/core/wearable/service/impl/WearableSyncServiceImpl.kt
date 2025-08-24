package com.projectecho.core.wearable.service.impl

import com.projectecho.core.common.result.Result
import com.projectecho.core.domain.repository.AudioRepository
import com.projectecho.core.wearable.client.WearableChannelClient
import com.projectecho.core.wearable.client.WearableDataClient
import com.projectecho.core.wearable.client.WearableMessageClient
import com.projectecho.core.wearable.model.*
import com.projectecho.core.wearable.service.WearableSyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableSyncServiceImpl @Inject constructor(
    private val wearableDataClient: WearableDataClient,
    private val wearableMessageClient: WearableMessageClient,
    private val wearableChannelClient: WearableChannelClient,
    private val audioRepository: AudioRepository
) : WearableSyncService {
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val syncQueue = ConcurrentHashMap<String, SyncOperation>()
    private val syncErrors = MutableSharedFlow<SyncError>()
    private val syncStatus = MutableStateFlow(SyncStatus.PENDING)
    
    init {
        // Start observing remote recording status messages
        observeRemoteMessages()
    }
    
    override suspend fun syncRecordingMetadata(recordingId: String): Result<Unit> {
        return try {
            val recording = audioRepository.getRecording(recordingId)
            if (recording == null) {
                return Result.Error(IllegalArgumentException("Recording not found: $recordingId"))
            }
            
            val metadata = RecordingMetadata(
                id = recording.id,
                title = recording.title,
                description = recording.description,
                tags = recording.tags,
                duration = recording.duration,
                fileSize = recording.fileSize,
                format = recording.format,
                sampleRate = recording.sampleRate,
                bitRate = recording.bitRate,
                createdAt = recording.createdAt,
                updatedAt = recording.updatedAt,
                deviceId = getDeviceId(),
                syncStatus = SyncStatus.IN_PROGRESS
            )
            
            syncStatus.value = SyncStatus.IN_PROGRESS
            val result = wearableDataClient.putRecordingMetadata(metadata)
            
            when (result) {
                is Result.Success -> {
                    syncStatus.value = SyncStatus.COMPLETED
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    syncStatus.value = SyncStatus.FAILED
                    emitSyncError(SyncErrorType.NETWORK_ERROR, "Failed to sync metadata: ${result.exception.message}", recordingId)
                    result
                }
            }
        } catch (e: Exception) {
            syncStatus.value = SyncStatus.FAILED
            emitSyncError(SyncErrorType.UNKNOWN_ERROR, e.message ?: "Unknown error", recordingId)
            Result.Error(e)
        }
    }
    
    override suspend fun syncAllRecordings(): Result<Unit> {
        return try {
            syncStatus.value = SyncStatus.IN_PROGRESS
            val recordings = audioRepository.getAllRecordings()
            
            recordings.forEach { recording ->
                syncRecordingMetadata(recording.id)
            }
            
            syncStatus.value = SyncStatus.COMPLETED
            Result.Success(Unit)
        } catch (e: Exception) {
            syncStatus.value = SyncStatus.FAILED
            emitSyncError(SyncErrorType.UNKNOWN_ERROR, e.message ?: "Failed to sync all recordings")
            Result.Error(e)
        }
    }
    
    override suspend fun syncRecordingAudioData(recordingId: String): Result<Unit> {
        return try {
            val recording = audioRepository.getRecording(recordingId)
                ?: return Result.Error(IllegalArgumentException("Recording not found"))
            
            val nodes = wearableMessageClient.getConnectedNodes()
            if (nodes is Result.Error) {
                return Result.Error(nodes.exception)
            }
            
            val connectedNodes = (nodes as Result.Success).data
            if (connectedNodes.isEmpty()) {
                return Result.Error(IllegalStateException("No connected devices"))
            }
            
            // Use the first connected node
            val nodeId = connectedNodes.first()
            val channelPath = "/audio_transfer/$recordingId"
            
            val channelResult = wearableChannelClient.openChannel(nodeId, channelPath)
            if (channelResult is Result.Error) {
                return Result.Error(channelResult.exception)
            }
            
            val channelHandle = (channelResult as Result.Success).data
            
            try {
                // Get audio data as InputStream
                val audioData = audioRepository.getRecordingAudioStream(recordingId)
                    ?: return Result.Error(IllegalStateException("Audio data not available"))
                
                // Stream audio data through the channel
                val streamResult = wearableChannelClient.streamAudioData(
                    channelHandle = channelHandle,
                    audioData = audioData,
                    progressCallback = { transferred, total ->
                        // Update progress in the sync status
                        // You could emit progress updates here
                    }
                )
                
                if (streamResult is Result.Success) {
                    // Notify completion
                    val completeMessage = AudioDataSyncComplete(
                        recordingId = recordingId,
                        success = true,
                        transferredBytes = recording.fileSize,
                        deviceId = getDeviceId()
                    )
                    wearableMessageClient.sendAudioSyncComplete(nodeId, completeMessage)
                }
                
                streamResult
            } finally {
                wearableChannelClient.closeChannel(channelHandle)
            }
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.NETWORK_ERROR, e.message ?: "Audio sync failed", recordingId)
            Result.Error(e)
        }
    }
    
    override suspend fun updateRecordingTitle(recordingId: String, title: String): Result<Unit> {
        return try {
            // Update local repository first
            audioRepository.updateRecordingTitle(recordingId, title)
            
            // Send metadata update message to all connected devices
            val message = MetadataSyncMessage.UpdateMetadata(
                recordingId = recordingId,
                title = title
            )
            
            val result = wearableMessageClient.broadcastMetadataSyncMessage(message)
            
            // Also update the data layer
            val metadata = wearableDataClient.getRecordingMetadata(recordingId)
            if (metadata is Result.Success && metadata.data != null) {
                val updatedMetadata = metadata.data.copy(
                    title = title,
                    updatedAt = System.currentTimeMillis()
                )
                wearableDataClient.putRecordingMetadata(updatedMetadata)
            }
            
            result
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.UNKNOWN_ERROR, e.message ?: "Failed to update title", recordingId)
            Result.Error(e)
        }
    }
    
    override suspend fun updateRecordingTags(recordingId: String, tags: List<String>): Result<Unit> {
        return try {
            // Update local repository first
            audioRepository.updateRecordingTags(recordingId, tags)
            
            // Send metadata update message
            val message = MetadataSyncMessage.UpdateMetadata(
                recordingId = recordingId,
                tags = tags
            )
            
            val result = wearableMessageClient.broadcastMetadataSyncMessage(message)
            
            // Also update the data layer
            val metadata = wearableDataClient.getRecordingMetadata(recordingId)
            if (metadata is Result.Success && metadata.data != null) {
                val updatedMetadata = metadata.data.copy(
                    tags = tags,
                    updatedAt = System.currentTimeMillis()
                )
                wearableDataClient.putRecordingMetadata(updatedMetadata)
            }
            
            result
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.UNKNOWN_ERROR, e.message ?: "Failed to update tags", recordingId)
            Result.Error(e)
        }
    }
    
    override suspend fun updateRecordingDescription(recordingId: String, description: String): Result<Unit> {
        return try {
            // Update local repository first
            audioRepository.updateRecordingDescription(recordingId, description)
            
            // Send metadata update message
            val message = MetadataSyncMessage.UpdateMetadata(
                recordingId = recordingId,
                description = description
            )
            
            val result = wearableMessageClient.broadcastMetadataSyncMessage(message)
            
            // Also update the data layer
            val metadata = wearableDataClient.getRecordingMetadata(recordingId)
            if (metadata is Result.Success && metadata.data != null) {
                val updatedMetadata = metadata.data.copy(
                    description = description,
                    updatedAt = System.currentTimeMillis()
                )
                wearableDataClient.putRecordingMetadata(updatedMetadata)
            }
            
            result
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.UNKNOWN_ERROR, e.message ?: "Failed to update description", recordingId)
            Result.Error(e)
        }
    }
    
    override suspend fun syncDevicePreferences(): Result<Unit> {
        return wearableDataClient.syncAllData()
    }
    
    override suspend fun updatePreferences(preferences: DevicePreferences): Result<Unit> {
        return wearableDataClient.putDevicePreferences(preferences)
    }
    
    override suspend fun startRemoteRecording(nodeId: String, title: String?): Result<String> {
        return try {
            val recordingId = java.util.UUID.randomUUID().toString()
            val message = RecordingControlMessage.StartRecording(
                recordingId = recordingId,
                title = title
            )
            
            val result = wearableMessageClient.sendRecordingControlMessage(nodeId, message)
            when (result) {
                is Result.Success -> Result.Success(recordingId)
                is Result.Error -> result.let { Result.Error(it.exception) }
            }
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.NETWORK_ERROR, e.message ?: "Failed to start remote recording")
            Result.Error(e)
        }
    }
    
    override suspend fun stopRemoteRecording(nodeId: String, recordingId: String): Result<Unit> {
        return try {
            val message = RecordingControlMessage.StopRecording(recordingId = recordingId)
            wearableMessageClient.sendRecordingControlMessage(nodeId, message)
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.NETWORK_ERROR, e.message ?: "Failed to stop remote recording", recordingId)
            Result.Error(e)
        }
    }
    
    override suspend fun pauseRemoteRecording(nodeId: String, recordingId: String): Result<Unit> {
        return try {
            val message = RecordingControlMessage.PauseRecording(recordingId = recordingId)
            wearableMessageClient.sendRecordingControlMessage(nodeId, message)
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.NETWORK_ERROR, e.message ?: "Failed to pause remote recording", recordingId)
            Result.Error(e)
        }
    }
    
    override suspend fun resumeRemoteRecording(nodeId: String, recordingId: String): Result<Unit> {
        return try {
            val message = RecordingControlMessage.ResumeRecording(recordingId = recordingId)
            wearableMessageClient.sendRecordingControlMessage(nodeId, message)
        } catch (e: Exception) {
            emitSyncError(SyncErrorType.NETWORK_ERROR, e.message ?: "Failed to resume remote recording", recordingId)
            Result.Error(e)
        }
    }
    
    override fun observeSyncStatus(): Flow<SyncStatus> = syncStatus.asStateFlow()
    
    override fun observeRecordingUpdates(): Flow<RecordingMetadata> = 
        wearableDataClient.observeRecordingMetadataChanges()
    
    override fun observeRemoteRecordingStatus(): Flow<Pair<String, RecordingStatusMessage>> = 
        wearableMessageClient.observeRecordingStatusMessages()
    
    override suspend fun isConnectedToDevices(): Boolean = 
        wearableDataClient.isConnectedToWearable()
    
    override fun observeDeviceConnections(): Flow<List<String>> = flow {
        wearableDataClient.observeConnectionStatus().collect { isConnected ->
            if (isConnected) {
                val nodes = wearableMessageClient.getConnectedNodes()
                if (nodes is Result.Success) {
                    emit(nodes.data)
                } else {
                    emit(emptyList())
                }
            } else {
                emit(emptyList())
            }
        }
    }
    
    override suspend fun retryFailedSyncs(): Result<Unit> {
        // Implement retry logic for failed sync operations
        return Result.Success(Unit)
    }
    
    override suspend fun clearSyncQueue(): Result<Unit> {
        syncQueue.clear()
        return Result.Success(Unit)
    }
    
    override fun observeSyncErrors(): Flow<SyncError> = syncErrors.asSharedFlow()
    
    private fun observeRemoteMessages() {
        coroutineScope.launch {
            // Observe metadata sync messages
            wearableMessageClient.observeMetadataSyncMessages().collect { (nodeId, message) ->
                handleMetadataSyncMessage(nodeId, message)
            }
        }
        
        coroutineScope.launch {
            // Observe recording status messages
            wearableMessageClient.observeRecordingStatusMessages().collect { (nodeId, message) ->
                handleRecordingStatusMessage(nodeId, message)
            }
        }
    }
    
    private suspend fun handleMetadataSyncMessage(nodeId: String, message: MetadataSyncMessage) {
        when (message) {
            is MetadataSyncMessage.UpdateMetadata -> {
                try {
                    // Update local data based on remote changes
                    message.title?.let { 
                        audioRepository.updateRecordingTitle(message.recordingId, it)
                    }
                    message.description?.let {
                        audioRepository.updateRecordingDescription(message.recordingId, it)
                    }
                    if (message.tags.isNotEmpty()) {
                        audioRepository.updateRecordingTags(message.recordingId, message.tags)
                    }
                    
                    // Send confirmation
                    val response = MetadataSyncMessage.MetadataUpdated(
                        recordingId = message.recordingId,
                        success = true,
                        deviceId = getDeviceId()
                    )
                    wearableMessageClient.sendMetadataSyncMessage(nodeId, response)
                } catch (e: Exception) {
                    val response = MetadataSyncMessage.MetadataUpdated(
                        recordingId = message.recordingId,
                        success = false,
                        deviceId = getDeviceId()
                    )
                    wearableMessageClient.sendMetadataSyncMessage(nodeId, response)
                }
            }
            is MetadataSyncMessage.MetadataUpdated -> {
                // Handle confirmation from remote device
                if (!message.success) {
                    emitSyncError(
                        SyncErrorType.UNKNOWN_ERROR,
                        "Remote metadata update failed",
                        message.recordingId
                    )
                }
            }
        }
    }
    
    private suspend fun handleRecordingStatusMessage(nodeId: String, message: RecordingStatusMessage) {
        when (message) {
            is RecordingStatusMessage.RecordingStarted -> {
                // Handle remote recording start
                // You might want to update UI or local state
            }
            is RecordingStatusMessage.RecordingStopped -> {
                // Handle remote recording stop
                // Potentially trigger sync of the new recording
                syncRecordingMetadata(message.recordingId)
            }
            is RecordingStatusMessage.RecordingError -> {
                emitSyncError(
                    SyncErrorType.UNKNOWN_ERROR,
                    "Remote recording error: ${message.error}",
                    message.recordingId
                )
            }
        }
    }
    
    private fun emitSyncError(
        errorType: SyncErrorType,
        message: String,
        recordingId: String? = null,
        deviceId: String? = null
    ) {
        val error = SyncError(
            recordingId = recordingId,
            errorType = errorType,
            message = message,
            deviceId = deviceId ?: getDeviceId()
        )
        
        coroutineScope.launch {
            syncErrors.emit(error)
        }
    }
    
    private fun getDeviceId(): String {
        // Return a unique device identifier
        return android.provider.Settings.Secure.getString(
            null, // Context would be needed here in real implementation
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
    
    private data class SyncOperation(
        val id: String,
        val type: String,
        val recordingId: String?,
        val retryCount: Int = 0,
        val maxRetries: Int = 3
    )
}
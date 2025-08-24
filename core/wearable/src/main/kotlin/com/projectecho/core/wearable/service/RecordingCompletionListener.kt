package com.projectecho.core.wearable.service

import com.projectecho.core.domain.model.AudioRecording
import com.projectecho.core.domain.repository.AudioRepository
import com.projectecho.core.wearable.model.RecordingStatusMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens for recording completion events and automatically triggers sync
 */
@Singleton
class RecordingCompletionListener @Inject constructor(
    private val audioRepository: AudioRepository,
    private val wearableSyncService: WearableSyncService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastRecordings = emptyList<AudioRecording>()
    
    fun startListening() {
        scope.launch {
            // Monitor for new recordings
            audioRepository.getAllRecordings()
                .distinctUntilChanged()
                .collect { currentRecordings ->
                    // Find newly added recordings
                    val newRecordings = currentRecordings.filter { recording ->
                        !lastRecordings.any { it.id == recording.id }
                    }
                    
                    // Trigger sync for new recordings
                    newRecordings.forEach { recording ->
                        launch {
                            triggerAutoSync(recording)
                        }
                    }
                    
                    lastRecordings = currentRecordings
                }
        }
        
        scope.launch {
            // Monitor for recording metadata updates
            wearableSyncService.observeRecordingUpdates()
                .collect { metadata ->
                    // Sync updated metadata back to local storage if needed
                    handleRemoteMetadataUpdate(metadata)
                }
        }
        
        scope.launch {
            // Monitor remote recording completions
            wearableSyncService.observeRemoteRecordingStatus()
                .collect { (nodeId, statusMessage) ->
                    handleRemoteRecordingStatus(nodeId, statusMessage)
                }
        }
    }
    
    fun stopListening() {
        scope.cancel()
    }
    
    private suspend fun triggerAutoSync(recording: AudioRecording) {
        try {
            // Check if connected to wearable devices
            if (!wearableSyncService.isConnectedToDevices()) {
                return
            }
            
            // Auto-sync metadata first (fast)
            wearableSyncService.syncRecordingMetadata(recording.id)
            
            // Check preferences for auto audio sync
            // This would normally check user preferences stored in data layer
            val shouldSyncAudio = true // Placeholder - implement preference check
            
            if (shouldSyncAudio) {
                // Delay audio sync to avoid overwhelming the connection
                delay(2000)
                wearableSyncService.syncRecordingAudioData(recording.id)
            }
            
        } catch (e: Exception) {
            // Log error but don't fail silently
            println("Auto-sync failed for recording ${recording.id}: ${e.message}")
        }
    }
    
    private suspend fun handleRemoteMetadataUpdate(metadata: com.projectecho.core.wearable.model.RecordingMetadata) {
        try {
            // Update local recording if it exists
            val localRecording = audioRepository.getRecording(metadata.id)
            if (localRecording != null) {
                // Update fields that might have changed
                metadata.title?.let { 
                    audioRepository.updateRecordingTitle(metadata.id, it)
                }
                metadata.description?.let {
                    audioRepository.updateRecordingDescription(metadata.id, it)
                }
                if (metadata.tags.isNotEmpty()) {
                    audioRepository.updateRecordingTags(metadata.id, metadata.tags)
                }
            }
        } catch (e: Exception) {
            println("Failed to handle remote metadata update: ${e.message}")
        }
    }
    
    private suspend fun handleRemoteRecordingStatus(nodeId: String, statusMessage: RecordingStatusMessage) {
        when (statusMessage) {
            is RecordingStatusMessage.RecordingStopped -> {
                // A remote recording was completed - trigger sync
                delay(1000) // Small delay to let the remote device finish processing
                wearableSyncService.syncRecordingMetadata(statusMessage.recordingId)
                
                // Request audio sync if enabled
                delay(5000) // Longer delay for audio sync
                wearableSyncService.syncRecordingAudioData(statusMessage.recordingId)
            }
            is RecordingStatusMessage.RecordingError -> {
                // Handle recording errors from remote devices
                println("Remote recording error: ${statusMessage.error}")
            }
            else -> {
                // Other status messages can be logged or handled as needed
            }
        }
    }
}
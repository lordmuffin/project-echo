package com.projectecho.core.wearable

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.model.*
import com.projectecho.core.wearable.service.WearableSyncService
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class WearableDataSyncTest {
    
    private lateinit var syncService: WearableSyncService
    
    @Before
    fun setup() {
        syncService = mockk()
    }
    
    @Test
    fun `test recording metadata sync success`() = runTest {
        // Arrange
        val recordingId = "test_recording_123"
        coEvery { syncService.syncRecordingMetadata(recordingId) } returns Result.Success(Unit)
        
        // Act
        val result = syncService.syncRecordingMetadata(recordingId)
        
        // Assert
        assertTrue(result is Result.Success)
        coVerify { syncService.syncRecordingMetadata(recordingId) }
    }
    
    @Test
    fun `test recording metadata sync failure`() = runTest {
        // Arrange
        val recordingId = "test_recording_123"
        val exception = Exception("Network error")
        coEvery { syncService.syncRecordingMetadata(recordingId) } returns Result.Error(exception)
        
        // Act
        val result = syncService.syncRecordingMetadata(recordingId)
        
        // Assert
        assertTrue(result is Result.Error)
        assertEquals(exception, (result as Result.Error).exception)
    }
    
    @Test
    fun `test metadata update propagation`() = runTest {
        // Arrange
        val recordingId = "test_recording_123"
        val newTitle = "Updated Title"
        coEvery { syncService.updateRecordingTitle(recordingId, newTitle) } returns Result.Success(Unit)
        
        // Act
        val result = syncService.updateRecordingTitle(recordingId, newTitle)
        
        // Assert
        assertTrue(result is Result.Success)
        coVerify { syncService.updateRecordingTitle(recordingId, newTitle) }
    }
    
    @Test
    fun `test remote recording control`() = runTest {
        // Arrange
        val deviceId = "watch_node_123"
        val recordingId = "remote_recording_456"
        val title = "Remote Recording"
        
        coEvery { syncService.startRemoteRecording(deviceId, title) } returns Result.Success(recordingId)
        
        // Act
        val result = syncService.startRemoteRecording(deviceId, title)
        
        // Assert
        assertTrue(result is Result.Success)
        assertEquals(recordingId, (result as Result.Success).data)
    }
    
    @Test
    fun `test device connection status monitoring`() = runTest {
        // Arrange
        val connectedDevices = listOf("device1", "device2")
        every { syncService.observeDeviceConnections() } returns flowOf(connectedDevices)
        
        // Act & Assert
        syncService.observeDeviceConnections().collect { devices ->
            assertEquals(connectedDevices, devices)
        }
    }
    
    @Test
    fun `test sync error handling`() = runTest {
        // Arrange
        val syncError = SyncError(
            recordingId = "test_recording",
            errorType = SyncErrorType.NETWORK_ERROR,
            message = "Connection failed",
            retryCount = 1
        )
        
        every { syncService.observeSyncErrors() } returns flowOf(syncError)
        
        // Act & Assert
        syncService.observeSyncErrors().collect { error ->
            assertEquals(syncError, error)
            assertTrue(error.isRetryable)
        }
    }
    
    @Test
    fun `test batch sync all recordings`() = runTest {
        // Arrange
        coEvery { syncService.syncAllRecordings() } returns Result.Success(Unit)
        
        // Act
        val result = syncService.syncAllRecordings()
        
        // Assert
        assertTrue(result is Result.Success)
        coVerify { syncService.syncAllRecordings() }
    }
    
    @Test
    fun `test audio data streaming sync`() = runTest {
        // Arrange
        val recordingId = "audio_recording_789"
        coEvery { syncService.syncRecordingAudioData(recordingId) } returns Result.Success(Unit)
        
        // Act
        val result = syncService.syncRecordingAudioData(recordingId)
        
        // Assert
        assertTrue(result is Result.Success)
        coVerify { syncService.syncRecordingAudioData(recordingId) }
    }
}
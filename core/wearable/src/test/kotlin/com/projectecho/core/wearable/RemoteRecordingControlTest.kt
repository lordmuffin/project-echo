package com.projectecho.core.wearable

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.service.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class RemoteRecordingControlTest {
    
    private lateinit var remoteController: RemoteRecordingController
    
    @Before
    fun setup() {
        remoteController = mockk()
    }
    
    @Test
    fun `test start recording on single device`() = runTest {
        // Arrange
        val deviceId = "watch_123"
        val title = "Test Recording"
        val recordingId = "recording_456"
        
        coEvery { remoteController.startRecordingOnDevice(deviceId, title) } returns Result.Success(recordingId)
        
        // Act
        val result = remoteController.startRecordingOnDevice(deviceId, title)
        
        // Assert
        assertTrue(result is Result.Success)
        assertEquals(recordingId, (result as Result.Success).data)
        coVerify { remoteController.startRecordingOnDevice(deviceId, title) }
    }
    
    @Test
    fun `test start recording on all devices`() = runTest {
        // Arrange
        val title = "Broadcast Recording"
        val recordingIds = listOf("rec_1", "rec_2", "rec_3")
        
        coEvery { remoteController.startRecordingOnAllDevices(title) } returns Result.Success(recordingIds)
        
        // Act
        val result = remoteController.startRecordingOnAllDevices(title)
        
        // Assert
        assertTrue(result is Result.Success)
        assertEquals(recordingIds, (result as Result.Success).data)
    }
    
    @Test
    fun `test stop recording on device`() = runTest {
        // Arrange
        val deviceId = "watch_123"
        val recordingId = "recording_456"
        
        coEvery { remoteController.stopRecordingOnDevice(deviceId, recordingId) } returns Result.Success(Unit)
        
        // Act
        val result = remoteController.stopRecordingOnDevice(deviceId, recordingId)
        
        // Assert
        assertTrue(result is Result.Success)
        coVerify { remoteController.stopRecordingOnDevice(deviceId, recordingId) }
    }
    
    @Test
    fun `test pause and resume recording`() = runTest {
        // Arrange
        val deviceId = "watch_123"
        val recordingId = "recording_456"
        
        coEvery { remoteController.pauseRecordingOnDevice(deviceId, recordingId) } returns Result.Success(Unit)
        coEvery { remoteController.resumeRecordingOnDevice(deviceId, recordingId) } returns Result.Success(Unit)
        
        // Act
        val pauseResult = remoteController.pauseRecordingOnDevice(deviceId, recordingId)
        val resumeResult = remoteController.resumeRecordingOnDevice(deviceId, recordingId)
        
        // Assert
        assertTrue(pauseResult is Result.Success)
        assertTrue(resumeResult is Result.Success)
        coVerify { remoteController.pauseRecordingOnDevice(deviceId, recordingId) }
        coVerify { remoteController.resumeRecordingOnDevice(deviceId, recordingId) }
    }
    
    @Test
    fun `test get active recording sessions`() = runTest {
        // Arrange
        val activeSessions = listOf(
            RemoteRecordingSession(
                recordingId = "rec_1",
                deviceId = "device_1",
                deviceName = "Watch 1",
                title = "Recording 1",
                startTime = System.currentTimeMillis() - 30000,
                duration = 30000,
                status = RecordingSessionStatus.RECORDING
            ),
            RemoteRecordingSession(
                recordingId = "rec_2",
                deviceId = "device_2", 
                deviceName = "Watch 2",
                title = "Recording 2",
                startTime = System.currentTimeMillis() - 15000,
                duration = 15000,
                status = RecordingSessionStatus.PAUSED
            )
        )
        
        coEvery { remoteController.getActiveRecordingSessions() } returns Result.Success(activeSessions)
        
        // Act
        val result = remoteController.getActiveRecordingSessions()
        
        // Assert
        assertTrue(result is Result.Success)
        val sessions = (result as Result.Success).data
        assertEquals(2, sessions.size)
        assertEquals(RecordingSessionStatus.RECORDING, sessions[0].status)
        assertEquals(RecordingSessionStatus.PAUSED, sessions[1].status)
    }
    
    @Test
    fun `test observe remote recording status`() = runTest {
        // Arrange
        val statusUpdate = RemoteRecordingStatus(
            deviceId = "watch_123",
            deviceName = "Galaxy Watch",
            recordingId = "rec_456",
            status = RecordingSessionStatus.RECORDING,
            timestamp = System.currentTimeMillis()
        )
        
        every { remoteController.observeRemoteRecordingStatus() } returns flowOf(statusUpdate)
        
        // Act & Assert
        remoteController.observeRemoteRecordingStatus().collect { status ->
            assertEquals(statusUpdate, status)
            assertEquals(RecordingSessionStatus.RECORDING, status.status)
        }
    }
    
    @Test
    fun `test get recording capable devices`() = runTest {
        // Arrange
        val capableDevices = listOf(
            RemoteDevice(
                id = "watch_1",
                name = "Galaxy Watch 5",
                type = DeviceType.WATCH,
                isConnected = true,
                batteryLevel = 85,
                capabilities = listOf("audio_recording", "bluetooth")
            ),
            RemoteDevice(
                id = "phone_1",
                name = "Galaxy S23",
                type = DeviceType.PHONE,
                isConnected = true,
                batteryLevel = 67,
                storageAvailable = 32000000000L, // 32GB
                capabilities = listOf("audio_recording", "high_quality_recording")
            )
        )
        
        coEvery { remoteController.getRecordingCapableDevices() } returns Result.Success(capableDevices)
        
        // Act
        val result = remoteController.getRecordingCapableDevices()
        
        // Assert
        assertTrue(result is Result.Success)
        val devices = (result as Result.Success).data
        assertEquals(2, devices.size)
        assertTrue(devices.all { it.capabilities.contains("audio_recording") })
        assertTrue(devices.all { it.isConnected })
    }
    
    @Test
    fun `test device recording status check`() = runTest {
        // Arrange
        val deviceId = "watch_123"
        coEvery { remoteController.isDeviceRecording(deviceId) } returns true
        
        // Act
        val isRecording = remoteController.isDeviceRecording(deviceId)
        
        // Assert
        assertTrue(isRecording)
        coVerify { remoteController.isDeviceRecording(deviceId) }
    }
    
    @Test
    fun `test recording control failure handling`() = runTest {
        // Arrange
        val deviceId = "disconnected_watch"
        val exception = Exception("Device not reachable")
        
        coEvery { remoteController.startRecordingOnDevice(deviceId, any()) } returns Result.Error(exception)
        
        // Act
        val result = remoteController.startRecordingOnDevice(deviceId, "Test")
        
        // Assert
        assertTrue(result is Result.Error)
        assertEquals(exception, (result as Result.Error).exception)
    }
}
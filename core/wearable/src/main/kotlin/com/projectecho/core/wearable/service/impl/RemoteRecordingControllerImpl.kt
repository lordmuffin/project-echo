package com.projectecho.core.wearable.service.impl

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.client.WearableMessageClient
import com.projectecho.core.wearable.model.*
import com.projectecho.core.wearable.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteRecordingControllerImpl @Inject constructor(
    private val messageClient: WearableMessageClient
) : RemoteRecordingController {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val activeRecordingSessions = ConcurrentHashMap<String, RemoteRecordingSession>()
    private val deviceRecordingStatus = ConcurrentHashMap<String, RecordingSessionStatus>()
    private val remoteStatusFlow = MutableSharedFlow<RemoteRecordingStatus>()
    
    init {
        // Start observing remote recording status messages
        observeRemoteStatusMessages()
    }
    
    override suspend fun startRecordingOnDevice(deviceId: String, title: String?): Result<String> {
        return try {
            val recordingId = generateRecordingId()
            val message = RecordingControlMessage.StartRecording(
                recordingId = recordingId,
                title = title
            )
            
            val result = messageClient.sendRecordingControlMessage(deviceId, message)
            
            when (result) {
                is Result.Success -> {
                    // Track the recording session
                    val session = RemoteRecordingSession(
                        recordingId = recordingId,
                        deviceId = deviceId,
                        deviceName = getDeviceName(deviceId),
                        title = title,
                        startTime = System.currentTimeMillis(),
                        duration = 0L,
                        status = RecordingSessionStatus.RECORDING
                    )
                    activeRecordingSessions[recordingId] = session
                    deviceRecordingStatus[deviceId] = RecordingSessionStatus.RECORDING
                    
                    Result.Success(recordingId)
                }
                is Result.Error -> result
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun startRecordingOnAllDevices(title: String?): Result<List<String>> {
        return try {
            val connectedNodes = messageClient.getConnectedNodes()
            
            when (connectedNodes) {
                is Result.Error -> connectedNodes.let { Result.Error(it.exception) }
                is Result.Success -> {
                    val recordingIds = mutableListOf<String>()
                    
                    connectedNodes.data.forEach { deviceId ->
                        val result = startRecordingOnDevice(deviceId, title)
                        if (result is Result.Success) {
                            recordingIds.add(result.data)
                        }
                    }
                    
                    Result.Success(recordingIds)
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun stopRecordingOnDevice(deviceId: String, recordingId: String): Result<Unit> {
        return try {
            val message = RecordingControlMessage.StopRecording(recordingId = recordingId)
            val result = messageClient.sendRecordingControlMessage(deviceId, message)
            
            if (result is Result.Success) {
                // Update session status
                activeRecordingSessions[recordingId]?.let { session ->
                    activeRecordingSessions[recordingId] = session.copy(
                        status = RecordingSessionStatus.STOPPED,
                        duration = System.currentTimeMillis() - session.startTime
                    )
                }
                deviceRecordingStatus[deviceId] = RecordingSessionStatus.STOPPED
            }
            
            result
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun stopRecordingOnAllDevices(): Result<Unit> {
        return try {
            val errors = mutableListOf<Exception>()
            
            activeRecordingSessions.values.forEach { session ->
                if (session.status == RecordingSessionStatus.RECORDING || 
                    session.status == RecordingSessionStatus.PAUSED) {
                    
                    val result = stopRecordingOnDevice(session.deviceId, session.recordingId)
                    if (result is Result.Error) {
                        errors.add(result.exception)
                    }
                }
            }
            
            if (errors.isNotEmpty()) {
                Result.Error(IllegalStateException("Some devices failed to stop recording: ${errors.joinToString { it.message ?: "Unknown error" }}"))
            } else {
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun pauseRecordingOnDevice(deviceId: String, recordingId: String): Result<Unit> {
        return try {
            val message = RecordingControlMessage.PauseRecording(recordingId = recordingId)
            val result = messageClient.sendRecordingControlMessage(deviceId, message)
            
            if (result is Result.Success) {
                // Update session status
                activeRecordingSessions[recordingId]?.let { session ->
                    activeRecordingSessions[recordingId] = session.copy(status = RecordingSessionStatus.PAUSED)
                }
                deviceRecordingStatus[deviceId] = RecordingSessionStatus.PAUSED
            }
            
            result
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun resumeRecordingOnDevice(deviceId: String, recordingId: String): Result<Unit> {
        return try {
            val message = RecordingControlMessage.ResumeRecording(recordingId = recordingId)
            val result = messageClient.sendRecordingControlMessage(deviceId, message)
            
            if (result is Result.Success) {
                // Update session status
                activeRecordingSessions[recordingId]?.let { session ->
                    activeRecordingSessions[recordingId] = session.copy(status = RecordingSessionStatus.RECORDING)
                }
                deviceRecordingStatus[deviceId] = RecordingSessionStatus.RECORDING
            }
            
            result
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getActiveRecordingSessions(): Result<List<RemoteRecordingSession>> {
        return try {
            val activeSessions = activeRecordingSessions.values
                .filter { it.status == RecordingSessionStatus.RECORDING || it.status == RecordingSessionStatus.PAUSED }
                .toList()
            
            Result.Success(activeSessions)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override fun observeRemoteRecordingStatus(): Flow<RemoteRecordingStatus> {
        return remoteStatusFlow.asSharedFlow()
    }
    
    override suspend fun getRecordingCapableDevices(): Result<List<RemoteDevice>> {
        return try {
            val capableNodes = messageClient.getCapableNodes("audio_recording")
            
            when (capableNodes) {
                is Result.Error -> capableNodes.let { Result.Error(it.exception) }
                is Result.Success -> {
                    val devices = capableNodes.data.map { nodeId ->
                        RemoteDevice(
                            id = nodeId,
                            name = getDeviceName(nodeId),
                            type = getDeviceType(nodeId),
                            isConnected = true,
                            capabilities = listOf("audio_recording")
                        )
                    }
                    Result.Success(devices)
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun isDeviceRecording(deviceId: String): Boolean {
        val status = deviceRecordingStatus[deviceId]
        return status == RecordingSessionStatus.RECORDING || status == RecordingSessionStatus.PAUSED
    }
    
    private fun observeRemoteStatusMessages() {
        scope.launch {
            messageClient.observeRecordingStatusMessages().collect { (nodeId, statusMessage) ->
                handleRemoteStatusMessage(nodeId, statusMessage)
            }
        }
    }
    
    private suspend fun handleRemoteStatusMessage(nodeId: String, statusMessage: RecordingStatusMessage) {
        val status = when (statusMessage) {
            is RecordingStatusMessage.RecordingStarted -> {
                // Update our tracking
                val session = RemoteRecordingSession(
                    recordingId = statusMessage.recordingId,
                    deviceId = nodeId,
                    deviceName = getDeviceName(nodeId),
                    title = statusMessage.title,
                    startTime = statusMessage.timestamp,
                    duration = 0L,
                    status = RecordingSessionStatus.RECORDING
                )
                activeRecordingSessions[statusMessage.recordingId] = session
                deviceRecordingStatus[nodeId] = RecordingSessionStatus.RECORDING
                
                RemoteRecordingStatus(
                    deviceId = nodeId,
                    deviceName = getDeviceName(nodeId),
                    recordingId = statusMessage.recordingId,
                    status = RecordingSessionStatus.RECORDING,
                    timestamp = statusMessage.timestamp
                )
            }
            
            is RecordingStatusMessage.RecordingStopped -> {
                // Update session
                activeRecordingSessions[statusMessage.recordingId]?.let { session ->
                    activeRecordingSessions[statusMessage.recordingId] = session.copy(
                        status = RecordingSessionStatus.STOPPED,
                        duration = statusMessage.duration
                    )
                }
                deviceRecordingStatus[nodeId] = RecordingSessionStatus.STOPPED
                
                RemoteRecordingStatus(
                    deviceId = nodeId,
                    deviceName = getDeviceName(nodeId),
                    recordingId = statusMessage.recordingId,
                    status = RecordingSessionStatus.STOPPED,
                    timestamp = statusMessage.timestamp
                )
            }
            
            is RecordingStatusMessage.RecordingError -> {
                // Update session
                activeRecordingSessions[statusMessage.recordingId]?.let { session ->
                    activeRecordingSessions[statusMessage.recordingId] = session.copy(
                        status = RecordingSessionStatus.ERROR
                    )
                }
                deviceRecordingStatus[nodeId] = RecordingSessionStatus.ERROR
                
                RemoteRecordingStatus(
                    deviceId = nodeId,
                    deviceName = getDeviceName(nodeId),
                    recordingId = statusMessage.recordingId,
                    status = RecordingSessionStatus.ERROR,
                    message = statusMessage.error,
                    timestamp = statusMessage.timestamp
                )
            }
        }
        
        remoteStatusFlow.emit(status)
    }
    
    private fun generateRecordingId(): String {
        return "recording_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    private fun getDeviceName(nodeId: String): String {
        // In a real implementation, you'd maintain a mapping of node IDs to device names
        // or query the Wearable API for node information
        return when {
            nodeId.contains("watch") -> "Watch"
            nodeId.contains("phone") -> "Phone"
            else -> "Device ${nodeId.takeLast(4)}"
        }
    }
    
    private fun getDeviceType(nodeId: String): DeviceType {
        // In a real implementation, you'd determine device type from node capabilities
        return when {
            nodeId.contains("watch") -> DeviceType.WATCH
            nodeId.contains("phone") -> DeviceType.PHONE
            nodeId.contains("tablet") -> DeviceType.TABLET
            else -> DeviceType.UNKNOWN
        }
    }
}
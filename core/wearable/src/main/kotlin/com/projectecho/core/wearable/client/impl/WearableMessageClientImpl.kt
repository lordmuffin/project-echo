package com.projectecho.core.wearable.client.impl

import android.content.Context
import com.google.android.gms.wearable.*
import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.client.WearableMessageClient
import com.projectecho.core.wearable.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableMessageClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) : WearableMessageClient {
    
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }
    
    override suspend fun sendRecordingControlMessage(
        nodeId: String,
        message: RecordingControlMessage
    ): Result<Unit> {
        return try {
            val jsonData = json.encodeToString<RecordingControlMessage>(message).toByteArray()
            messageClient.sendMessage(nodeId, WearableMessagePaths.RECORDING_CONTROL, jsonData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun broadcastRecordingControlMessage(
        message: RecordingControlMessage
    ): Result<Unit> {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val jsonData = json.encodeToString<RecordingControlMessage>(message).toByteArray()
            
            nodes.forEach { node: Node ->
                messageClient.sendMessage(node.id, WearableMessagePaths.RECORDING_CONTROL, jsonData)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun sendRecordingStatusMessage(
        nodeId: String,
        message: RecordingStatusMessage
    ): Result<Unit> {
        return try {
            val jsonData = json.encodeToString<RecordingStatusMessage>(message).toByteArray()
            messageClient.sendMessage(nodeId, WearableMessagePaths.RECORDING_STATUS, jsonData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun broadcastRecordingStatusMessage(
        message: RecordingStatusMessage
    ): Result<Unit> {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val jsonData = json.encodeToString<RecordingStatusMessage>(message).toByteArray()
            
            nodes.forEach { node: Node ->
                messageClient.sendMessage(node.id, WearableMessagePaths.RECORDING_STATUS, jsonData)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun sendMetadataSyncMessage(
        nodeId: String,
        message: MetadataSyncMessage
    ): Result<Unit> {
        return try {
            val jsonData = json.encodeToString<MetadataSyncMessage>(message).toByteArray()
            messageClient.sendMessage(nodeId, WearableMessagePaths.METADATA_SYNC, jsonData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun broadcastMetadataSyncMessage(
        message: MetadataSyncMessage
    ): Result<Unit> {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val jsonData = json.encodeToString<MetadataSyncMessage>(message).toByteArray()
            
            nodes.forEach { node: Node ->
                messageClient.sendMessage(node.id, WearableMessagePaths.METADATA_SYNC, jsonData)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun sendAudioSyncRequest(
        nodeId: String,
        request: AudioDataSyncRequest
    ): Result<Unit> {
        return try {
            val jsonData = json.encodeToString(request).toByteArray()
            messageClient.sendMessage(nodeId, WearableMessagePaths.AUDIO_SYNC_REQUEST, jsonData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun sendAudioSyncComplete(
        nodeId: String,
        complete: AudioDataSyncComplete
    ): Result<Unit> {
        return try {
            val jsonData = json.encodeToString(complete).toByteArray()
            messageClient.sendMessage(nodeId, WearableMessagePaths.AUDIO_SYNC_COMPLETE, jsonData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun sendDeviceConnectionStatus(
        nodeId: String,
        status: DeviceConnectionStatus
    ): Result<Unit> {
        return try {
            val jsonData = json.encodeToString(status).toByteArray()
            messageClient.sendMessage(nodeId, WearableMessagePaths.DEVICE_CONNECTION, jsonData).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun broadcastDeviceConnectionStatus(
        status: DeviceConnectionStatus
    ): Result<Unit> {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val jsonData = json.encodeToString(status).toByteArray()
            
            nodes.forEach { node: Node ->
                messageClient.sendMessage(node.id, WearableMessagePaths.DEVICE_CONNECTION, jsonData)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override fun observeRecordingControlMessages(): Flow<Pair<String, RecordingControlMessage>> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == WearableMessagePaths.RECORDING_CONTROL) {
                try {
                    val jsonData = String(messageEvent.data)
                    val message = json.decodeFromString<RecordingControlMessage>(jsonData)
                    trySend(Pair(messageEvent.sourceNodeId, message))
                } catch (e: Exception) {
                    // Skip invalid messages
                }
            }
        }
        
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
    
    override fun observeRecordingStatusMessages(): Flow<Pair<String, RecordingStatusMessage>> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == WearableMessagePaths.RECORDING_STATUS) {
                try {
                    val jsonData = String(messageEvent.data)
                    val message = json.decodeFromString<RecordingStatusMessage>(jsonData)
                    trySend(Pair(messageEvent.sourceNodeId, message))
                } catch (e: Exception) {
                    // Skip invalid messages
                }
            }
        }
        
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
    
    override fun observeMetadataSyncMessages(): Flow<Pair<String, MetadataSyncMessage>> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == WearableMessagePaths.METADATA_SYNC) {
                try {
                    val jsonData = String(messageEvent.data)
                    val message = json.decodeFromString<MetadataSyncMessage>(jsonData)
                    trySend(Pair(messageEvent.sourceNodeId, message))
                } catch (e: Exception) {
                    // Skip invalid messages
                }
            }
        }
        
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
    
    override fun observeAudioSyncRequests(): Flow<Pair<String, AudioDataSyncRequest>> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == WearableMessagePaths.AUDIO_SYNC_REQUEST) {
                try {
                    val jsonData = String(messageEvent.data)
                    val message = json.decodeFromString<AudioDataSyncRequest>(jsonData)
                    trySend(Pair(messageEvent.sourceNodeId, message))
                } catch (e: Exception) {
                    // Skip invalid messages
                }
            }
        }
        
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
    
    override fun observeAudioSyncComplete(): Flow<Pair<String, AudioDataSyncComplete>> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == WearableMessagePaths.AUDIO_SYNC_COMPLETE) {
                try {
                    val jsonData = String(messageEvent.data)
                    val message = json.decodeFromString<AudioDataSyncComplete>(jsonData)
                    trySend(Pair(messageEvent.sourceNodeId, message))
                } catch (e: Exception) {
                    // Skip invalid messages
                }
            }
        }
        
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
    
    override fun observeDeviceConnectionStatus(): Flow<Pair<String, DeviceConnectionStatus>> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { messageEvent ->
            if (messageEvent.path == WearableMessagePaths.DEVICE_CONNECTION) {
                try {
                    val jsonData = String(messageEvent.data)
                    val message = json.decodeFromString<DeviceConnectionStatus>(jsonData)
                    trySend(Pair(messageEvent.sourceNodeId, message))
                } catch (e: Exception) {
                    // Skip invalid messages
                }
            }
        }
        
        messageClient.addListener(listener)
        awaitClose { messageClient.removeListener(listener) }
    }
    
    override suspend fun getConnectedNodes(): Result<List<String>> {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            val nodeIds = nodes.map { it.id }
            Result.Success(nodeIds)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getCapableNodes(capability: String): Result<List<String>> {
        return try {
            val capabilityInfo = capabilityClient.getCapability(capability, CapabilityClient.FILTER_ALL).await()
            val nodeIds = capabilityInfo.nodes.map { it.id }
            Result.Success(nodeIds)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
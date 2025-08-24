package com.projectecho.core.wearable.client.impl

import android.content.Context
import com.google.android.gms.wearable.*
import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.client.WearableDataClient
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
class WearableDataClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) : WearableDataClient {
    
    private val dataClient by lazy { Wearable.getDataClient(context) }
    
    override suspend fun putRecordingMetadata(metadata: RecordingMetadata): Result<Unit> {
        return try {
            val path = "${WearableDataPaths.RECORDING_METADATA}/${metadata.id}"
            val jsonData = json.encodeToString(metadata)
            
            val putRequest = PutDataMapRequest.create(path).apply {
                dataMap.putString("metadata", jsonData)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(putRequest).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getRecordingMetadata(recordingId: String): Result<RecordingMetadata?> {
        return try {
            val uri = WearableDataPaths.RECORDING_METADATA.toUri()
            val dataItems = dataClient.getDataItems(uri).await()
            
            for (dataItem in dataItems) {
                if (dataItem.uri.path?.contains(recordingId) == true) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    val jsonData = dataMap.getString("metadata")
                    if (jsonData != null) {
                        val metadata = json.decodeFromString<RecordingMetadata>(jsonData)
                        dataItems.release()
                        return Result.Success(metadata)
                    }
                }
            }
            dataItems.release()
            Result.Success(null)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getAllRecordingMetadata(): Result<List<RecordingMetadata>> {
        return try {
            val uri = WearableDataPaths.RECORDING_METADATA.toUri()
            val dataItems = dataClient.getDataItems(uri).await()
            val metadataList = mutableListOf<RecordingMetadata>()
            
            for (dataItem in dataItems) {
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val jsonData = dataMap.getString("metadata")
                if (jsonData != null) {
                    try {
                        val metadata = json.decodeFromString<RecordingMetadata>(jsonData)
                        metadataList.add(metadata)
                    } catch (e: Exception) {
                        // Skip invalid entries
                        continue
                    }
                }
            }
            dataItems.release()
            Result.Success(metadataList)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun deleteRecordingMetadata(recordingId: String): Result<Unit> {
        return try {
            val path = "${WearableDataPaths.RECORDING_METADATA}/$recordingId"
            val uri = path.toUri()
            dataClient.deleteDataItems(uri).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun putDevicePreferences(preferences: DevicePreferences): Result<Unit> {
        return try {
            val putRequest = PutDataMapRequest.create(WearableDataPaths.DEVICE_PREFERENCES).apply {
                dataMap.putString("preferences", json.encodeToString(preferences))
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(putRequest).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getDevicePreferences(): Result<DevicePreferences?> {
        return try {
            val uri = WearableDataPaths.DEVICE_PREFERENCES.toUri()
            val dataItems = dataClient.getDataItems(uri).await()
            
            if (dataItems.count > 0) {
                val dataItem = dataItems.first()
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val jsonData = dataMap.getString("preferences")
                dataItems.release()
                
                if (jsonData != null) {
                    val preferences = json.decodeFromString<DevicePreferences>(jsonData)
                    Result.Success(preferences)
                } else {
                    Result.Success(null)
                }
            } else {
                dataItems.release()
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun putSyncConfiguration(config: SyncConfiguration): Result<Unit> {
        return try {
            val putRequest = PutDataMapRequest.create(WearableDataPaths.SYNC_CONFIGURATION).apply {
                dataMap.putString("config", json.encodeToString(config))
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(putRequest).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getSyncConfiguration(): Result<SyncConfiguration?> {
        return try {
            val uri = WearableDataPaths.SYNC_CONFIGURATION.toUri()
            val dataItems = dataClient.getDataItems(uri).await()
            
            if (dataItems.count > 0) {
                val dataItem = dataItems.first()
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val jsonData = dataMap.getString("config")
                dataItems.release()
                
                if (jsonData != null) {
                    val config = json.decodeFromString<SyncConfiguration>(jsonData)
                    Result.Success(config)
                } else {
                    Result.Success(null)
                }
            } else {
                dataItems.release()
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override fun observeRecordingMetadataChanges(): Flow<RecordingMetadata> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { dataEventBuffer ->
            for (dataEvent in dataEventBuffer) {
                if (dataEvent.type == DataEvent.TYPE_CHANGED && 
                    dataEvent.dataItem.uri.path?.startsWith(WearableDataPaths.RECORDING_METADATA) == true) {
                    
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                        val jsonData = dataMap.getString("metadata")
                        if (jsonData != null) {
                            val metadata = json.decodeFromString<RecordingMetadata>(jsonData)
                            trySend(metadata)
                        }
                    } catch (e: Exception) {
                        // Skip invalid entries
                    }
                }
            }
            dataEventBuffer.release()
        }
        
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }
    
    override fun observeDevicePreferencesChanges(): Flow<DevicePreferences> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { dataEventBuffer ->
            for (dataEvent in dataEventBuffer) {
                if (dataEvent.type == DataEvent.TYPE_CHANGED && 
                    dataEvent.dataItem.uri.path == WearableDataPaths.DEVICE_PREFERENCES) {
                    
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                        val jsonData = dataMap.getString("preferences")
                        if (jsonData != null) {
                            val preferences = json.decodeFromString<DevicePreferences>(jsonData)
                            trySend(preferences)
                        }
                    } catch (e: Exception) {
                        // Skip invalid entries
                    }
                }
            }
            dataEventBuffer.release()
        }
        
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }
    
    override fun observeSyncConfigurationChanges(): Flow<SyncConfiguration> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { dataEventBuffer ->
            for (dataEvent in dataEventBuffer) {
                if (dataEvent.type == DataEvent.TYPE_CHANGED && 
                    dataEvent.dataItem.uri.path == WearableDataPaths.SYNC_CONFIGURATION) {
                    
                    try {
                        val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
                        val jsonData = dataMap.getString("config")
                        if (jsonData != null) {
                            val config = json.decodeFromString<SyncConfiguration>(jsonData)
                            trySend(config)
                        }
                    } catch (e: Exception) {
                        // Skip invalid entries
                    }
                }
            }
            dataEventBuffer.release()
        }
        
        dataClient.addListener(listener)
        awaitClose { dataClient.removeListener(listener) }
    }
    
    override suspend fun isConnectedToWearable(): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            nodes.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun observeConnectionStatus(): Flow<Boolean> = callbackFlow {
        val listener = CapabilityClient.OnCapabilityChangedListener { capabilityInfo ->
            val isConnected = capabilityInfo.nodes.isNotEmpty()
            trySend(isConnected)
        }
        
        val capabilityClient = Wearable.getCapabilityClient(context)
        capabilityClient.addListener(listener, "audio_recording")
        
        awaitClose { capabilityClient.removeListener(listener) }
    }
    
    override suspend fun syncAllData(): Result<Unit> {
        return try {
            // Trigger sync by updating a sync marker
            val putRequest = PutDataMapRequest.create("/sync_trigger").apply {
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(putRequest).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun clearAllData(): Result<Unit> {
        return try {
            dataClient.deleteDataItems("wear://*/recording_metadata/*".toUri()).await()
            dataClient.deleteDataItems(WearableDataPaths.DEVICE_PREFERENCES.toUri()).await()
            dataClient.deleteDataItems(WearableDataPaths.SYNC_CONFIGURATION.toUri()).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private fun String.toUri(): android.net.Uri {
        return android.net.Uri.parse("wear://*$this")
    }
}
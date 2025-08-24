package com.projectecho.core.wearable.client.impl

import android.content.Context
import com.google.android.gms.wearable.*
import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.client.WearableChannelClient
import com.projectecho.core.wearable.client.ChannelHandle
import com.projectecho.core.wearable.client.ChannelEvent
import com.projectecho.core.wearable.client.TransferProgress
import com.projectecho.core.wearable.client.CloseReason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableChannelClientImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearableChannelClient {
    
    private val channelClient by lazy { Wearable.getChannelClient(context) }
    private val activeChannels = ConcurrentHashMap<String, ChannelClient.Channel>()
    private val transferProgress = ConcurrentHashMap<String, MutableStateFlow<TransferProgress>>()
    
    override suspend fun openChannel(nodeId: String, path: String): Result<ChannelHandle> {
        return try {
            val channel = channelClient.openChannel(nodeId, path).await()
            val handle = ChannelHandle(
                channelId = channel.toString(),
                nodeId = nodeId,
                path = path,
                isOpen = true
            )
            activeChannels[handle.channelId] = channel
            initializeTransferProgress(handle.channelId)
            Result.Success(handle)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun acceptChannelRequest(channelId: String): Result<ChannelHandle> {
        return try {
            val channel = activeChannels[channelId]
                ?: return Result.Error(IllegalArgumentException("Channel not found: $channelId"))
            
            val handle = ChannelHandle(
                channelId = channelId,
                nodeId = channel.nodeId,
                path = channel.path,
                isOpen = true
            )
            initializeTransferProgress(channelId)
            Result.Success(handle)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun streamAudioData(
        channelHandle: ChannelHandle,
        audioData: InputStream,
        progressCallback: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): Result<Unit> {
        return try {
            val channel = activeChannels[channelHandle.channelId]
                ?: return Result.Error(IllegalArgumentException("Channel not found"))
            
            val outputStream = channelClient.getOutputStream(channel).await()
            val totalBytes = audioData.available().toLong()
            var bytesTransferred = 0L
            val buffer = ByteArray(8192)
            
            withContext(Dispatchers.IO) {
                var bytesRead = 0
                while (audioData.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesTransferred += bytesRead
                    
                    // Update progress
                    progressCallback(bytesTransferred, totalBytes)
                    updateTransferProgress(channelHandle.channelId, bytesTransferred, totalBytes)
                }
                outputStream.flush()
                outputStream.close()
            }
            
            // Mark transfer as complete
            updateTransferProgress(channelHandle.channelId, bytesTransferred, totalBytes, isComplete = true)
            Result.Success(Unit)
        } catch (e: Exception) {
            updateTransferProgress(channelHandle.channelId, 0L, null, error = e.message)
            Result.Error(e)
        }
    }
    
    override suspend fun receiveAudioData(
        channelHandle: ChannelHandle,
        outputStream: OutputStream,
        progressCallback: (bytesReceived: Long) -> Unit
    ): Result<Unit> {
        return try {
            val channel = activeChannels[channelHandle.channelId]
                ?: return Result.Error(IllegalArgumentException("Channel not found"))
            
            val inputStream = channelClient.getInputStream(channel).await()
            var bytesReceived = 0L
            val buffer = ByteArray(8192)
            
            withContext(Dispatchers.IO) {
                var bytesRead = 0
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    bytesReceived += bytesRead
                    
                    // Update progress
                    progressCallback(bytesReceived)
                    updateTransferProgress(channelHandle.channelId, bytesReceived, null)
                }
                outputStream.flush()
                inputStream.close()
            }
            
            // Mark transfer as complete
            updateTransferProgress(channelHandle.channelId, bytesReceived, null, isComplete = true)
            Result.Success(Unit)
        } catch (e: Exception) {
            updateTransferProgress(channelHandle.channelId, 0L, null, error = e.message)
            Result.Error(e)
        }
    }
    
    override suspend fun closeChannel(channelHandle: ChannelHandle): Result<Unit> {
        return try {
            val channel = activeChannels[channelHandle.channelId]
            if (channel != null) {
                channelClient.close(channel).await()
                activeChannels.remove(channelHandle.channelId)
                transferProgress.remove(channelHandle.channelId)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getChannelInputStream(channelHandle: ChannelHandle): Result<InputStream> {
        return try {
            val channel = activeChannels[channelHandle.channelId]
                ?: return Result.Error(IllegalArgumentException("Channel not found"))
            
            val inputStream = channelClient.getInputStream(channel).await()
            Result.Success(inputStream)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getChannelOutputStream(channelHandle: ChannelHandle): Result<OutputStream> {
        return try {
            val channel = activeChannels[channelHandle.channelId]
                ?: return Result.Error(IllegalArgumentException("Channel not found"))
            
            val outputStream = channelClient.getOutputStream(channel).await()
            Result.Success(outputStream)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override fun observeChannelEvents(): Flow<ChannelEvent> = callbackFlow {
        val listener = object : ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                val channelId = channel.toString()
                activeChannels[channelId] = channel
                
                val event = ChannelEvent.ChannelOpened(
                    channelId = channelId,
                    nodeId = channel.nodeId,
                    path = channel.path
                )
                trySend(event)
            }
            
            override fun onChannelClosed(
                channel: ChannelClient.Channel,
                closeReason: Int,
                appSpecificErrorCode: Int
            ) {
                val channelId = channel.toString()
                activeChannels.remove(channelId)
                transferProgress.remove(channelId)
                
                val reason = when (closeReason) {
                    0 -> CloseReason.NORMAL  // Normal close
                    1 -> CloseReason.NORMAL  // Remote close
                    2 -> CloseReason.NORMAL  // Local close
                    else -> CloseReason.ERROR
                }
                
                val event = ChannelEvent.ChannelClosed(
                    channelId = channelId,
                    nodeId = channel.nodeId,
                    closeReason = reason
                )
                trySend(event)
            }
            
            override fun onInputClosed(
                channel: ChannelClient.Channel,
                closeReason: Int,
                appSpecificErrorCode: Int
            ) {
                val event = ChannelEvent.ChannelInputClosed(
                    channelId = channel.toString(),
                    nodeId = channel.nodeId,
                    error = if (closeReason == 0) null else "Error code: $closeReason"
                )
                trySend(event)
            }
            
            override fun onOutputClosed(
                channel: ChannelClient.Channel,
                closeReason: Int,
                appSpecificErrorCode: Int
            ) {
                val event = ChannelEvent.ChannelOutputClosed(
                    channelId = channel.toString(),
                    nodeId = channel.nodeId,
                    error = if (closeReason == 0) null else "Error code: $closeReason"
                )
                trySend(event)
            }
        }
        
        channelClient.registerChannelCallback(listener)
        awaitClose { channelClient.unregisterChannelCallback(listener) }
    }
    
    override fun getTransferProgress(channelHandle: ChannelHandle): Flow<TransferProgress> {
        return transferProgress[channelHandle.channelId]?.asStateFlow() 
            ?: MutableStateFlow(TransferProgress(0L)).asStateFlow()
    }
    
    private fun initializeTransferProgress(channelId: String) {
        transferProgress[channelId] = MutableStateFlow(TransferProgress(0L))
    }
    
    private fun updateTransferProgress(
        channelId: String,
        bytesTransferred: Long,
        totalBytes: Long? = null,
        isComplete: Boolean = false,
        error: String? = null
    ) {
        transferProgress[channelId]?.value = TransferProgress(
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            transferRate = calculateTransferRate(channelId, bytesTransferred),
            estimatedTimeRemaining = calculateTimeRemaining(bytesTransferred, totalBytes, calculateTransferRate(channelId, bytesTransferred)),
            isComplete = isComplete,
            error = error
        )
    }
    
    private fun calculateTransferRate(channelId: String, bytesTransferred: Long): Long {
        // Simple rate calculation based on elapsed time
        // In a real implementation, you'd track start time and calculate rate over time
        return if (bytesTransferred > 0) {
            // Placeholder calculation - implement proper rate tracking
            bytesTransferred / 10 // bytes per second
        } else {
            0L
        }
    }
    
    private fun calculateTimeRemaining(
        bytesTransferred: Long,
        totalBytes: Long?,
        transferRate: Long
    ): Long? {
        return if (totalBytes != null && totalBytes > bytesTransferred && transferRate > 0) {
            val remainingBytes = totalBytes - bytesTransferred
            (remainingBytes * 1000) / transferRate // milliseconds
        } else {
            null
        }
    }
}
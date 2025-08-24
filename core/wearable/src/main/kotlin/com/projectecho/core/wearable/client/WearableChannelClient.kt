package com.projectecho.core.wearable.client

import com.projectecho.core.common.result.Result
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/**
 * Interface for streaming large audio data between phone and watch
 * Uses Google's Wearable Data Layer API ChannelClient
 */
interface WearableChannelClient {
    
    /**
     * Channel establishment
     */
    suspend fun openChannel(
        nodeId: String,
        path: String
    ): Result<ChannelHandle>
    
    suspend fun acceptChannelRequest(
        channelId: String
    ): Result<ChannelHandle>
    
    /**
     * Audio streaming
     */
    suspend fun streamAudioData(
        channelHandle: ChannelHandle,
        audioData: InputStream,
        progressCallback: (bytesTransferred: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Unit>
    
    suspend fun receiveAudioData(
        channelHandle: ChannelHandle,
        outputStream: OutputStream,
        progressCallback: (bytesReceived: Long) -> Unit = { _ -> }
    ): Result<Unit>
    
    /**
     * Channel management
     */
    suspend fun closeChannel(channelHandle: ChannelHandle): Result<Unit>
    suspend fun getChannelInputStream(channelHandle: ChannelHandle): Result<InputStream>
    suspend fun getChannelOutputStream(channelHandle: ChannelHandle): Result<OutputStream>
    
    /**
     * Channel observation
     */
    fun observeChannelEvents(): Flow<ChannelEvent>
    
    /**
     * Transfer status
     */
    fun getTransferProgress(channelHandle: ChannelHandle): Flow<TransferProgress>
}

/**
 * Handle for an active channel connection
 */
data class ChannelHandle(
    val channelId: String,
    val nodeId: String,
    val path: String,
    val isOpen: Boolean
)

/**
 * Channel events
 */
sealed interface ChannelEvent {
    val channelId: String
    val nodeId: String
    val timestamp: Long
    
    data class ChannelOpened(
        override val channelId: String,
        override val nodeId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val path: String
    ) : ChannelEvent
    
    data class ChannelClosed(
        override val channelId: String,
        override val nodeId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val closeReason: CloseReason
    ) : ChannelEvent
    
    data class ChannelInputClosed(
        override val channelId: String,
        override val nodeId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String? = null
    ) : ChannelEvent
    
    data class ChannelOutputClosed(
        override val channelId: String,
        override val nodeId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String? = null
    ) : ChannelEvent
}

enum class CloseReason {
    NORMAL,
    ERROR,
    TIMEOUT,
    CANCELLED
}

/**
 * Transfer progress information
 */
data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long? = null,
    val transferRate: Long = 0L, // bytes per second
    val estimatedTimeRemaining: Long? = null, // milliseconds
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val progressPercentage: Float
        get() = if (totalBytes != null && totalBytes > 0) {
            (bytesTransferred.toFloat() / totalBytes.toFloat()) * 100f
        } else 0f
}
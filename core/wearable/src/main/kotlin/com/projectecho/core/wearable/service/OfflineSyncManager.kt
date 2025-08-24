package com.projectecho.core.wearable.service

import com.projectecho.core.common.result.Result
import com.projectecho.core.wearable.model.SyncError
import com.projectecho.core.wearable.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Manages offline sync capabilities and retry logic for failed operations
 */
interface OfflineSyncManager {
    
    /**
     * Queue operations for offline sync when network is available
     */
    suspend fun queueRecordingForSync(recordingId: String): Result<Unit>
    suspend fun queueMetadataUpdate(recordingId: String, field: String, value: String): Result<Unit>
    suspend fun queueAudioDataSync(recordingId: String): Result<Unit>
    
    /**
     * Process queued sync operations
     */
    suspend fun processQueuedOperations(): Result<Int>
    suspend fun processQueuedOperationsForRecording(recordingId: String): Result<Unit>
    
    /**
     * Retry failed operations
     */
    suspend fun retryFailedOperations(): Result<Int>
    suspend fun retryOperation(operationId: String): Result<Unit>
    
    /**
     * Queue management
     */
    suspend fun clearQueue(): Result<Unit>
    suspend fun clearFailedOperations(): Result<Unit>
    suspend fun getQueuedOperationsCount(): Int
    suspend fun getFailedOperationsCount(): Int
    
    /**
     * Status and observation
     */
    fun observeQueueStatus(): Flow<OfflineQueueStatus>
    fun observeNetworkStatus(): Flow<Boolean>
    suspend fun isOnline(): Boolean
    
    /**
     * Configuration
     */
    suspend fun setRetryPolicy(policy: RetryPolicy): Result<Unit>
    suspend fun getRetryPolicy(): RetryPolicy
}

data class OfflineQueueStatus(
    val queuedOperations: Int,
    val failedOperations: Int,
    val isProcessing: Boolean,
    val lastProcessedTime: Long?,
    val nextRetryTime: Long?
)

data class RetryPolicy(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 5000L, // 5 seconds
    val maxDelayMs: Long = 300000L, // 5 minutes
    val backoffMultiplier: Double = 2.0,
    val retryOnNetworkError: Boolean = true,
    val retryOnTimeout: Boolean = true,
    val retryOnServerError: Boolean = false
)

data class QueuedOperation(
    val id: String,
    val type: OperationType,
    val recordingId: String?,
    val data: Map<String, String> = emptyMap(),
    val priority: OperationPriority = OperationPriority.NORMAL,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val nextRetryTime: Long? = null,
    val error: SyncError? = null
)

enum class OperationType {
    SYNC_METADATA,
    SYNC_AUDIO_DATA,
    UPDATE_TITLE,
    UPDATE_TAGS,
    UPDATE_DESCRIPTION,
    DELETE_RECORDING
}

enum class OperationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
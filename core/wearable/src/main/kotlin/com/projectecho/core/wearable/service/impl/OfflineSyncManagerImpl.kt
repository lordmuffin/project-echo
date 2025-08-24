package com.projectecho.core.wearable.service.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.projectecho.core.common.result.Result
import com.projectecho.core.datastore.PreferencesDataStore
import com.projectecho.core.wearable.model.SyncError
import com.projectecho.core.wearable.model.SyncErrorType
import com.projectecho.core.wearable.service.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

@Singleton
class OfflineSyncManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearableSyncService: WearableSyncService,
    private val preferencesDataStore: PreferencesDataStore,
    private val json: Json
) : OfflineSyncManager {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // In-memory queue - in production, this should be persisted to database
    private val operationQueue = PriorityQueue<QueuedOperation>(
        compareBy<QueuedOperation> { it.priority.ordinal }.thenBy { it.createdAt }
    )
    
    private val failedOperations = ConcurrentHashMap<String, QueuedOperation>()
    private val isProcessing = MutableStateFlow(false)
    private val networkStatus = MutableStateFlow(false)
    private val queueStatus = MutableStateFlow(OfflineQueueStatus(0, 0, false, null, null))
    
    private var retryPolicy = RetryPolicy()
    
    init {
        setupNetworkMonitoring()
        startAutoProcessing()
    }
    
    override suspend fun queueRecordingForSync(recordingId: String): Result<Unit> {
        return try {
            val operation = QueuedOperation(
                id = generateOperationId(),
                type = OperationType.SYNC_METADATA,
                recordingId = recordingId,
                priority = OperationPriority.HIGH
            )
            
            synchronized(operationQueue) {
                operationQueue.offer(operation)
            }
            
            updateQueueStatus()
            
            // Try to process immediately if online
            if (isOnline()) {
                processQueuedOperations()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun queueMetadataUpdate(recordingId: String, field: String, value: String): Result<Unit> {
        return try {
            val operationType = when (field) {
                "title" -> OperationType.UPDATE_TITLE
                "tags" -> OperationType.UPDATE_TAGS
                "description" -> OperationType.UPDATE_DESCRIPTION
                else -> OperationType.SYNC_METADATA
            }
            
            val operation = QueuedOperation(
                id = generateOperationId(),
                type = operationType,
                recordingId = recordingId,
                data = mapOf(field to value),
                priority = OperationPriority.NORMAL
            )
            
            synchronized(operationQueue) {
                operationQueue.offer(operation)
            }
            
            updateQueueStatus()
            
            if (isOnline()) {
                processQueuedOperations()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun queueAudioDataSync(recordingId: String): Result<Unit> {
        return try {
            val operation = QueuedOperation(
                id = generateOperationId(),
                type = OperationType.SYNC_AUDIO_DATA,
                recordingId = recordingId,
                priority = OperationPriority.LOW // Audio sync has lower priority
            )
            
            synchronized(operationQueue) {
                operationQueue.offer(operation)
            }
            
            updateQueueStatus()
            
            if (isOnline()) {
                // Delay audio sync to not overwhelm the network
                delay(5000)
                processQueuedOperationsForRecording(recordingId)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun processQueuedOperations(): Result<Int> {
        if (isProcessing.value || !isOnline()) {
            return Result.Success(0)
        }
        
        isProcessing.value = true
        var processedCount = 0
        
        try {
            val operationsToProcess = mutableListOf<QueuedOperation>()
            
            synchronized(operationQueue) {
                // Process up to 10 operations at a time to avoid overwhelming the system
                repeat(min(10, operationQueue.size)) {
                    operationQueue.poll()?.let { operationsToProcess.add(it) }
                }
            }
            
            for (operation in operationsToProcess) {
                try {
                    val result = processOperation(operation)
                    
                    when (result) {
                        is Result.Success -> {
                            processedCount++
                        }
                        is Result.Error -> {
                            handleOperationFailure(operation, result.exception)
                        }
                    }
                } catch (e: Exception) {
                    handleOperationFailure(operation, e)
                }
                
                // Small delay between operations
                delay(100)
            }
            
            updateQueueStatus()
            Result.Success(processedCount)
        } catch (e: Exception) {
            Result.Error(e)
        } finally {
            isProcessing.value = false
        }
    }
    
    override suspend fun processQueuedOperationsForRecording(recordingId: String): Result<Unit> {
        if (isProcessing.value || !isOnline()) {
            return Result.Success(Unit)
        }
        
        try {
            val recordingOperations = mutableListOf<QueuedOperation>()
            
            synchronized(operationQueue) {
                val iterator = operationQueue.iterator()
                while (iterator.hasNext()) {
                    val operation = iterator.next()
                    if (operation.recordingId == recordingId) {
                        recordingOperations.add(operation)
                        iterator.remove()
                    }
                }
            }
            
            for (operation in recordingOperations.sortedBy { it.priority.ordinal }) {
                try {
                    processOperation(operation)
                    delay(100)
                } catch (e: Exception) {
                    handleOperationFailure(operation, e)
                }
            }
            
            updateQueueStatus()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun retryFailedOperations(): Result<Int> {
        if (!isOnline()) {
            return Result.Success(0)
        }
        
        var retriedCount = 0
        val currentTime = System.currentTimeMillis()
        
        val operationsToRetry = failedOperations.values
            .filter { operation ->
                operation.retryCount < retryPolicy.maxRetries &&
                (operation.nextRetryTime == null || operation.nextRetryTime <= currentTime)
            }
            .toList()
        
        for (operation in operationsToRetry) {
            try {
                val result = processOperation(operation)
                
                when (result) {
                    is Result.Success -> {
                        failedOperations.remove(operation.id)
                        retriedCount++
                    }
                    is Result.Error -> {
                        val updatedOperation = operation.copy(
                            retryCount = operation.retryCount + 1,
                            nextRetryTime = currentTime + calculateRetryDelay(operation.retryCount + 1)
                        )
                        failedOperations[operation.id] = updatedOperation
                    }
                }
            } catch (e: Exception) {
                val updatedOperation = operation.copy(
                    retryCount = operation.retryCount + 1,
                    nextRetryTime = currentTime + calculateRetryDelay(operation.retryCount + 1),
                    error = SyncError(
                        recordingId = operation.recordingId,
                        errorType = SyncErrorType.UNKNOWN_ERROR,
                        message = e.message ?: "Retry failed"
                    )
                )
                failedOperations[operation.id] = updatedOperation
            }
            
            delay(100)
        }
        
        updateQueueStatus()
        return Result.Success(retriedCount)
    }
    
    override suspend fun retryOperation(operationId: String): Result<Unit> {
        val operation = failedOperations[operationId]
            ?: return Result.Error(IllegalArgumentException("Operation not found: $operationId"))
        
        return try {
            val result = processOperation(operation)
            
            when (result) {
                is Result.Success -> {
                    failedOperations.remove(operationId)
                    updateQueueStatus()
                    Result.Success(Unit)
                }
                is Result.Error -> {
                    val updatedOperation = operation.copy(
                        retryCount = operation.retryCount + 1,
                        nextRetryTime = System.currentTimeMillis() + calculateRetryDelay(operation.retryCount + 1)
                    )
                    failedOperations[operationId] = updatedOperation
                    updateQueueStatus()
                    result
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun clearQueue(): Result<Unit> {
        return try {
            synchronized(operationQueue) {
                operationQueue.clear()
            }
            updateQueueStatus()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun clearFailedOperations(): Result<Unit> {
        return try {
            failedOperations.clear()
            updateQueueStatus()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getQueuedOperationsCount(): Int {
        return synchronized(operationQueue) { operationQueue.size }
    }
    
    override suspend fun getFailedOperationsCount(): Int {
        return failedOperations.size
    }
    
    override fun observeQueueStatus(): Flow<OfflineQueueStatus> = queueStatus.asStateFlow()
    
    override fun observeNetworkStatus(): Flow<Boolean> = networkStatus.asStateFlow()
    
    override suspend fun isOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    override suspend fun setRetryPolicy(policy: RetryPolicy): Result<Unit> {
        return try {
            this.retryPolicy = policy
            // Persist to preferences
            preferencesDataStore.saveString("retry_policy", json.encodeToString(policy))
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getRetryPolicy(): RetryPolicy {
        return retryPolicy
    }
    
    private suspend fun processOperation(operation: QueuedOperation): Result<Unit> {
        return when (operation.type) {
            OperationType.SYNC_METADATA -> {
                operation.recordingId?.let { recordingId ->
                    wearableSyncService.syncRecordingMetadata(recordingId)
                } ?: Result.Error(IllegalArgumentException("Recording ID required for metadata sync"))
            }
            
            OperationType.SYNC_AUDIO_DATA -> {
                operation.recordingId?.let { recordingId ->
                    wearableSyncService.syncRecordingAudioData(recordingId)
                } ?: Result.Error(IllegalArgumentException("Recording ID required for audio sync"))
            }
            
            OperationType.UPDATE_TITLE -> {
                val recordingId = operation.recordingId
                val title = operation.data["title"]
                if (recordingId != null && title != null) {
                    wearableSyncService.updateRecordingTitle(recordingId, title)
                } else {
                    Result.Error(IllegalArgumentException("Recording ID and title required"))
                }
            }
            
            OperationType.UPDATE_TAGS -> {
                val recordingId = operation.recordingId
                val tagsString = operation.data["tags"]
                if (recordingId != null && tagsString != null) {
                    val tags = tagsString.split(",").map { it.trim() }
                    wearableSyncService.updateRecordingTags(recordingId, tags)
                } else {
                    Result.Error(IllegalArgumentException("Recording ID and tags required"))
                }
            }
            
            OperationType.UPDATE_DESCRIPTION -> {
                val recordingId = operation.recordingId
                val description = operation.data["description"]
                if (recordingId != null && description != null) {
                    wearableSyncService.updateRecordingDescription(recordingId, description)
                } else {
                    Result.Error(IllegalArgumentException("Recording ID and description required"))
                }
            }
            
            OperationType.DELETE_RECORDING -> {
                // Implementation for delete operation would go here
                Result.Success(Unit)
            }
        }
    }
    
    private suspend fun handleOperationFailure(operation: QueuedOperation, exception: Throwable) {
        val errorType = when {
            exception.message?.contains("network", ignoreCase = true) == true -> SyncErrorType.NETWORK_ERROR
            exception.message?.contains("timeout", ignoreCase = true) == true -> SyncErrorType.TIMEOUT_ERROR
            exception.message?.contains("disconnected", ignoreCase = true) == true -> SyncErrorType.DEVICE_DISCONNECTED
            else -> SyncErrorType.UNKNOWN_ERROR
        }
        
        val syncError = SyncError(
            recordingId = operation.recordingId,
            errorType = errorType,
            message = exception.message ?: "Operation failed",
            retryCount = operation.retryCount
        )
        
        val failedOperation = operation.copy(
            retryCount = operation.retryCount + 1,
            nextRetryTime = System.currentTimeMillis() + calculateRetryDelay(operation.retryCount + 1),
            error = syncError
        )
        
        if (shouldRetry(errorType) && failedOperation.retryCount < retryPolicy.maxRetries) {
            failedOperations[operation.id] = failedOperation
        }
        
        updateQueueStatus()
    }
    
    private fun shouldRetry(errorType: SyncErrorType): Boolean {
        return when (errorType) {
            SyncErrorType.NETWORK_ERROR -> retryPolicy.retryOnNetworkError
            SyncErrorType.TIMEOUT_ERROR -> retryPolicy.retryOnTimeout
            SyncErrorType.DEVICE_DISCONNECTED -> true
            SyncErrorType.STORAGE_FULL -> false
            SyncErrorType.PERMISSION_DENIED -> false
            SyncErrorType.AUTHENTICATION_ERROR -> false
            else -> false
        }
    }
    
    private fun calculateRetryDelay(retryCount: Int): Long {
        val delay = (retryPolicy.baseDelayMs * retryPolicy.backoffMultiplier.pow(retryCount - 1)).toLong()
        return min(delay, retryPolicy.maxDelayMs)
    }
    
    private fun setupNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                scope.launch {
                    networkStatus.value = true
                    // Auto-process queue when network becomes available
                    delay(1000) // Small delay to ensure stable connection
                    processQueuedOperations()
                }
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                scope.launch {
                    networkStatus.value = false
                }
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    
    private fun startAutoProcessing() {
        scope.launch {
            // Process queue periodically
            while (true) {
                delay(30000) // Every 30 seconds
                if (isOnline() && !isProcessing.value) {
                    processQueuedOperations()
                }
            }
        }
        
        scope.launch {
            // Retry failed operations periodically
            while (true) {
                delay(300000) // Every 5 minutes
                if (isOnline()) {
                    retryFailedOperations()
                }
            }
        }
    }
    
    private fun updateQueueStatus() {
        val queuedCount = synchronized(operationQueue) { operationQueue.size }
        val failedCount = failedOperations.size
        val nextRetryTime = failedOperations.values
            .mapNotNull { it.nextRetryTime }
            .minOrNull()
        
        queueStatus.value = OfflineQueueStatus(
            queuedOperations = queuedCount,
            failedOperations = failedCount,
            isProcessing = isProcessing.value,
            lastProcessedTime = System.currentTimeMillis(),
            nextRetryTime = nextRetryTime
        )
    }
    
    private fun generateOperationId(): String {
        return "op_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}
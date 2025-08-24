package com.projectecho.core.wearable.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncError(
    val id: String = java.util.UUID.randomUUID().toString(),
    val recordingId: String? = null,
    val errorType: SyncErrorType,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val canRetry: Boolean = true,
    val deviceId: String? = null
) {
    val isRetryable: Boolean
        get() = canRetry && retryCount < maxRetries
}

@Serializable
enum class SyncErrorType {
    NETWORK_ERROR,
    DEVICE_DISCONNECTED,
    TIMEOUT_ERROR,
    AUTHENTICATION_ERROR,
    STORAGE_FULL,
    INVALID_DATA,
    PERMISSION_DENIED,
    UNKNOWN_ERROR
}
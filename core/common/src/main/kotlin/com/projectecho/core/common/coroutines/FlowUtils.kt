package com.projectecho.core.common.coroutines

import com.projectecho.core.common.result.Result
import kotlinx.coroutines.delay as coroutineDelay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.pow

/**
 * Utility functions for Flow operations with error handling and retry logic.
 */
object FlowUtils {

    /**
     * Retry flow with exponential backoff for network-related exceptions.
     */
    fun <T> Flow<T>.retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        backoffMultiplier: Double = 2.0,
        retryPredicate: (Throwable) -> Boolean = { isRetryableException(it) }
    ): Flow<T> {
        return retryWhen { cause, attempt ->
            if (attempt < maxRetries && retryPredicate(cause)) {
                val delayMs = (initialDelayMs * backoffMultiplier.pow(attempt.toDouble()))
                    .toLong()
                    .coerceAtMost(maxDelayMs)
                
                coroutineDelay(delayMs)
                true
            } else {
                false
            }
        }
    }

    /**
     * Handle errors and convert to Result with proper error mapping.
     */
    fun <T> Flow<T>.handleErrors(): Flow<Result<T>> {
        return flow {
            try {
                collect { value ->
                    emit(Result.Success(value))
                }
            } catch (e: Exception) {
                emit(Result.Error(mapException(e)))
            }
        }
    }

    /**
     * Safe flow collection that catches exceptions and converts them to Error results.
     */
    fun <T> Flow<Result<T>>.safeCollect(): Flow<Result<T>> {
        return catch { exception ->
            emit(Result.Error(mapException(exception)))
        }
    }

    /**
     * Execute a flow operation with timeout and error handling.
     */
    fun <T> safeFlow(
        timeoutMs: Long = 30000L, // 30 seconds default timeout
        operation: suspend () -> T
    ): Flow<Result<T>> = flow {
        emit(Result.Loading)
        try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                val result = operation()
                emit(Result.Success(result))
            }
        } catch (e: Exception) {
            emit(Result.Error(mapException(e)))
        }
    }

    /**
     * Determine if an exception is retryable.
     */
    private fun isRetryableException(exception: Throwable): Boolean {
        return when (exception) {
            is SocketTimeoutException,
            is UnknownHostException,
            is IOException -> true
            else -> false
        }
    }

    /**
     * Map exceptions to appropriate domain exceptions.
     */
    private fun mapException(exception: Throwable): Throwable {
        return when (exception) {
            is SocketTimeoutException -> Exception("Connection timeout. Please try again.")
            is UnknownHostException -> Exception("No internet connection. Please check your network.")
            is IOException -> Exception("Network error occurred. Please try again.")
            is SecurityException -> Exception("Permission denied. Please check app permissions.")
            is IllegalArgumentException -> Exception("Invalid input provided.")
            is IllegalStateException -> Exception("Operation not allowed in current state.")
            else -> Exception("An unexpected error occurred: ${exception.message}")
        }
    }

    /**
     * Combine multiple flows with error handling.
     */
    fun <T1, T2, R> combineWithErrorHandling(
        flow1: Flow<Result<T1>>,
        flow2: Flow<Result<T2>>,
        transform: suspend (T1, T2) -> R
    ): Flow<Result<R>> = flow {
        combine(flow1, flow2) { result1, result2 ->
            when {
                result1 is Result.Loading || result2 is Result.Loading -> Result.Loading
                result1 is Result.Error -> result1
                result2 is Result.Error -> result2
                result1 is Result.Success && result2 is Result.Success -> {
                    try {
                        Result.Success(transform(result1.data, result2.data))
                    } catch (e: Exception) {
                        Result.Error(mapException(e))
                    }
                }
                else -> Result.Error(Exception("Unexpected state in flow combination"))
            }
        }.collect { result ->
            emit(result)
        }
    }

    /**
     * Debounce flow emissions with error handling.
     */
    fun <T> Flow<T>.debounceWithErrorHandling(timeoutMs: Long = 300L): Flow<T> {
        return debounce(timeoutMs)
            .catch { exception: Throwable ->
                // Log the error but don't emit it, just skip the emission
                android.util.Log.w("FlowUtils", "Error in debounced flow", exception)
            }
    }

    /**
     * Throttle flow emissions to prevent overwhelming downstream consumers.
     */
    fun <T> Flow<T>.throttleFirst(periodMs: Long): Flow<T> = flow {
        var lastEmissionTime = 0L
        collect { value ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmissionTime >= periodMs) {
                lastEmissionTime = currentTime
                emit(value)
            }
        }
    }
}
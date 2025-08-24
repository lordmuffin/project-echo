package com.projectecho.core.common.result

/**
 * A sealed class representing the result of an operation that can succeed or fail.
 * This provides type-safe error handling throughout the application.
 */
sealed class Result<out T> {
    /**
     * Success result containing data of type T.
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * Error result containing error information.
     */
    data class Error(val exception: Throwable) : Result<Nothing>()

    /**
     * Loading state to represent ongoing operations.
     */
    object Loading : Result<Nothing>()
}

/**
 * Extension function to check if result is success.
 */
inline val Result<*>.isSuccess: Boolean
    get() = this is Result.Success

/**
 * Extension function to check if result is error.
 */
inline val Result<*>.isError: Boolean
    get() = this is Result.Error

/**
 * Extension function to check if result is loading.
 */
inline val Result<*>.isLoading: Boolean
    get() = this is Result.Loading

/**
 * Extension function to get data from success result or null.
 */
inline fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    else -> null
}

/**
 * Extension function to get exception from error result or null.
 */
inline fun <T> Result<T>.exceptionOrNull(): Throwable? = when (this) {
    is Result.Error -> exception
    else -> null
}

/**
 * Extension function to handle both success and error cases.
 */
inline fun <T> Result<T>.fold(
    onSuccess: (value: T) -> Unit,
    onError: (exception: Throwable) -> Unit,
    onLoading: () -> Unit = {}
) {
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(exception)
        is Result.Loading -> onLoading()
    }
}

/**
 * Extension function to map success result to another type.
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}

/**
 * Extension function to handle errors and provide default value.
 */
inline fun <T> Result<T>.getOrDefault(defaultValue: T): T = when (this) {
    is Result.Success -> data
    else -> defaultValue
}
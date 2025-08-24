package com.projectecho.core.network.exception

/**
 * Sealed class hierarchy for network-related exceptions.
 * Provides type-safe error handling for different network scenarios.
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * No internet connection available.
     */
    data class NoConnection(override val message: String) : NetworkException(message)

    /**
     * Request timeout occurred.
     */
    data class Timeout(override val message: String) : NetworkException(message)

    /**
     * Authentication failed (401).
     */
    data class Unauthorized(override val message: String) : NetworkException(message)

    /**
     * Access forbidden (403).
     */
    data class Forbidden(override val message: String) : NetworkException(message)

    /**
     * Resource not found (404).
     */
    data class NotFound(override val message: String) : NetworkException(message)

    /**
     * Rate limit exceeded (429).
     */
    data class RateLimited(override val message: String) : NetworkException(message)

    /**
     * Server error (5xx).
     */
    data class ServerError(override val message: String) : NetworkException(message)

    /**
     * HTTP error with specific status code.
     */
    data class HttpError(
        val statusCode: Int,
        override val message: String
    ) : NetworkException(message)

    /**
     * General network error (IOException, etc.).
     */
    data class NetworkError(override val message: String) : NetworkException(message)

    /**
     * Unknown error occurred.
     */
    data class Unknown(override val message: String) : NetworkException(message)
}

/**
 * Extension function to convert NetworkException to user-friendly error messages.
 */
fun NetworkException.toUserMessage(): String = when (this) {
    is NetworkException.NoConnection -> "No internet connection. Please check your network."
    is NetworkException.Timeout -> "Request timed out. Please try again."
    is NetworkException.Unauthorized -> "Please log in again."
    is NetworkException.Forbidden -> "Access denied."
    is NetworkException.NotFound -> "The requested content was not found."
    is NetworkException.RateLimited -> "Too many requests. Please wait and try again."
    is NetworkException.ServerError -> "Server is temporarily unavailable."
    is NetworkException.HttpError -> "Network error occurred. Please try again."
    is NetworkException.NetworkError -> "Connection error. Please check your network."
    is NetworkException.Unknown -> "An unexpected error occurred."
}
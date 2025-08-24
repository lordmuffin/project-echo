package com.projectecho.core.network.interceptor

import com.projectecho.core.network.exception.NetworkException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * OkHttp interceptor for handling network errors and converting them to domain-specific exceptions.
 * Provides consistent error handling across all network operations.
 */
class ErrorInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        try {
            val response = chain.proceed(request)
            
            // Handle HTTP error codes
            if (!response.isSuccessful) {
                throw when (response.code) {
                    401 -> NetworkException.Unauthorized("Authentication failed")
                    403 -> NetworkException.Forbidden("Access denied")
                    404 -> NetworkException.NotFound("Resource not found")
                    408 -> NetworkException.Timeout("Request timeout")
                    429 -> NetworkException.RateLimited("Too many requests")
                    in 500..599 -> NetworkException.ServerError("Server error: ${response.code}")
                    else -> NetworkException.HttpError(response.code, "HTTP error: ${response.code}")
                }
            }
            
            return response
            
        } catch (e: Exception) {
            throw when (e) {
                is NetworkException -> e // Re-throw our custom exceptions
                is SocketTimeoutException -> NetworkException.Timeout("Connection timeout")
                is UnknownHostException -> NetworkException.NoConnection("No internet connection")
                is IOException -> NetworkException.NetworkError("Network error: ${e.message}")
                else -> NetworkException.Unknown("Unknown error: ${e.message}")
            }
        }
    }
}
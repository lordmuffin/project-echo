package com.projectecho.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor for adding authentication headers to network requests.
 * Handles API key, bearer tokens, and user authentication.
 */
class AuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip auth for certain endpoints
        if (shouldSkipAuth(originalRequest.url.encodedPath)) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .apply {
                // Add API key header
                addHeader("X-API-Key", getApiKey())
                
                // Add bearer token if available
                getBearerToken()?.let { token ->
                    addHeader("Authorization", "Bearer $token")
                }
                
                // Add device information
                addHeader("X-Device-Type", "wear-os")
                addHeader("X-App-Version", getAppVersion())
            }
            .build()

        return chain.proceed(authenticatedRequest)
    }

    private fun shouldSkipAuth(path: String): Boolean {
        return path in listOf(
            "/health",
            "/version",
            "/auth/refresh"
        )
    }

    private fun getApiKey(): String {
        // In production, this should come from secure storage
        return "your-api-key-here"
    }

    private fun getBearerToken(): String? {
        // In production, retrieve from secure storage or DataStore
        // This would typically be managed by an authentication manager
        return null // TODO: Implement token management
    }

    private fun getAppVersion(): String {
        return "1.0.0" // TODO: Get from BuildConfig
    }
}
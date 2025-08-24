package com.projectecho.core.common.coroutines

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * Centralized coroutine exception handler for the application.
 * Provides structured error handling and logging for uncaught coroutine exceptions.
 */
@Singleton
class CoroutineErrorHandler @Inject constructor() {

    /**
     * Global exception handler for coroutines.
     * Logs errors and prevents app crashes from uncaught exceptions.
     */
    val handler = CoroutineExceptionHandler { context, exception ->
        handleException(context, exception)
    }

    /**
     * Create a supervised scope with error handling.
     * Uses SupervisorJob to prevent child failures from canceling siblings.
     */
    fun createSupervisedScope(baseScope: CoroutineScope): CoroutineScope {
        return baseScope + SupervisorJob() + handler
    }

    /**
     * Handle exceptions from coroutines.
     * In production, this would integrate with crash reporting services.
     */
    private fun handleException(context: CoroutineContext, exception: Throwable) {
        // Log the error
        android.util.Log.e("CoroutineError", "Uncaught exception in coroutine", exception)
        
        // In production, report to crash analytics
        // FirebaseCrashlytics.getInstance().recordException(exception)
        
        // Handle specific exception types
        when (exception) {
            is OutOfMemoryError -> {
                // Handle memory issues
                System.gc()
                android.util.Log.e("CoroutineError", "Out of memory error handled")
            }
            is SecurityException -> {
                // Handle security/permission issues
                android.util.Log.e("CoroutineError", "Security exception: ${exception.message}")
            }
            is IllegalStateException -> {
                // Handle state issues
                android.util.Log.e("CoroutineError", "Illegal state: ${exception.message}")
            }
            else -> {
                // Generic error handling
                android.util.Log.e("CoroutineError", "Unhandled exception: ${exception.javaClass.simpleName}")
            }
        }
    }
}
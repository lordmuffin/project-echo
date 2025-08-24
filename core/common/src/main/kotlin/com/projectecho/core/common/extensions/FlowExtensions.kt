package com.projectecho.core.common.extensions

import com.projectecho.core.common.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Extension function to wrap Flow emissions in Result.Success and catch exceptions as Result.Error.
 * Optionally starts with Result.Loading.
 */
fun <T> Flow<T>.asResult(emitLoading: Boolean = false): Flow<Result<T>> {
    return this
        .map<T, Result<T>> { Result.Success(it) }
        .catch { emit(Result.Error(it)) }
        .let { flow ->
            if (emitLoading) {
                flow.onStart { emit(Result.Loading) }
            } else {
                flow
            }
        }
}

/**
 * Extension function to handle Result emissions and extract success values.
 */
fun <T> Flow<Result<T>>.onSuccess(): Flow<T> {
    return this.map { result ->
        when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw result.exception
            is Result.Loading -> throw IllegalStateException("Cannot extract data from loading state")
        }
    }
}

/**
 * Extension function to filter only success results.
 */
fun <T> Flow<Result<T>>.filterSuccess(): Flow<T> {
    return this.map { result ->
        when (result) {
            is Result.Success -> result.data
            else -> null
        }
    }.map { it ?: return@map null }
        .map { it!! } // Safe cast after null check
}
package com.projectecho.core.network.service

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit service interface for audio synchronization with cloud backend.
 * Handles uploading recordings, syncing metadata, and managing user data.
 */
interface AudioSyncService {

    /**
     * Upload an audio file to the cloud storage.
     */
    @Multipart
    @POST("recordings/upload")
    suspend fun uploadRecording(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: String
    ): Response<AudioUploadResponse>

    /**
     * Get synchronization status for all recordings.
     */
    @GET("recordings/sync-status")
    suspend fun getSyncStatus(
        @Query("lastSync") lastSyncTimestamp: Long? = null
    ): Response<SyncStatusResponse>

    /**
     * Download a recording by ID.
     */
    @GET("recordings/{id}/download")
    @Streaming
    suspend fun downloadRecording(
        @Path("id") recordingId: String
    ): Response<okhttp3.ResponseBody>

    /**
     * Delete a recording from cloud storage.
     */
    @DELETE("recordings/{id}")
    suspend fun deleteRecording(
        @Path("id") recordingId: String
    ): Response<Unit>

    /**
     * Get user's cloud storage usage.
     */
    @GET("user/storage")
    suspend fun getStorageUsage(): Response<StorageUsageResponse>

    /**
     * Sync recording metadata without uploading files.
     */
    @POST("recordings/sync-metadata")
    suspend fun syncMetadata(
        @Body recordings: List<RecordingMetadata>
    ): Response<SyncStatusResponse>
}

/**
 * Response models for network operations
 */
@kotlinx.serialization.Serializable
data class AudioUploadResponse(
    val success: Boolean,
    val recordingId: String,
    val cloudUrl: String,
    val message: String? = null
)

@kotlinx.serialization.Serializable
data class SyncStatusResponse(
    val success: Boolean,
    val recordings: List<CloudRecording>,
    val lastSyncTimestamp: Long,
    val totalCount: Int
)

@kotlinx.serialization.Serializable
data class CloudRecording(
    val id: String,
    val title: String,
    val duration: Long,
    val timestamp: Long,
    val size: Long,
    val cloudUrl: String,
    val syncStatus: SyncStatus
)

@kotlinx.serialization.Serializable
data class StorageUsageResponse(
    val usedBytes: Long,
    val totalBytes: Long,
    val recordingCount: Int
)

@kotlinx.serialization.Serializable
data class RecordingMetadata(
    val id: String,
    val title: String,
    val duration: Long,
    val timestamp: Long,
    val size: Long,
    val format: String,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int
)

@kotlinx.serialization.Serializable
enum class SyncStatus {
    PENDING,
    UPLOADING,
    SYNCED,
    FAILED,
    DELETED
}
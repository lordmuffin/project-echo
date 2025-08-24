package com.projectecho.core.data.source.remote

import com.projectecho.core.common.result.Result
import com.projectecho.core.domain.model.AudioRecording
import com.projectecho.core.network.service.AudioSyncService
import com.projectecho.core.network.service.RecordingMetadata
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Remote data source for audio synchronization with cloud backend.
 * Handles uploading, downloading, and syncing audio recordings with the server.
 */
@Singleton
class RemoteAudioDataSource @Inject constructor(
    private val audioSyncService: AudioSyncService
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * Upload a recording to the cloud storage.
     */
    suspend fun uploadRecording(recording: AudioRecording): Result<String> {
        return try {
            val file = File(recording.filePath)
            if (!file.exists()) {
                return Result.Error(IllegalArgumentException("Recording file not found: ${recording.filePath}"))
            }

            // Create multipart request
            val requestFile = file.asRequestBody("audio/wav".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            // Create metadata JSON
            val metadata = RecordingMetadata(
                id = recording.id,
                title = recording.title,
                duration = recording.duration,
                timestamp = recording.timestamp,
                size = recording.size,
                format = recording.format.name,
                sampleRate = recording.sampleRate,
                channels = recording.channels,
                bitDepth = recording.bitDepth
            )
            val metadataJson = json.encodeToString(metadata)
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaTypeOrNull())

            // Upload to server
            val response = audioSyncService.uploadRecording(filePart, metadataJson)
            
            if (response.isSuccessful) {
                val uploadResponse = response.body()
                if (uploadResponse?.success == true) {
                    Result.Success(uploadResponse.cloudUrl)
                } else {
                    Result.Error(Exception("Upload failed: ${uploadResponse?.message}"))
                }
            } else {
                Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Schedule an upload for later (when network is available, etc.).
     */
    suspend fun scheduleUpload(recording: AudioRecording): Result<Unit> {
        return try {
            // In a real implementation, this would:
            // 1. Add to upload queue
            // 2. Schedule with WorkManager
            // 3. Handle retry logic
            
            // For now, just attempt immediate upload
            when (uploadRecording(recording)) {
                is Result.Success -> Result.Success(Unit)
                is Result.Error -> {
                    // Schedule for later retry
                    // TODO: Implement WorkManager scheduling
                    Result.Success(Unit) // Don't fail the recording save
                }
                is Result.Loading -> Result.Success(Unit)
            }
        } catch (e: Exception) {
            // Don't fail the local save if cloud upload fails
            Result.Success(Unit)
        }
    }

    /**
     * Download a recording from cloud storage.
     */
    suspend fun downloadRecording(recordingId: String, localPath: String): Result<String> {
        return try {
            val response = audioSyncService.downloadRecording(recordingId)
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // Save to local file
                    val file = File(localPath)
                    file.parentFile?.mkdirs()
                    
                    body.byteStream().use { inputStream ->
                        file.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    
                    Result.Success(localPath)
                } else {
                    Result.Error(Exception("Empty response body"))
                }
            } else {
                Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Delete a recording from cloud storage.
     */
    suspend fun deleteRecording(recordingId: String): Result<Unit> {
        return try {
            val response = audioSyncService.deleteRecording(recordingId)
            
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get synchronization status for all recordings.
     */
    suspend fun getSyncStatus(lastSyncTimestamp: Long? = null): Result<List<com.projectecho.core.network.service.CloudRecording>> {
        return try {
            val response = audioSyncService.getSyncStatus(lastSyncTimestamp)
            
            if (response.isSuccessful) {
                val syncResponse = response.body()
                if (syncResponse?.success == true) {
                    Result.Success(syncResponse.recordings)
                } else {
                    Result.Error(Exception("Sync failed"))
                }
            } else {
                Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Sync recording metadata without uploading files.
     */
    suspend fun syncMetadata(recordings: List<AudioRecording>): Result<Unit> {
        return try {
            val metadataList = recordings.map { recording ->
                RecordingMetadata(
                    id = recording.id,
                    title = recording.title,
                    duration = recording.duration,
                    timestamp = recording.timestamp,
                    size = recording.size,
                    format = recording.format.name,
                    sampleRate = recording.sampleRate,
                    channels = recording.channels,
                    bitDepth = recording.bitDepth
                )
            }

            val response = audioSyncService.syncMetadata(metadataList)
            
            if (response.isSuccessful) {
                val syncResponse = response.body()
                if (syncResponse?.success == true) {
                    Result.Success(Unit)
                } else {
                    Result.Error(Exception("Metadata sync failed"))
                }
            } else {
                Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get user's cloud storage usage information.
     */
    suspend fun getStorageUsage(): Result<com.projectecho.core.network.service.StorageUsageResponse> {
        return try {
            val response = audioSyncService.getStorageUsage()
            
            if (response.isSuccessful) {
                val storageResponse = response.body()
                if (storageResponse != null) {
                    Result.Success(storageResponse)
                } else {
                    Result.Error(Exception("Empty storage response"))
                }
            } else {
                Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
package com.projectecho.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.projectecho.core.domain.model.AudioFormat
import com.projectecho.core.domain.model.AudioRecording

@Entity(tableName = "audio_recordings")
data class AudioRecordingEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val filePath: String,
    val duration: Long,
    val timestamp: Long,
    val size: Long,
    val format: String,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int
)

/**
 * Extension function to convert entity to domain model.
 */
fun AudioRecordingEntity.toDomainModel(): AudioRecording {
    return AudioRecording(
        id = id,
        title = title,
        filePath = filePath,
        duration = duration,
        timestamp = timestamp,
        size = size,
        format = AudioFormat.valueOf(format),
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth
    )
}

/**
 * Extension function to convert domain model to entity.
 */
fun AudioRecording.toEntity(): AudioRecordingEntity {
    return AudioRecordingEntity(
        id = id,
        title = title,
        filePath = filePath,
        duration = duration,
        timestamp = timestamp,
        size = size,
        format = format.name,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth
    )
}
package com.projectecho.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.projectecho.core.database.entity.AudioRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioRecordingDao {

    @Query("SELECT * FROM audio_recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<AudioRecordingEntity>>

    @Query("SELECT * FROM audio_recordings WHERE id = :id")
    suspend fun getRecordingById(id: String): AudioRecordingEntity?

    @Query("SELECT * FROM audio_recordings WHERE title LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchRecordings(query: String): Flow<List<AudioRecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: AudioRecordingEntity)

    @Update
    suspend fun updateRecording(recording: AudioRecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: AudioRecordingEntity)

    @Query("DELETE FROM audio_recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: String)

    @Query("SELECT COUNT(*) FROM audio_recordings")
    suspend fun getRecordingCount(): Int

    @Query("SELECT SUM(size) FROM audio_recordings")
    suspend fun getTotalSize(): Long?

    @Query("DELETE FROM audio_recordings")
    suspend fun deleteAllRecordings()
}
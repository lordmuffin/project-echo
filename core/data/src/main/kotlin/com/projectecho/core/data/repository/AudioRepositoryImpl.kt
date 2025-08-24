package com.projectecho.core.data.repository

import com.projectecho.core.common.di.IoDispatcher
import com.projectecho.core.common.result.Result
import com.projectecho.core.data.source.local.LocalAudioDataSource
import com.projectecho.core.data.source.remote.RemoteAudioDataSource
import com.projectecho.core.database.dao.AudioRecordingDao
import com.projectecho.core.database.entity.toDomainModel
import com.projectecho.core.database.entity.toEntity
import com.projectecho.core.domain.model.AudioLevel
import com.projectecho.core.domain.model.AudioRecording
import com.projectecho.core.domain.model.PlaybackState
import com.projectecho.core.domain.model.RecordingConfig
import com.projectecho.core.domain.model.RecordingState
import com.projectecho.core.domain.repository.AudioRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AudioRepository that coordinates between local and remote data sources.
 * Implements the repository pattern with proper separation of concerns.
 */
@Singleton
class AudioRepositoryImpl @Inject constructor(
    private val localDataSource: LocalAudioDataSource,
    private val remoteDataSource: RemoteAudioDataSource,
    private val audioRecordingDao: AudioRecordingDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : AudioRepository {

    override suspend fun startRecording(config: RecordingConfig): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.startRecording(config)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun stopRecording(): Result<AudioRecording> {
        return withContext(dispatcher) {
            try {
                val recording = localDataSource.stopRecording()
                if (recording is Result.Success) {
                    // Save to database
                    audioRecordingDao.insertRecording(recording.data.toEntity())
                    
                    // Schedule cloud sync if enabled
                    remoteDataSource.scheduleUpload(recording.data)
                }
                recording
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun pauseRecording(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.pauseRecording()
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun resumeRecording(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.resumeRecording()
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun cancelRecording(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.cancelRecording()
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override fun getRecordingState(): Flow<RecordingState> {
        return localDataSource.getRecordingState()
    }

    override fun getAudioLevels(): Flow<AudioLevel> {
        return localDataSource.getAudioLevels()
    }

    override fun getAllRecordings(): Flow<List<AudioRecording>> {
        return audioRecordingDao.getAllRecordings()
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override suspend fun getRecordingById(id: String): Result<AudioRecording?> {
        return withContext(dispatcher) {
            try {
                val entity = audioRecordingDao.getRecordingById(id)
                Result.Success(entity?.toDomainModel())
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun deleteRecording(id: String): Result<Unit> {
        return withContext(dispatcher) {
            try {
                // Delete from local storage
                localDataSource.deleteRecording(id)
                
                // Delete from database
                audioRecordingDao.deleteRecordingById(id)
                
                // Delete from cloud if synced
                remoteDataSource.deleteRecording(id)
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun playRecording(recording: AudioRecording): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.playRecording(recording)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun stopPlayback(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.stopPlayback()
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun pausePlayback(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.pausePlayback()
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun resumePlayback(): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.resumePlayback()
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override suspend fun seekTo(position: Long): Result<Unit> {
        return withContext(dispatcher) {
            try {
                localDataSource.seekTo(position)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }

    override fun getPlaybackState(): Flow<PlaybackState> {
        return localDataSource.getPlaybackState()
    }

    override fun getPlaybackPosition(): Flow<Long> {
        return localDataSource.getPlaybackPosition()
    }
}
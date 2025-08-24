package com.projectecho.core.domain.usecase

import com.projectecho.core.common.di.IoDispatcher
import com.projectecho.core.common.result.Result
import com.projectecho.core.domain.model.RecordingConfig
import com.projectecho.core.domain.repository.AudioRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for starting audio recording.
 */
class StartRecordingUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(config: RecordingConfig): Result<Unit> {
        return withContext(dispatcher) {
            audioRepository.startRecording(config)
        }
    }
}

/**
 * Use case for stopping audio recording.
 */
class StopRecordingUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke() = withContext(dispatcher) {
        audioRepository.stopRecording()
    }
}

/**
 * Use case for getting all recordings.
 */
class GetAllRecordingsUseCase @Inject constructor(
    private val audioRepository: AudioRepository
) {
    operator fun invoke() = audioRepository.getAllRecordings()
}

/**
 * Use case for deleting a recording.
 */
class DeleteRecordingUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(recordingId: String): Result<Unit> {
        return withContext(dispatcher) {
            audioRepository.deleteRecording(recordingId)
        }
    }
}
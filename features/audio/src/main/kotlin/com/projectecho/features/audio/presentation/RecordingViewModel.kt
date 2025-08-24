package com.projectecho.features.audio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectecho.core.common.extensions.asResult
import com.projectecho.core.common.result.Result
import com.projectecho.core.domain.model.AudioLevel
import com.projectecho.core.domain.model.AudioRecording
import com.projectecho.core.domain.model.RecordingConfig
import com.projectecho.core.domain.model.RecordingState
import com.projectecho.core.domain.repository.AudioRepository
import com.projectecho.core.domain.usecase.DeleteRecordingUseCase
import com.projectecho.core.domain.usecase.GetAllRecordingsUseCase
import com.projectecho.core.domain.usecase.StartRecordingUseCase
import com.projectecho.core.domain.usecase.StopRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingUiState(
    val recordingState: RecordingState = RecordingState.Idle,
    val recordings: Result<List<AudioRecording>> = Result.Loading,
    val audioLevel: AudioLevel? = null,
    val currentRecording: AudioRecording? = null,
    val isProcessing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val startRecordingUseCase: StartRecordingUseCase,
    private val stopRecordingUseCase: StopRecordingUseCase,
    private val getAllRecordingsUseCase: GetAllRecordingsUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    init {
        observeRecordingState()
        observeAudioLevels()
        loadRecordings()
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            audioRepository.getRecordingState().collect { state ->
                _uiState.value = _uiState.value.copy(recordingState = state)
            }
        }
    }

    private fun observeAudioLevels() {
        viewModelScope.launch {
            audioRepository.getAudioLevels().collect { level ->
                _uiState.value = _uiState.value.copy(audioLevel = level)
            }
        }
    }

    private fun loadRecordings() {
        viewModelScope.launch {
            getAllRecordingsUseCase()
                .asResult()
                .collect { result ->
                    _uiState.value = _uiState.value.copy(recordings = result)
                }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            
            val config = RecordingConfig(
                enableNoiseReduction = true,
                enableAutoGainControl = true,
                enableEchoCancellation = true
            )
            
            when (val result = startRecordingUseCase(config)) {
                is Result.Success -> {
                    // Recording started successfully
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.exception.message ?: "Failed to start recording"
                    )
                }
                is Result.Loading -> {
                    // Handle loading state if needed
                }
            }
            
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
            
            when (val result = stopRecordingUseCase()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(currentRecording = result.data)
                    loadRecordings() // Refresh the recordings list
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.exception.message ?: "Failed to stop recording"
                    )
                }
                is Result.Loading -> {
                    // Handle loading state if needed
                }
            }
            
            _uiState.value = _uiState.value.copy(isProcessing = false)
        }
    }

    fun pauseRecording() {
        viewModelScope.launch {
            audioRepository.pauseRecording()
        }
    }

    fun resumeRecording() {
        viewModelScope.launch {
            audioRepository.resumeRecording()
        }
    }

    fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            when (val result = deleteRecordingUseCase(recordingId)) {
                is Result.Success -> {
                    loadRecordings() // Refresh the recordings list
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.exception.message ?: "Failed to delete recording"
                    )
                }
                is Result.Loading -> {
                    // Handle loading state if needed
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
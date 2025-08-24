package com.projectecho.watch.presentation.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.projectecho.core.ui.components.RecordingButton
import com.projectecho.core.ui.theme.ProjectEchoWearTheme
import com.projectecho.features.audio.presentation.RecordingViewModel

@Composable
fun WatchRecordingScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToRecordings: () -> Unit = {},
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    WatchRecordingContent(
        recordingState = uiState.recordingState,
        onStartRecording = viewModel::startRecording,
        onStopRecording = viewModel::stopRecording,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToRecordings = onNavigateToRecordings
    )
}

@Composable
private fun WatchRecordingContent(
    recordingState: com.projectecho.core.domain.model.RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecordings: () -> Unit
) {
    ProjectEchoWearTheme {
        Scaffold(
            timeText = {
                TimeText()
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = when (recordingState) {
                            is com.projectecho.core.domain.model.RecordingState.Idle -> "Ready to Record"
                            is com.projectecho.core.domain.model.RecordingState.Recording -> "Recording..."
                            is com.projectecho.core.domain.model.RecordingState.Paused -> "Paused"
                            is com.projectecho.core.domain.model.RecordingState.Error -> "Error: ${recordingState.message}"
                        },
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onBackground
                    )

                    RecordingButton(
                        recordingState = recordingState,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording
                    )
                }
            }
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun WatchRecordingScreenPreview() {
    WatchRecordingContent(
        recordingState = com.projectecho.core.domain.model.RecordingState.Idle,
        onStartRecording = {},
        onStopRecording = {},
        onNavigateToSettings = {},
        onNavigateToRecordings = {}
    )
}
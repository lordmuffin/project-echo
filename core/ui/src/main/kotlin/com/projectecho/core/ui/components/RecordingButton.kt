package com.projectecho.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.projectecho.core.domain.model.RecordingState

@Composable
fun RecordingButton(
    recordingState: RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }
    val isRecording = recordingState is RecordingState.Recording

    LaunchedEffect(isRecording) {
        if (isRecording) {
            scale.animateTo(
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            scale.animateTo(1f)
        }
    }

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .background(
                color = when (recordingState) {
                    is RecordingState.Recording -> MaterialTheme.colorScheme.error
                    is RecordingState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            .clickable {
                when (recordingState) {
                    is RecordingState.Idle, is RecordingState.Paused -> onStartRecording()
                    is RecordingState.Recording -> onStopRecording()
                    is RecordingState.Error -> onStartRecording()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Preview
@Composable
private fun RecordingButtonPreview() {
    RecordingButton(
        recordingState = RecordingState.Idle,
        onStartRecording = {},
        onStopRecording = {}
    )
}
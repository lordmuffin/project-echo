package com.projectecho.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

/**
 * Waveform visualizer with gesture controls for Project Echo.
 * Displays audio levels and provides gesture-based recording controls.
 */
@Composable
fun WaveformVisualizerWithGestures(
    audioLevel: Float,
    isRecording: Boolean,
    isPaused: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit = {},
    showGuidance: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        // Waveform visualization
        WaveformDisplay(
            audioLevel = audioLevel,
            isRecording = isRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Recording control with gestures
        RecordingControlButton(
            isRecording = isRecording,
            isPaused = isPaused,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onPauseRecording = onPauseRecording,
            modifier = Modifier.size(80.dp)
        )
    }
}

@Composable
private fun WaveformDisplay(
    audioLevel: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val waveformData = remember { mutableStateListOf<Float>() }
    
    // Update waveform data when recording
    LaunchedEffect(audioLevel, isRecording) {
        if (isRecording) {
            waveformData.add(audioLevel)
            if (waveformData.size > 100) {
                waveformData.removeAt(0)
            }
        }
    }
    
    Canvas(modifier = modifier) {
        drawWaveform(waveformData, isRecording)
    }
}

private fun DrawScope.drawWaveform(
    waveformData: List<Float>,
    isRecording: Boolean
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2
    
    if (waveformData.isEmpty()) return
    
    val stepWidth = width / waveformData.size
    val color = if (isRecording) Color(0xFF1976D2) else Color.Gray
    
    waveformData.forEachIndexed { index, level ->
        val x = index * stepWidth
        val amplitude = (level * height * 0.4f).coerceIn(0f, height * 0.4f)
        
        drawLine(
            color = color,
            start = Offset(x, centerY - amplitude),
            end = Offset(x, centerY + amplitude),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
private fun RecordingControlButton(
    isRecording: Boolean,
    isPaused: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonColor = when {
        isRecording && !isPaused -> MaterialTheme.colorScheme.error
        isPaused -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    FloatingActionButton(
        onClick = {
            when {
                !isRecording -> onStartRecording()
                isPaused -> onStartRecording() // Resume
                else -> onStopRecording()
            }
        },
        containerColor = buttonColor,
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        if (isRecording && !isPaused) {
                            onPauseRecording()
                        }
                    }
                )
            }
    ) {
        when {
            !isRecording -> Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Start Recording"
            )
            isPaused -> Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Resume Recording"
            )
            else -> Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop Recording"
            )
        }
    }
}
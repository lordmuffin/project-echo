package com.projectecho

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectecho.permissions.PermissionHandler
import com.projectecho.ui.RecordingController
import com.projectecho.ui.RecordingUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Activity for Project Echo Wear OS audio recorder.
 * Implements Story 1: Quick Recording Start with one-tap recording interface.
 * Implements Story 2: Long Recording Stability with health monitoring.
 * 
 * Features:
 * - One-tap recording with large, accessible button
 * - Real-time recording status and duration
 * - Permission handling with clear user guidance
 * - Health monitoring display
 * - Wear OS optimized UI
 */
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var permissionHandler: PermissionHandler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "MainActivity created")
        
        // Initialize permission handler
        permissionHandler = PermissionHandler(this)
        
        setContent {
            ProjectEchoTheme {
                RecordingScreen(permissionHandler = permissionHandler)
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHandler.onPermissionResult(requestCode, permissions, grantResults)
    }
    
    override fun onResume() {
        super.onResume()
        // Check permission status on resume
        permissionHandler.hasAudioPermission()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    permissionHandler: PermissionHandler,
    recordingController: RecordingController = viewModel { RecordingController(permissionHandler.context) }
) {
    val uiState by recordingController.uiState.collectAsState()
    val permissionState by permissionHandler.permissionState.collectAsState()
    
    // Track current recording time
    var currentDuration by remember { mutableStateOf(0L) }
    
    // Update duration every second when recording
    LaunchedEffect(uiState.isRecording) {
        while (uiState.isRecording) {
            currentDuration = recordingController.getCurrentDuration()
            delay(1000)
        }
        if (!uiState.isRecording) {
            currentDuration = 0L
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                // App title
                Text(
                    text = "Project Echo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Recording duration (when recording)
                if (uiState.isRecording) {
                    Text(
                        text = formatDuration(currentDuration),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // Main recording button
                RecordingButton(
                    uiState = uiState,
                    permissionState = permissionState,
                    onToggleRecording = { recordingController.toggleRecording() },
                    onRequestPermission = { permissionHandler.requestAudioPermission(permissionHandler.context as MainActivity) }
                )
                
                // Status message
                Text(
                    text = when {
                        !permissionState.hasMicrophonePermission -> permissionHandler.getPermissionStatusMessage()
                        uiState.hasError -> uiState.statusMessage
                        uiState.isStarting -> "Starting..."
                        uiState.isStopping -> "Stopping..."
                        uiState.isPaused -> "Paused - Tap to resume"
                        uiState.isRecording -> "Recording..."
                        else -> uiState.statusMessage
                    },
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = if (uiState.hasError) Color.Red else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                )
                
                // Health metrics (when recording)
                uiState.healthMetrics?.let { metrics ->
                    HealthMonitorDisplay(
                        metrics = metrics,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                
                // Permission rationale dialog
                if (permissionState.showingRationale) {
                    AlertDialog(
                        onDismissRequest = { permissionHandler.cancelPermissionRequest() },
                        title = {
                            Text("Microphone Access")
                        },
                        text = {
                            Text(permissionState.rationaleMessage)
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { 
                                    permissionHandler.proceedWithPermissionRequest(
                                        permissionHandler.context as MainActivity
                                    )
                                }
                            ) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { permissionHandler.cancelPermissionRequest() }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                
                // Settings button for permanent denial
                if (permissionState.permissionDeniedPermanently) {
                    TextButton(
                        onClick = { 
                            permissionHandler.openAppSettings(
                                permissionHandler.context as MainActivity
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Open Settings")
                    }
                }
            }
        }
    }
    
    // Clear error/permission flags after showing
    LaunchedEffect(uiState.hasError) {
        if (uiState.hasError) {
            delay(3000)
            recordingController.clearError()
        }
    }
    
    LaunchedEffect(permissionState.permissionJustGranted || permissionState.permissionJustDenied) {
        delay(2000)
        permissionHandler.clearPermissionFlags()
    }
}

@Composable
fun RecordingButton(
    uiState: RecordingUiState,
    permissionState: PermissionState,
    onToggleRecording: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val isEnabled = !uiState.isStarting && !uiState.isStopping
    val buttonColor = when {
        !permissionState.hasMicrophonePermission -> MaterialTheme.colorScheme.secondary
        uiState.isRecording -> Color.Red
        else -> MaterialTheme.colorScheme.primary
    }
    
    FloatingActionButton(
        onClick = {
            if (!permissionState.hasMicrophonePermission) {
                onRequestPermission()
            } else {
                onToggleRecording()
            }
        },
        modifier = Modifier
            .size(80.dp)
            .scale(if (uiState.isRecording) 1.1f else 1.0f),
        shape = CircleShape,
        containerColor = buttonColor,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = when {
                !permissionState.hasMicrophonePermission -> Icons.Default.Mic
                uiState.isRecording -> Icons.Default.Stop
                else -> Icons.Default.Mic
            },
            contentDescription = when {
                !permissionState.hasMicrophonePermission -> "Request microphone permission"
                uiState.isRecording -> "Stop recording"
                else -> "Start recording"
            },
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun HealthMonitorDisplay(
    metrics: HealthMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (metrics.isHealthy) 
                Color.Green.copy(alpha = 0.1f) 
            else 
                Color.Red.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Health: ${if (metrics.isHealthy) "Good" else "Warning"}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (metrics.isHealthy) Color.Green else Color.Red
            )
            
            Text(
                text = "Recorded: ${formatBytes(metrics.bytesRecorded)}",
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (metrics.bufferOverruns > 0) {
                Text(
                    text = "Buffer overruns: ${metrics.bufferOverruns}",
                    fontSize = 8.sp,
                    color = Color.Orange
                )
            }
        }
    }
}

@Composable
fun ProjectEchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF1976D2),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        ),
        content = content
    )
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
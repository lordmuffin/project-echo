package com.projectecho

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat

/**
 * Main Activity for Project Echo Phone audio recorder.
 * Simple launcher activity that shows the app is working.
 */
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private var hasPermission by mutableStateOf(false)
    private var isRecording by mutableStateOf(false)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        Log.d(TAG, "Permission granted: $isGranted")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "MainActivity created successfully")
        
        // Check initial permission status
        hasPermission = ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        setContent {
            ProjectEchoTheme {
                RecordingScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RecordingScreen() {
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
                    modifier = Modifier.padding(32.dp)
                ) {
                    // App title
                    Text(
                        text = "Project Echo",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Recording status
                    if (isRecording) {
                        Text(
                            text = "Recording...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                    
                    // Main recording button
                    FloatingActionButton(
                        onClick = {
                            if (!hasPermission) {
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                isRecording = !isRecording
                                Log.d(TAG, "Recording toggled: $isRecording")
                            }
                        },
                        modifier = Modifier
                            .size(120.dp)
                            .scale(if (isRecording) 1.1f else 1.0f),
                        shape = CircleShape,
                        containerColor = when {
                            !hasPermission -> MaterialTheme.colorScheme.secondary
                            isRecording -> Color.Red
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = when {
                                !hasPermission -> Icons.Default.Mic
                                isRecording -> Icons.Default.Stop
                                else -> Icons.Default.Mic
                            },
                            contentDescription = when {
                                !hasPermission -> "Request microphone permission"
                                isRecording -> "Stop recording"
                                else -> "Start recording"
                            },
                            modifier = Modifier.size(60.dp)
                        )
                    }
                    
                    // Status message
                    Text(
                        text = when {
                            !hasPermission -> "Tap to request microphone permission"
                            isRecording -> "Recording in progress..."
                            else -> "Ready to record"
                        },
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = if (!hasPermission) Color.Gray else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .fillMaxWidth()
                    )
                    
                    // Success message
                    Text(
                        text = "✅ App launched successfully!",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Green,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectEchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),
            secondary = Color(0xFF03DAC6),
            background = Color(0xFFFAFAFA),
            surface = Color.White
        ),
        content = content
    )
}
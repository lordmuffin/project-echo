# Wearable Data Layer Communication Architecture

## Overview

This document outlines the comprehensive multi-channel communication system between the watch and phone using Google's Wearable Data Layer API. The implementation fulfills the three core user stories:

1. **Instant Sync After Recording** - Recordings automatically sync when finished
2. **Live Recording Commands** - Phone can control watch recording remotely  
3. **Metadata Sync Across Devices** - Titles, tags, and descriptions stay consistent

## Architecture Components

### Core API Clients (`core/wearable/client/`)

#### WearableDataClient
- **Purpose**: Persistent data synchronization using Google's DataClient
- **Features**: 
  - Recording metadata sync with automatic conflict resolution
  - Device preferences synchronization
  - Real-time data change observation
  - Offline-first design with queue management

#### WearableMessageClient  
- **Purpose**: Real-time messaging using Google's MessageClient
- **Features**:
  - Live recording control commands (start/stop/pause/resume)
  - Recording status broadcasts
  - Metadata update notifications
  - Bi-directional message observation

#### WearableChannelClient
- **Purpose**: Large file streaming using Google's ChannelClient
- **Features**:
  - Audio data streaming with progress tracking
  - Automatic retry and error recovery
  - Transfer rate monitoring
  - Connection lifecycle management

### High-Level Services (`core/wearable/service/`)

#### WearableSyncService
- **Purpose**: Orchestrates all synchronization operations
- **Key Methods**:
  - `syncRecordingMetadata()` - Syncs recording info immediately after completion
  - `updateRecordingTitle/Tags/Description()` - Real-time metadata sync
  - `syncRecordingAudioData()` - Streams audio files via channels
  - `observeRecordingUpdates()` - Monitors remote changes

#### RemoteRecordingController
- **Purpose**: Phone-to-watch recording control
- **Key Methods**:
  - `startRecordingOnDevice()` - Initiate recording on watch from phone
  - `stopRecordingOnAllDevices()` - Emergency stop across all devices
  - `observeRemoteRecordingStatus()` - Monitor recording status across devices
  - `getRecordingCapableDevices()` - Discover available recording devices

#### OfflineSyncManager
- **Purpose**: Handles offline scenarios and retry logic
- **Features**:
  - Automatic queue management for failed operations
  - Exponential backoff retry policy
  - Network status monitoring with auto-resume
  - Persistent operation storage

#### RecordingCompletionListener
- **Purpose**: Automatic sync triggers
- **Features**:
  - Monitors recording completion events
  - Triggers immediate metadata sync
  - Queues audio data for background transfer
  - Handles remote recording notifications

## Data Models (`core/wearable/model/`)

### WearableMessage Types
- **RecordingControlMessage**: Start/stop/pause/resume commands
- **RecordingStatusMessage**: Recording state updates and errors
- **MetadataSyncMessage**: Title/tag/description updates
- **AudioDataSyncRequest**: Audio transfer coordination
- **DeviceConnectionStatus**: Device availability and capabilities

### Data Synchronization Models  
- **RecordingMetadata**: Complete recording information with sync status
- **DevicePreferences**: User settings and sync configuration
- **SyncConfiguration**: Transfer parameters and retry policies
- **SyncError**: Detailed error information with retry capabilities

## User Story Implementation

### 1. Instant Sync After Recording

```kotlin
// Automatic trigger when recording completes
class RecordingCompletionListener {
    private suspend fun triggerAutoSync(recording: AudioRecording) {
        // Immediate metadata sync (fast)
        wearableSyncService.syncRecordingMetadata(recording.id)
        
        // Delayed audio sync (background)
        delay(2000)
        wearableSyncService.syncRecordingAudioData(recording.id)
    }
}
```

**Flow:**
1. Recording stops on watch
2. RecordingCompletionListener detects completion
3. Metadata syncs immediately via DataClient (~100ms)
4. Audio data queued for background transfer via ChannelClient
5. Phone receives notification and displays recording

### 2. Live Recording Commands

```kotlin
// Phone controls watch recording
remoteController.startRecordingOnDevice(watchNodeId, "Interview #3")
remoteController.pauseRecordingOnDevice(watchNodeId, recordingId) 
remoteController.stopRecordingOnAllDevices()
```

**Flow:**
1. Phone app sends RecordingControlMessage via MessageClient
2. Watch receives message and starts/stops recording
3. Watch responds with RecordingStatusMessage
4. Phone updates UI with recording status
5. Real-time status sync across all devices

### 3. Metadata Sync Across Devices

```kotlin
// Update title on phone, auto-syncs to watch
wearableSyncService.updateRecordingTitle(recordingId, "Updated Title")

// Watch observes changes and updates local storage
wearableDataClient.observeRecordingMetadataChanges()
    .collect { metadata -> updateLocalRecording(metadata) }
```

**Flow:**
1. User updates title/tags on any device
2. Change sent via MessageClient for immediate notification
3. DataClient updated for persistent storage
4. All connected devices receive update
5. Local storage synchronized automatically

## Error Handling & Offline Support

### Network Resilience
- **Automatic Retry**: Failed operations retry with exponential backoff
- **Offline Queue**: Operations queued when network unavailable
- **Connection Monitoring**: Auto-resume when connection restored
- **Partial Sync**: Metadata syncs first, audio follows when bandwidth available

### Error Classification
- **Network Errors**: Retry with backoff
- **Device Disconnected**: Queue until reconnection
- **Storage Full**: User notification, no retry
- **Timeout**: Immediate retry with shorter timeout

### Recovery Strategies
```kotlin
class OfflineSyncManager {
    // Smart retry with different strategies per error type
    private fun shouldRetry(errorType: SyncErrorType): Boolean {
        return when (errorType) {
            SyncErrorType.NETWORK_ERROR -> retryPolicy.retryOnNetworkError
            SyncErrorType.TIMEOUT_ERROR -> retryPolicy.retryOnTimeout  
            SyncErrorType.DEVICE_DISCONNECTED -> true
            SyncErrorType.STORAGE_FULL -> false
            else -> false
        }
    }
}
```

## Performance Optimizations

### Data Transfer Efficiency
- **Chunked Transfer**: Large audio files sent in 1MB chunks
- **Compression**: Audio data compressed before transfer
- **Priority Queuing**: Metadata syncs prioritized over audio
- **Bandwidth Awareness**: Adjusts transfer rate based on connection quality

### Battery Optimization  
- **Batched Operations**: Multiple updates sent together
- **Background Scheduling**: Audio transfers during charging/WiFi
- **Connection Pooling**: Reuse existing connections
- **Smart Scheduling**: Sync during natural usage patterns

### Memory Management
- **Streaming Architecture**: Audio never fully loaded into memory
- **Weak References**: Prevent memory leaks in observers
- **Connection Cleanup**: Automatic resource management
- **Buffer Management**: Configurable buffer sizes

## Testing Strategy

### Unit Tests (`core/wearable/src/test/`)
- **WearableDataSyncTest**: Data layer synchronization
- **RemoteRecordingControlTest**: Cross-device recording control
- **Mock Integration**: Comprehensive mocking of Wearable APIs
- **Flow Testing**: Async operations and real-time updates

### Integration Testing
- **Multi-Device Scenarios**: Phone + watch combinations
- **Network Simulation**: Connection drops, slow networks
- **Error Injection**: Deliberate failures to test recovery
- **Performance Testing**: Large file transfers, many recordings

## Security Considerations

### Data Protection
- **Encryption**: Audio data encrypted during transfer
- **Authentication**: Device pairing verification
- **Permission Validation**: Runtime permission checks
- **Secure Storage**: Encrypted local storage for sensitive data

### Privacy Features
- **Local Processing**: No cloud dependencies required
- **User Control**: Granular sync preferences
- **Data Retention**: Configurable automatic cleanup
- **Audit Trail**: Comprehensive logging for debugging

## Configuration

### Sync Settings
```kotlin
data class SyncConfiguration(
    val batchSize: Int = 1024 * 1024, // 1MB chunks
    val maxConcurrentTransfers: Int = 3,
    val timeoutMs: Long = 30000L,
    val retryDelayMs: Long = 5000L,
    val compressionEnabled: Boolean = true,
    val encryptionEnabled: Boolean = true
)
```

### Device Preferences  
```kotlin
data class DevicePreferences(
    val autoSyncEnabled: Boolean = true,
    val syncOnWifiOnly: Boolean = false,
    val maxFileSize: Long = 100 * 1024 * 1024L, // 100MB
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val retryAttempts: Int = 3
)
```

## Future Enhancements

### Advanced Features
- **Multi-Watch Support**: Handle multiple watches per phone
- **Cloud Backup**: Optional cloud sync for device switching
- **Smart Transcription**: Auto-generate captions during sync
- **Collaborative Recording**: Multiple devices recording simultaneously

### Performance Improvements  
- **Delta Sync**: Only transfer changed audio segments
- **Predictive Caching**: Pre-load likely-needed recordings
- **ML Optimization**: Learn user patterns for better scheduling
- **Adaptive Quality**: Dynamic quality based on usage context

## Dependencies

### Gradle Configuration
```kotlin
// Version catalog entry
wearable-data-layer = "18.0.0"

// Module dependencies  
implementation("com.google.android.gms:play-services-wearable:18.0.0")
implementation(project(":core:wearable"))
```

### Required Permissions
```xml
<!-- Phone & Watch -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Watch only -->
<uses-permission android:name="android.permission.BODY_SENSORS" />
```

This architecture provides a robust, scalable foundation for seamless wearable data synchronization that enhances the user experience across all connected devices while maintaining performance and reliability.
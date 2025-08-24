package com.projectecho.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Manages audio focus for recording sessions with graceful handling of interruptions.
 * Ensures proper behavior when other apps request audio focus during recording.
 * 
 * Features:
 * - Proper audio focus request/abandon cycle
 * - Handles temporary and permanent focus loss
 * - Compatible with Android 8.0+ AudioFocusRequest API
 * - Graceful degradation for older Android versions
 */
class AudioFocusManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioFocusManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    // Callbacks for focus changes
    private var onFocusGained: (() -> Unit)? = null
    private var onFocusLost: (() -> Unit)? = null
    private var onFocusLostTransient: (() -> Unit)? = null
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleAudioFocusChange(focusChange)
    }
    
    /**
     * Request audio focus for recording
     */
    fun requestAudioFocus(): Boolean {
        return try {
            if (hasAudioFocus) {
                Log.d(TAG, "Audio focus already held")
                return true
            }
            
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestAudioFocusAPI26()
            } else {
                requestAudioFocusLegacy()
            }
            
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            
            if (hasAudioFocus) {
                Log.d(TAG, "Audio focus granted")
            } else {
                Log.w(TAG, "Audio focus denied, result: $result")
            }
            
            hasAudioFocus
            
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
            false
        }
    }
    
    /**
     * Abandon audio focus
     */
    fun abandonAudioFocus() {
        try {
            if (!hasAudioFocus) {
                Log.d(TAG, "Audio focus not held, nothing to abandon")
                return
            }
            
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                abandonAudioFocusAPI26()
            } else {
                abandonAudioFocusLegacy()
            }
            
            hasAudioFocus = false
            
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio focus abandoned successfully")
            } else {
                Log.w(TAG, "Error abandoning audio focus, result: $result")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
    }
    
    /**
     * Request audio focus using Android 8.0+ API
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocusAPI26(): Int {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        
        return audioManager.requestAudioFocus(audioFocusRequest!!)
    }
    
    /**
     * Request audio focus using legacy API
     */
    @Suppress("DEPRECATION")
    private fun requestAudioFocusLegacy(): Int {
        return audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
    }
    
    /**
     * Abandon audio focus using Android 8.0+ API
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun abandonAudioFocusAPI26(): Int {
        return audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
        } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }
    
    /**
     * Abandon audio focus using legacy API
     */
    @Suppress("DEPRECATION")
    private fun abandonAudioFocusLegacy(): Int {
        return audioManager.abandonAudioFocus(audioFocusChangeListener)
    }
    
    /**
     * Handle audio focus changes
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasAudioFocus = true
                onFocusGained?.invoke()
            }
            
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                hasAudioFocus = false
                onFocusLost?.invoke()
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily")
                hasAudioFocus = false
                onFocusLostTransient?.invoke()
            }
            
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost, can duck")
                // For recording, we might want to pause rather than duck
                onFocusLostTransient?.invoke()
            }
            
            else -> {
                Log.w(TAG, "Unknown audio focus change: $focusChange")
            }
        }
    }
    
    /**
     * Check if we currently have audio focus
     */
    fun hasAudioFocus(): Boolean = hasAudioFocus
    
    /**
     * Set callback for when audio focus is gained
     */
    fun setOnFocusGained(callback: () -> Unit) {
        onFocusGained = callback
    }
    
    /**
     * Set callback for when audio focus is permanently lost
     */
    fun setOnFocusLost(callback: () -> Unit) {
        onFocusLost = callback
    }
    
    /**
     * Set callback for when audio focus is temporarily lost
     */
    fun setOnFocusLostTransient(callback: () -> Unit) {
        onFocusLostTransient = callback
    }
}
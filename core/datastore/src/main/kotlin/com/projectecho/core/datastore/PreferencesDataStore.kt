package com.projectecho.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.projectecho.core.datastore.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Extension property to create DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * DataStore implementation for managing user preferences.
 * Provides type-safe storage using Kotlin Serialization for complex data structures.
 */
@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    companion object {
        // Preference keys
        private val USER_PREFERENCES_KEY = stringPreferencesKey("user_preferences")
        private val FIRST_LAUNCH_KEY = booleanPreferencesKey("first_launch")
        private val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
        private val TOTAL_RECORDINGS_KEY = intPreferencesKey("total_recordings")
        private val TOTAL_RECORDING_TIME_KEY = longPreferencesKey("total_recording_time")
    }

    /**
     * Flow of user preferences with error handling and default values.
     */
    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val preferencesJson = preferences[USER_PREFERENCES_KEY]
            if (preferencesJson != null) {
                try {
                    json.decodeFromString<UserPreferences>(preferencesJson)
                } catch (e: Exception) {
                    UserPreferences() // Return default preferences if parsing fails
                }
            } else {
                UserPreferences()
            }
        }

    /**
     * Update user preferences.
     */
    suspend fun updateUserPreferences(preferences: UserPreferences) {
        dataStore.edit { prefs ->
            prefs[USER_PREFERENCES_KEY] = json.encodeToString(preferences)
        }
    }

    /**
     * Update recording settings only.
     */
    suspend fun updateRecordingSettings(recordingSettings: RecordingSettings) {
        dataStore.edit { prefs ->
            val currentPrefsJson = prefs[USER_PREFERENCES_KEY]
            val currentPrefs = if (currentPrefsJson != null) {
                try {
                    json.decodeFromString<UserPreferences>(currentPrefsJson)
                } catch (e: Exception) {
                    UserPreferences()
                }
            } else {
                UserPreferences()
            }
            
            val updatedPrefs = currentPrefs.copy(recordingSettings = recordingSettings)
            prefs[USER_PREFERENCES_KEY] = json.encodeToString(updatedPrefs)
        }
    }

    /**
     * Update UI settings only.
     */
    suspend fun updateUiSettings(uiSettings: UiSettings) {
        dataStore.edit { prefs ->
            val currentPrefsJson = prefs[USER_PREFERENCES_KEY]
            val currentPrefs = if (currentPrefsJson != null) {
                try {
                    json.decodeFromString<UserPreferences>(currentPrefsJson)
                } catch (e: Exception) {
                    UserPreferences()
                }
            } else {
                UserPreferences()
            }
            
            val updatedPrefs = currentPrefs.copy(uiSettings = uiSettings)
            prefs[USER_PREFERENCES_KEY] = json.encodeToString(updatedPrefs)
        }
    }

    /**
     * Update sync settings only.
     */
    suspend fun updateSyncSettings(syncSettings: SyncSettings) {
        dataStore.edit { prefs ->
            val currentPrefsJson = prefs[USER_PREFERENCES_KEY]
            val currentPrefs = if (currentPrefsJson != null) {
                try {
                    json.decodeFromString<UserPreferences>(currentPrefsJson)
                } catch (e: Exception) {
                    UserPreferences()
                }
            } else {
                UserPreferences()
            }
            
            val updatedPrefs = currentPrefs.copy(syncSettings = syncSettings)
            prefs[USER_PREFERENCES_KEY] = json.encodeToString(updatedPrefs)
        }
    }

    /**
     * Check if this is the first app launch.
     */
    val isFirstLaunch: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[FIRST_LAUNCH_KEY] ?: true
        }

    /**
     * Mark first launch as completed.
     */
    suspend fun setFirstLaunchCompleted() {
        dataStore.edit { preferences ->
            preferences[FIRST_LAUNCH_KEY] = false
        }
    }

    /**
     * Get last sync timestamp.
     */
    val lastSyncTime: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[LAST_SYNC_TIME_KEY] ?: 0L
        }

    /**
     * Update last sync timestamp.
     */
    suspend fun updateLastSyncTime(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = timestamp
        }
    }

    /**
     * Get total number of recordings made.
     */
    val totalRecordings: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[TOTAL_RECORDINGS_KEY] ?: 0
        }

    /**
     * Increment total recording count.
     */
    suspend fun incrementTotalRecordings() {
        dataStore.edit { preferences ->
            val current = preferences[TOTAL_RECORDINGS_KEY] ?: 0
            preferences[TOTAL_RECORDINGS_KEY] = current + 1
        }
    }

    /**
     * Get total recording time in milliseconds.
     */
    val totalRecordingTime: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[TOTAL_RECORDING_TIME_KEY] ?: 0L
        }

    /**
     * Add to total recording time.
     */
    suspend fun addToTotalRecordingTime(durationMs: Long) {
        dataStore.edit { preferences ->
            val current = preferences[TOTAL_RECORDING_TIME_KEY] ?: 0L
            preferences[TOTAL_RECORDING_TIME_KEY] = current + durationMs
        }
    }

    /**
     * Clear all preferences (for testing or reset functionality).
     */
    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
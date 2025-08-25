package com.projectecho.core.ui.navigation

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Type-safe navigation arguments system for Jetpack Navigation.
 * Provides compile-time safety for navigation parameters and route generation.
 */
sealed class NavigationArgs {

    /**
     * Convert arguments to navigation route string with type safety.
     */
    abstract fun toRoute(): String

    /**
     * Arguments for recording screen navigation.
     */
    @Serializable
    data class RecordingArgs(
        val recordingId: String? = null,
        val autoStart: Boolean = false
    ) : NavigationArgs() {
        override fun toRoute(): String {
            return if (recordingId != null || autoStart) {
                val params = mutableListOf<String>()
                recordingId?.let { params.add("recordingId=$it") }
                if (autoStart) params.add("autoStart=$autoStart")
                "${NavigationRoutes.RECORDING_SCREEN}?${params.joinToString("&")}"
            } else {
                NavigationRoutes.RECORDING_SCREEN
            }
        }

        companion object {
            fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): RecordingArgs {
                return RecordingArgs(
                    recordingId = savedStateHandle.get<String>("recordingId"),
                    autoStart = savedStateHandle.get<Boolean>("autoStart") ?: false
                )
            }
        }
    }

    /**
     * Arguments for playback screen navigation.
     */
    @Serializable
    data class PlaybackArgs(
        val recordingId: String,
        val autoPlay: Boolean = false,
        val startPosition: Long = 0L
    ) : NavigationArgs() {
        override fun toRoute(): String {
            val params = mutableListOf("recordingId=$recordingId")
            if (autoPlay) params.add("autoPlay=$autoPlay")
            if (startPosition > 0) params.add("startPosition=$startPosition")
            return "${NavigationRoutes.RECORDING_PLAYBACK}?${params.joinToString("&")}"
        }

        companion object {
            fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): PlaybackArgs? {
                val recordingId = savedStateHandle.get<String>("recordingId")
                return if (recordingId != null) {
                    PlaybackArgs(
                        recordingId = recordingId,
                        autoPlay = savedStateHandle.get<Boolean>("autoPlay") ?: false,
                        startPosition = savedStateHandle.get<Long>("startPosition") ?: 0L
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Arguments for recordings list screen with filtering options.
     */
    @Serializable
    data class RecordingsListArgs(
        val sortBy: SortOption = SortOption.DATE_DESC,
        val filterBy: FilterOption = FilterOption.ALL,
        val searchQuery: String? = null
    ) : NavigationArgs() {
        override fun toRoute(): String {
            val params = mutableListOf<String>()
            if (sortBy != SortOption.DATE_DESC) params.add("sortBy=${sortBy.name}")
            if (filterBy != FilterOption.ALL) params.add("filterBy=${filterBy.name}")
            searchQuery?.let { if (it.isNotBlank()) params.add("searchQuery=$it") }
            
            return if (params.isNotEmpty()) {
                "${NavigationRoutes.RECORDINGS_LIST}?${params.joinToString("&")}"
            } else {
                NavigationRoutes.RECORDINGS_LIST
            }
        }

        companion object {
            fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): RecordingsListArgs {
                return RecordingsListArgs(
                    sortBy = savedStateHandle.get<String>("sortBy")?.let { 
                        SortOption.valueOf(it) 
                    } ?: SortOption.DATE_DESC,
                    filterBy = savedStateHandle.get<String>("filterBy")?.let { 
                        FilterOption.valueOf(it) 
                    } ?: FilterOption.ALL,
                    searchQuery = savedStateHandle.get<String>("searchQuery")
                )
            }
        }
    }

    /**
     * Arguments for settings screen with specific section.
     */
    @Serializable
    data class SettingsArgs(
        val section: SettingsSection = SettingsSection.GENERAL
    ) : NavigationArgs() {
        override fun toRoute(): String {
            return if (section != SettingsSection.GENERAL) {
                "${NavigationRoutes.SETTINGS}?section=${section.name}"
            } else {
                NavigationRoutes.SETTINGS
            }
        }

        companion object {
            fun fromSavedStateHandle(savedStateHandle: SavedStateHandle): SettingsArgs {
                return SettingsArgs(
                    section = savedStateHandle.get<String>("section")?.let { 
                        SettingsSection.valueOf(it) 
                    } ?: SettingsSection.GENERAL
                )
            }
        }
    }
}

/**
 * Enumeration for sort options in recordings list.
 */
@Serializable
enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    TITLE_ASC,
    TITLE_DESC,
    DURATION_ASC,
    DURATION_DESC,
    SIZE_ASC,
    SIZE_DESC
}

/**
 * Enumeration for filter options in recordings list.
 */
@Serializable
enum class FilterOption {
    ALL,
    THIS_WEEK,
    THIS_MONTH,
    WAV_FORMAT,
    MP3_FORMAT,
    LARGE_FILES,
    SHORT_RECORDINGS,
    LONG_RECORDINGS
}

/**
 * Enumeration for settings sections.
 */
@Serializable
enum class SettingsSection {
    GENERAL,
    RECORDING,
    PLAYBACK,
    SYNC,
    PRIVACY,
    ABOUT
}

/**
 * Extension functions for type-safe navigation with NavController.
 */
fun NavController.navigateWithArgs(args: NavigationArgs) {
    navigate(args.toRoute())
}

/**
 * Extension function for type-safe route building.
 */
inline fun <reified T : NavigationArgs> buildRoute(args: T): String {
    return args.toRoute()
}

/**
 * Type-safe navigation argument parsing from Bundle.
 */
object NavigationArgsParser {
    internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * Serialize navigation arguments to string for complex objects.
     */
    internal inline fun <reified T : NavigationArgs> serialize(args: T): String {
        return json.encodeToString(args)
    }

    /**
     * Deserialize navigation arguments from string for complex objects.
     */
    internal inline fun <reified T : NavigationArgs> deserialize(serialized: String): T {
        return json.decodeFromString(serialized)
    }

    /**
     * Safe argument retrieval with defaults.
     */
    fun Bundle.getStringArgOrDefault(key: String, default: String = ""): String {
        return getString(key) ?: default
    }

    fun Bundle.getBooleanArgOrDefault(key: String, default: Boolean = false): Boolean {
        return getBoolean(key, default)
    }

    fun Bundle.getLongArgOrDefault(key: String, default: Long = 0L): Long {
        return getLong(key, default)
    }

    fun Bundle.getIntArgOrDefault(key: String, default: Int = 0): Int {
        return getInt(key, default)
    }
}
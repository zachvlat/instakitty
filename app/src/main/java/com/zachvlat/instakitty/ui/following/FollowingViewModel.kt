package com.zachvlat.instakitty.ui.following

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ------------------------------------------------------------
// UI state that the composables read from
// ------------------------------------------------------------
data class FollowingUiState(
    val usernames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val avatars: Map<String, String> = emptyMap()
)

// ------------------------------------------------------------
// ViewModel
// ------------------------------------------------------------
class FollowingViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)

    /* ---------- State flows ---------- */
    private val _state = MutableStateFlow(FollowingUiState())
    val state: StateFlow<FollowingUiState> = _state.asStateFlow()

    private val _removingUsers = MutableStateFlow<Set<String>>(emptySet())
    val removingUsers: StateFlow<Set<String>> = _removingUsers.asStateFlow()

    /* ---------- Init: collect from DataStore ---------- */
    init {
        // Avatars (only keep local files)
        viewModelScope.launch {
            dataStore.profilePics.collect { pics ->
                _state.value = _state.value.copy(
                    avatars = pics.filterValues { it.startsWith("/") }
                )
            }
        }

        // Followed usernames
        viewModelScope.launch {
            dataStore.followedUsers.collect { users ->
                val sorted = users.toList().sorted()
                // Keep only those that are still in the list
                _removingUsers.value = _removingUsers.value.intersect(sorted.toSet())
                _state.value = _state.value.copy(
                    usernames = sorted,
                    isLoading = false
                )
            }
        }
    }

    /* ---------- Remove a user ---------- */
    fun removeUser(username: String) {
        // Optimistically show the user as “removing”
        _removingUsers.value += username

        viewModelScope.launch {
            try {
                dataStore.toggleFollow(username)   // toggles unfollow
                // Success – remove flag
                _removingUsers.value -= username
            } catch (e: Exception) {
                // Failure – rollback UI flag
                _removingUsers.value -= username
            }
        }
    }

    /* ---------- Export to JSON ---------- */
    fun exportJson(): String =
        Json.encodeToString(_state.value.usernames)

    /* ---------- Import from JSON ---------- */
    fun importJson(json: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val usernames = Json.decodeFromString<List<String>>(json)
                if (usernames.isEmpty()) {
                    onResult(false, "No usernames found")
                    return@launch
                }
                dataStore.addFollowedUsers(usernames)
                onResult(true, "Imported ${usernames.size} profile(s)")
            } catch (e: Exception) {
                onResult(false, "Invalid JSON: ${e.message}")
            }
        }
    }
}
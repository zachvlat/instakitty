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

data class FollowingUiState(
    val usernames: List<String> = emptyList(),
    val isLoading: Boolean = true
)

class FollowingViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)

    private val _state = MutableStateFlow(FollowingUiState())
    val state: StateFlow<FollowingUiState> = _state.asStateFlow()

    private val _removingUsers = MutableStateFlow<Set<String>>(emptySet())
    val removingUsers: StateFlow<Set<String>> = _removingUsers.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.followedUsers.collect { users ->
                val sorted = users.toList().sorted()
                _removingUsers.value = _removingUsers.value.intersect(sorted.toSet())
                _state.value = _state.value.copy(
                    usernames = sorted,
                    isLoading = false
                )
            }
        }
    }

    fun removeUser(username: String) {
        _removingUsers.value = _removingUsers.value + username
        viewModelScope.launch {
            try {
                dataStore.toggleFollow(username)
            } catch (_: Exception) {
                _removingUsers.value = _removingUsers.value - username
            }
        }
    }

    fun exportJson(): String {
        return Json.encodeToString(_state.value.usernames)
    }

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

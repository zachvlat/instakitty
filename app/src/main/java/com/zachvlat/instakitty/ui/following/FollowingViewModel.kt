package com.zachvlat.instakitty.ui.following

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.KittygramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class FollowingUiState(
    val usernames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val profilePics: Map<String, String> = emptyMap()
)

class FollowingViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(FollowingUiState())
    val state: StateFlow<FollowingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.followedUsers.collect { users ->
                val sorted = users.toList().sorted()
                val cachedPics = dataStore.getProfilePicsSnapshot()
                _state.value = _state.value.copy(
                    usernames = sorted,
                    profilePics = sorted.mapNotNull { cachedPics[it]?.let { url -> it to url } }.toMap(),
                    isLoading = false
                )
                sorted.forEach { loadProfilePic(it) }
            }
        }
    }

    private suspend fun loadProfilePic(username: String) {
        val result = repository.getUser(username)
        if (result is ApiResult.Success) {
            val url = result.data.userInfo?.profilePicture
                ?: result.data.userInfo?.profilePicUrl
            if (url != null) {
                _state.value = _state.value.copy(
                    profilePics = _state.value.profilePics + (username to url)
                )
                dataStore.updateProfilePic(username, url)
            }
        }
    }

    fun removeUser(username: String) {
        viewModelScope.launch {
            dataStore.toggleFollow(username)
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

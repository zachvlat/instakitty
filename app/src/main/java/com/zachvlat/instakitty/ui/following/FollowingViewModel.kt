package com.zachvlat.instakitty.ui.following

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.KittygramRepository
import kotlinx.coroutines.delay
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

    private val _removingUsers = MutableStateFlow<Set<String>>(emptySet())
    val removingUsers: StateFlow<Set<String>> = _removingUsers.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.followedUsers.collect { users ->
                val sorted = users.toList().sorted()
                val cachedPics = dataStore.getProfilePicsSnapshot()
                _removingUsers.value = _removingUsers.value.intersect(sorted.toSet())
                _state.value = _state.value.copy(
                    usernames = sorted,
                    profilePics = sorted.mapNotNull { cachedPics[it]?.let { url -> it to url } }.toMap(),
                    isLoading = false
                )
                sorted.forEach { user ->
                    if (cachedPics[user] == null) {
                        loadProfilePic(user)
                    }
                    delay(500L)
                }
            }
        }
    }

    private suspend fun loadProfilePic(username: String) {
        var attempts = 0
        while (attempts < 3) {
            val result = repository.getUser(username)
            when {
                result is ApiResult.Success -> {
                    val url = result.data.userInfo?.profilePicture
                        ?: result.data.userInfo?.profilePicUrl
                    if (url != null) {
                        _state.value = _state.value.copy(
                            profilePics = _state.value.profilePics + (username to url)
                        )
                        dataStore.updateProfilePic(username, url)
                    }
                    return
                }
                result is ApiResult.Error && isRateLimitError(result) -> {
                    attempts++
                    if (attempts < 3) {
                        delay(1000L * (1 shl (attempts - 1)))
                    }
                }
                else -> return
            }
        }
    }

    private fun isRateLimitError(error: ApiResult.Error): Boolean {
        return error.type == "ratelimited" || error.type == "http_502"
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

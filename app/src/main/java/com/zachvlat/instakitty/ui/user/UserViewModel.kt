package com.zachvlat.instakitty.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.Post
import com.zachvlat.instakitty.data.remote.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class UserUiState(
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val endCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isFollowing: Boolean = false
)

class UserViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(UserUiState())
    val state: StateFlow<UserUiState> = _state.asStateFlow()

    private var currentUsername: String = ""

    fun loadUser(username: String) {
        currentUsername = username
        _state.value = UserUiState(isLoading = true)
        viewModelScope.launch {
            val followed = dataStore.followedUsers.first()
            when (val result = repository.getUser(username)) {
                is ApiResult.Success -> {
                    _state.value = UserUiState(
                        user = result.data.userInfo,
                        posts = result.data.posts ?: emptyList(),
                        isLoading = false,
                        endCursor = result.data.endCursor,
                        isFollowing = username in followed
                    )
                }
                is ApiResult.Error -> {
                    _state.value = UserUiState(isLoading = false, error = result.message)
                }
                is ApiResult.NetworkError -> {
                    _state.value = UserUiState(isLoading = false, error = result.message)
                }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.endCursor ?: return
        if (cursor.isBlank() || _state.value.isLoadingMore) return
        _state.value = _state.value.copy(isLoadingMore = true)
        viewModelScope.launch {
            when (val result = repository.getUser(currentUsername, cursor)) {
                is ApiResult.Success -> {
                    val current = _state.value
                    _state.value = current.copy(
                        posts = current.posts + (result.data.posts ?: emptyList()),
                        isLoadingMore = false,
                        endCursor = result.data.endCursor
                    )
                }
                else -> {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
            }
        }
    }

    fun toggleFollow() {
        viewModelScope.launch {
            dataStore.toggleFollow(currentUsername)
            val followed = dataStore.followedUsers.first()
            _state.value = _state.value.copy(isFollowing = currentUsername in followed)
        }
    }
}

package com.zachvlat.instakitty.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.AvatarCache
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
                        user = result.data.info,
                        posts = result.data.grid?.items ?: emptyList(),
                        isLoading = false,
                        endCursor = result.data.grid?.cursor,
                        isFollowing = username in followed
                    )
                    if (username in followed) cacheAvatar(username, result.data.info)
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
                    val newPosts = (result.data.grid?.items ?: emptyList())
                        .filterNot { newPost ->
                            current.posts.any { it.shortcode != null && it.shortcode == newPost.shortcode }
                        }
                    val nextEndCursor = if (newPosts.isEmpty()) null else result.data.grid?.cursor
                    _state.value = current.copy(
                        posts = current.posts + newPosts,
                        isLoadingMore = false,
                        endCursor = nextEndCursor
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
            val isFollowing = currentUsername in followed
            _state.value = _state.value.copy(isFollowing = isFollowing)
            if (isFollowing) cacheAvatar(currentUsername, _state.value.user, force = true)
        }
    }

    // Followed profiles keep a local copy of their picture for the Following page. It is
    // refreshed at most every few days, piggybacking on profile visits instead of fetching
    // every followed account up front.
    private fun cacheAvatar(username: String, user: User?, force: Boolean = false) {
        val url = user?.profilePicture ?: user?.profilePicUrl ?: return
        val context = getApplication<Application>()
        val file = AvatarCache.fileFor(context, username)
        if (!force && file.exists() &&
            System.currentTimeMillis() - file.lastModified() < AVATAR_MAX_AGE_MILLIS
        ) return
        viewModelScope.launch {
            val path = AvatarCache.save(context, username, url) ?: return@launch
            // The user may have unfollowed while the download was running.
            if (username in dataStore.followedUsers.first()) {
                dataStore.updateProfilePic(username, path)
            } else {
                file.delete()
            }
        }
    }

    companion object {
        private const val AVATAR_MAX_AGE_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}

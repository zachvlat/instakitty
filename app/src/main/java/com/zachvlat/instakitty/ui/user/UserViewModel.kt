package com.zachvlat.instakitty.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.InstaHighlight
import com.zachvlat.instakitty.data.remote.InstaStory
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.Post
import com.zachvlat.instakitty.data.remote.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryViewerState(
    val title: String?,
    val stories: List<InstaStory>
)

data class UserUiState(
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val endCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isFollowing: Boolean = false,
    val highlights: List<InstaHighlight> = emptyList(),
    val stories: List<InstaStory> = emptyList(),
    val viewer: StoryViewerState? = null
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
                    loadExtras(username)
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
            _state.value = _state.value.copy(isFollowing = currentUsername in followed)
        }
    }

    fun openStories() {
        val stories = _state.value.stories
        if (stories.isEmpty()) return
        _state.update { it.copy(viewer = StoryViewerState(it.user?.username, stories)) }
    }

    fun openHighlight(highlight: InstaHighlight) {
        val id = highlight.id ?: return
        viewModelScope.launch {
            val items = when (val result = repository.getHighlightItems(id, currentUsername)) {
                is ApiResult.Success -> result.data
                else -> emptyList()
            }
            if (items.isNotEmpty()) {
                _state.update { it.copy(viewer = StoryViewerState(highlight.title, items)) }
            }
        }
    }

    fun closeViewer() {
        _state.update { it.copy(viewer = null) }
    }

    private fun loadExtras(username: String) {
        viewModelScope.launch {
            val highlights = when (val result = repository.getHighlights(username)) {
                is ApiResult.Success -> result.data
                else -> emptyList()
            }
            _state.update { it.copy(highlights = highlights) }
        }
        viewModelScope.launch {
            val stories = when (val result = repository.getStories(username)) {
                is ApiResult.Success -> result.data
                else -> emptyList()
            }
            _state.update { it.copy(stories = stories) }
        }
    }
}

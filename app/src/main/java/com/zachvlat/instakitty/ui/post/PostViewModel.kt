package com.zachvlat.instakitty.ui.post

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.Comment
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.Post
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PostUiState(
    val post: Post? = null,
    val comments: List<Comment> = emptyList(),
    val commentsEndCursor: String? = null,
    val isLoading: Boolean = true,
    val isLoadingComments: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false
)

class PostViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state.asStateFlow()

    private var currentShortcode: String? = null
    private var loadJob: Job? = null

    fun loadPost(shortcode: String) {
        currentShortcode = shortcode
        loadJob?.cancel()
        _state.value = PostUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            val saved = shortcode in dataStore.getSavedPostsSnapshot()
            when (val result = repository.getPost(shortcode)) {
                is ApiResult.Success -> {
                    val post = result.data
                    if (saved) dataStore.cacheSavedPost(post)
                    _state.value = PostUiState(post = post, isLoading = false, isSaved = saved)
                    loadComments(shortcode)
                }
                is ApiResult.Error -> {
                    _state.value = PostUiState(isLoading = false, isSaved = saved, error = result.message)
                }
                is ApiResult.NetworkError -> {
                    _state.value = PostUiState(isLoading = false, isSaved = saved, error = result.message)
                }
            }
        }
    }

    fun toggleSave() {
        val sc = currentShortcode ?: return
        viewModelScope.launch {
            dataStore.toggleSavePost(sc, _state.value.post)
            _state.value = _state.value.copy(
                isSaved = sc in dataStore.getSavedPostsSnapshot()
            )
        }
    }

    private suspend fun loadComments(shortcode: String) {
        _state.value = _state.value.copy(isLoadingComments = true)
        when (val result = repository.getComments(shortcode)) {
            is ApiResult.Success -> {
                _state.value = _state.value.copy(
                    comments = result.data.items,
                    commentsEndCursor = result.data.endCursor,
                    isLoadingComments = false
                )
            }
            else -> {
                _state.value = _state.value.copy(isLoadingComments = false)
            }
        }
    }
}

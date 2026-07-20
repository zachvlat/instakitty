package com.zachvlat.instakitty.ui.post

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.Comment
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.Post
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
    val error: String? = null
)

class PostViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KittygramRepository(SettingsDataStore(application))

    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state.asStateFlow()

    private var currentShortcode: String? = null

    fun loadPost(shortcode: String) {
        currentShortcode = shortcode
        _state.value = PostUiState(isLoading = true)
        viewModelScope.launch {
            when (val result = repository.getPost(shortcode)) {
                is ApiResult.Success -> {
                    _state.value = PostUiState(post = result.data, isLoading = false)
                    loadComments(shortcode)
                }
                is ApiResult.Error -> {
                    _state.value = PostUiState(isLoading = false, error = result.message)
                }
                is ApiResult.NetworkError -> {
                    _state.value = PostUiState(isLoading = false, error = result.message)
                }
            }
        }
    }

    private fun loadComments(shortcode: String) {
        viewModelScope.launch {
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
}

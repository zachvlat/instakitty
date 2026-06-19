package com.zachvlat.instakitty.ui.post

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PostUiState(
    val post: Post? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class PostViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KittygramRepository(SettingsDataStore(application))

    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state.asStateFlow()

    fun loadPost(shortcode: String) {
        _state.value = PostUiState(isLoading = true)
        viewModelScope.launch {
            when (val result = repository.getPost(shortcode)) {
                is ApiResult.Success -> {
                    _state.value = PostUiState(post = result.data, isLoading = false)
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
}

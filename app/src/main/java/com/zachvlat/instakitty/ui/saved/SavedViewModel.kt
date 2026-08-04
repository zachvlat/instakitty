package com.zachvlat.instakitty.ui.saved

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

data class SavedUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true
)

class SavedViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(SavedUiState())
    val state: StateFlow<SavedUiState> = _state.asStateFlow()

    private val _removing = MutableStateFlow<Set<String>>(emptySet())
    val removing: StateFlow<Set<String>> = _removing.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.savedPosts.collect { shortcodes ->
                val codes = shortcodes.toList().sorted()
                _removing.value = _removing.value.intersect(codes.toSet())
                if (codes.isEmpty()) {
                    _state.value = SavedUiState(posts = emptyList(), isLoading = false)
                    return@collect
                }
                _state.value = _state.value.copy(isLoading = true)
                val loaded = codes.mapNotNull { sc ->
                    when (val result = repository.getPost(sc)) {
                        is ApiResult.Success -> result.data
                        else -> null
                    }
                }
                _state.value = SavedUiState(posts = loaded, isLoading = false)
            }
        }
    }

    fun removeSaved(shortcode: String) {
        _removing.value = _removing.value + shortcode
        viewModelScope.launch {
            try {
                dataStore.toggleSavePost(shortcode)
            } catch (_: Exception) {
                _removing.value = _removing.value - shortcode
            }
        }
    }
}
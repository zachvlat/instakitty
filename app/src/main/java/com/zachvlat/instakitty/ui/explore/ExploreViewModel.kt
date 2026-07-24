package com.zachvlat.instakitty.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.ExploreItem
import com.zachvlat.instakitty.data.remote.KittygramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExploreUiState(
    val searchQuery: String = "",
    val currentTopic: String? = null,
    val items: List<ExploreItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val endCursor: String? = null,
    val favoriteQueries: List<String> = emptyList()
)

class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.favoriteQueries.collect { favorites ->
                _state.value = _state.value.copy(favoriteQueries = favorites)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun search() {
        val query = _state.value.searchQuery.trim().ifBlank { return }
        searchTopic(query)
    }

    fun searchTopic(topic: String) {
        if (topic.isBlank()) return
        _state.value = _state.value.copy(
            searchQuery = topic,
            currentTopic = topic
        )
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true, error = null,
                items = emptyList(), endCursor = null
            )
            when (val result = repository.getPopular(topic)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        items = result.data.items,
                        isLoading = false,
                        endCursor = result.data.endCursor
                    )
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false, error = result.message
                    )
                }
                is ApiResult.NetworkError -> {
                    _state.value = _state.value.copy(
                        isLoading = false, error = result.message
                    )
                }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.endCursor ?: return
        val topic = _state.value.currentTopic ?: return
        if (_state.value.isLoadingMore) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            when (val result = repository.getPopular(topic, cursor)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        items = _state.value.items + result.data.items,
                        isLoadingMore = false,
                        endCursor = result.data.endCursor
                    )
                }
                is ApiResult.Error -> {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
                is ApiResult.NetworkError -> {
                    _state.value = _state.value.copy(isLoadingMore = false)
                }
            }
        }
    }

    fun toggleFavorite() {
        val topic = _state.value.currentTopic ?: return
        viewModelScope.launch {
            dataStore.toggleFavoriteQuery(topic)
        }
    }

    fun removeFavorite(query: String) {
        viewModelScope.launch {
            dataStore.toggleFavoriteQuery(query)
        }
    }

    fun isFavorited(query: String): Boolean = query in _state.value.favoriteQueries
}

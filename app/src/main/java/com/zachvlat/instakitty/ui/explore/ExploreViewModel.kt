package com.zachvlat.instakitty.ui.explore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.ExploreItem
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchMode { Hashtags, Accounts }

data class ExploreUiState(
    val searchQuery: String = "",
    val searchMode: SearchMode = SearchMode.Hashtags,
    val userResults: List<User> = emptyList(),
    val isSearchingUsers: Boolean = false,
    val userSearchError: String? = null,
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

    private var userSearchJob: Job? = null

    // A leading "@" or "#" switches the mode, so either kind of search can be typed directly.
    fun onSearchQueryChange(query: String) {
        val mode = when {
            query.startsWith("@") -> SearchMode.Accounts
            query.startsWith("#") -> SearchMode.Hashtags
            else -> _state.value.searchMode
        }
        _state.value = _state.value.copy(searchQuery = query, searchMode = mode)
        if (mode == SearchMode.Accounts) scheduleUserSearch() else cancelUserSearch()
    }

    fun onSearchModeChange(mode: SearchMode) {
        if (mode == _state.value.searchMode) return
        _state.value = _state.value.copy(searchMode = mode)
        if (mode == SearchMode.Accounts) scheduleUserSearch() else cancelUserSearch()
    }

    fun search() {
        val query = _state.value.searchQuery.trim().removePrefix("#").ifBlank { return }
        searchTopic(query)
    }

    private fun scheduleUserSearch() {
        val query = _state.value.searchQuery.trim().removePrefix("@")
        userSearchJob?.cancel()
        if (query.length < MIN_USER_QUERY) {
            _state.value = _state.value.copy(
                userResults = emptyList(), isSearchingUsers = false, userSearchError = null
            )
            return
        }
        userSearchJob = viewModelScope.launch {
            delay(USER_SEARCH_DEBOUNCE_MILLIS)
            performUserSearch(query)
        }
    }

    private fun cancelUserSearch() {
        userSearchJob?.cancel()
        _state.value = _state.value.copy(isSearchingUsers = false)
    }

    private suspend fun performUserSearch(query: String) {
        _state.value = _state.value.copy(isSearchingUsers = true, userSearchError = null)
        _state.value = when (val result = repository.searchUsers(query)) {
            is ApiResult.Success -> _state.value.copy(
                userResults = result.data,
                isSearchingUsers = false,
                userSearchError = if (result.data.isEmpty()) "No users found" else null
            )
            is ApiResult.Error -> _state.value.copy(
                userResults = emptyList(), isSearchingUsers = false, userSearchError = result.message
            )
            is ApiResult.NetworkError -> _state.value.copy(
                userResults = emptyList(), isSearchingUsers = false, userSearchError = result.message
            )
        }
    }

    fun searchTopic(topic: String) {
        if (topic.isBlank()) return
        _state.value = _state.value.copy(
            searchQuery = topic,
            searchMode = SearchMode.Hashtags,
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

    companion object {
        private const val MIN_USER_QUERY = 3
        private const val USER_SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

package com.zachvlat.instakitty.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.ApiResult
import com.zachvlat.instakitty.data.remote.KittygramRepository
import com.zachvlat.instakitty.data.remote.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val instanceUrl: String = "",
    val searchQuery: String = "",
    val searchResults: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                instanceUrl = dataStore.instanceUrl.first()
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.length >= 4) {
            searchJob = viewModelScope.launch {
                delay(300)
                performSearch(query)
            }
        } else {
            _state.value = _state.value.copy(
                searchResults = emptyList(),
                isSearching = false,
                searchError = null
            )
        }
    }

    private suspend fun performSearch(query: String) {
        _state.value = _state.value.copy(isSearching = true, searchError = null)
        when (val result = repository.searchUsers(query)) {
            is ApiResult.Success -> {
                _state.value = _state.value.copy(
                    searchResults = result.data,
                    isSearching = false,
                    searchError = if (result.data.isEmpty()) "No users found" else null
                )
            }
            is ApiResult.Error -> {
                _state.value = _state.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = result.message
                )
            }
            is ApiResult.NetworkError -> {
                _state.value = _state.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = result.message
                )
            }
        }
    }

    fun clearSearch() {
        _state.value = _state.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            searchError = null
        )
        searchJob?.cancel()
    }
}

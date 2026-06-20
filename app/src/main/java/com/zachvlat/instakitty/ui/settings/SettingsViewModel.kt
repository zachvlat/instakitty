package com.zachvlat.instakitty.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.KittygramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val currentUrl: String = "",
    val currentToken: String = "",
    val status: String = "",
    val isTesting: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val url = dataStore.instanceUrl.first()
            val token = dataStore.apiToken.first()
            _state.value = _state.value.copy(currentUrl = url, currentToken = token)
        }
    }

    fun resetDialogState() {
        _state.value = _state.value.copy(status = "", isTesting = false)
    }

    fun testAndSave(url: String, token: String, onResult: (Boolean, String) -> Unit) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            onResult(false, "Enter an instance URL")
            return
        }
        _state.value = _state.value.copy(isTesting = true, status = "Testing...")
        viewModelScope.launch {
            val result = repository.testConnection(trimmedUrl, token.trim())
            if (result.success) {
                dataStore.saveInstance(trimmedUrl, token.trim())
                _state.value = _state.value.copy(
                    isTesting = false,
                    status = "Saved!",
                    currentUrl = trimmedUrl,
                    currentToken = token.trim()
                )
                onResult(true, "Instance updated successfully")
            } else {
                _state.value = _state.value.copy(
                    isTesting = false,
                    status = result.message
                )
                onResult(false, result.message)
            }
        }
    }
}

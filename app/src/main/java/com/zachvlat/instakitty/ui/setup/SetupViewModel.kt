package com.zachvlat.instakitty.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zachvlat.instakitty.data.local.SettingsDataStore
import com.zachvlat.instakitty.data.remote.KittygramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SetupUiState(
    val instanceUrl: String = "",
    val apiToken: String = "",
    val status: String = "",
    val isTesting: Boolean = false,
    val isConfigured: Boolean = false,
    val showTokenField: Boolean = false
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = SettingsDataStore(application)
    private val repository = KittygramRepository(dataStore)

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val configured = dataStore.isConfigured.first()
            if (configured) {
                val url = dataStore.instanceUrl.first()
                val token = dataStore.apiToken.first()
                _state.value = _state.value.copy(
                    isConfigured = true,
                    instanceUrl = url,
                    apiToken = token
                )
            }
        }
    }

    fun onUrlChange(url: String) {
        _state.value = _state.value.copy(instanceUrl = url, status = "")
    }

    fun onTokenChange(token: String) {
        _state.value = _state.value.copy(apiToken = token)
    }

    fun testAndSave() {
        val url = _state.value.instanceUrl.trim()
        if (url.isBlank()) {
            _state.value = _state.value.copy(status = "Enter an instance URL")
            return
        }
        _state.value = _state.value.copy(isTesting = true, status = "Testing...")
        viewModelScope.launch {
            val result = repository.testConnection(url, _state.value.apiToken)
            if (result.success) {
                dataStore.saveInstance(url, _state.value.apiToken)
                _state.value = _state.value.copy(
                    isTesting = false,
                    status = "Saved!",
                    isConfigured = true
                )
            } else {
                _state.value = _state.value.copy(
                    isTesting = false,
                    status = result.message,
                    showTokenField = result.message.contains("401")
                )
            }
        }
    }
}

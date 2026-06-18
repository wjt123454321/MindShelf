package com.example.mindshelf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.local.AiPreferences
import com.example.mindshelf.data.repository.AiProvider
import com.example.mindshelf.data.repository.AiProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val providers: List<AiProvider> = emptyList(),
    val activeChannel: String = AiPreferences.CHANNEL_BUILTIN,
    val enableTools: Boolean = true,
    val enableSearch: Boolean = false,
    val autoReadReplies: Boolean = false,
    val showAddDialog: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val repository: AiProviderRepository,
    private val aiPreferences: AiPreferences,
) : ViewModel() {

    private val _showAddDialog = MutableStateFlow(false)
    private val _saving = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AiSettingsUiState> = combine(
        combine(
            repository.observeProviders(),
            aiPreferences.channel,
            aiPreferences.enableTools,
            aiPreferences.enableSearch,
            aiPreferences.autoReadReplies,
        ) { providers, channel, tools, search, autoRead ->
            arrayOf(providers, channel, tools, search, autoRead)
        },
        combine(_showAddDialog, _saving, _error) { showDialog, saving, error ->
            Triple(showDialog, saving, error)
        },
    ) { prefs, (showDialog, saving, error) ->
        @Suppress("UNCHECKED_CAST")
        AiSettingsUiState(
            providers = prefs[0] as List<AiProvider>,
            activeChannel = prefs[1] as String,
            enableTools = prefs[2] as Boolean,
            enableSearch = prefs[3] as Boolean,
            autoReadReplies = prefs[4] as Boolean,
            showAddDialog = showDialog,
            saving = saving,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettingsUiState())

    fun showAddDialog() {
        _showAddDialog.value = true
        _error.value = null
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
    }

    fun setActiveChannel(channel: String) {
        viewModelScope.launch {
            repository.setActiveChannel(channel)
        }
    }

    fun setToolsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferences.setToolsEnabled(enabled)
        }
    }

    fun setSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferences.setSearchEnabled(enabled)
        }
    }

    fun setAutoReadReplies(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferences.setAutoReadReplies(enabled)
        }
    }

    fun addProvider(name: String, baseUrl: String, model: String, apiKey: String) {
        if (name.isBlank() || baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) {
            _error.value = "请填写完整信息"
            return
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                repository.addProvider(name, baseUrl, model, apiKey)
                _showAddDialog.value = false
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "保存失败"
            } finally {
                _saving.value = false
            }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repository.deleteProvider(id)
        }
    }
}

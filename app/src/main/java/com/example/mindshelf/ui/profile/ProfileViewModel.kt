package com.example.mindshelf.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.local.SyncPreferences
import com.example.mindshelf.data.remote.dto.SyncConflict
import com.example.mindshelf.data.remote.dto.UserDto
import com.example.mindshelf.data.repository.AuthRepository
import com.example.mindshelf.data.repository.SessionExpiredException
import com.example.mindshelf.data.repository.SyncEngine
import com.example.mindshelf.data.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = false,
    val user: UserDto? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val cloudSyncEnabled: Boolean = false,
    val syncing: Boolean = false,
    val syncMessage: String? = null,
    val activeConflict: SyncConflict? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferences,
    private val syncCoordinator: SyncCoordinator,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val syncConflicts: StateFlow<List<SyncConflict>> = syncEngine.conflicts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var loaded = false

    init {
        loadUser()
        viewModelScope.launch {
            syncPreferences.cloudSyncEnabled.collect { enabled ->
                _uiState.update { it.copy(cloudSyncEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            syncEngine.conflicts.collect { conflicts ->
                _uiState.update { it.copy(activeConflict = conflicts.firstOrNull()) }
            }
        }
    }

    fun loadUser(isRefresh: Boolean = false) {
        if (!isRefresh && loaded && _uiState.value.user != null) return
        viewModelScope.launch {
            if (!isRefresh) {
                authRepository.getCachedUser()?.let { cached ->
                    _uiState.update { it.copy(user = cached) }
                }
            }
            if (isRefresh) {
                _refreshing.value = true
            } else if (_uiState.value.user == null) {
                _uiState.update { it.copy(loading = true, error = null, sessionExpired = false) }
            }
            try {
                val user = authRepository.me()
                loaded = true
                _uiState.update { it.copy(loading = false, user = user, error = null) }
            } catch (e: SessionExpiredException) {
                loaded = false
                authRepository.logout()
                _uiState.update {
                    it.copy(loading = false, sessionExpired = true, error = e.message)
                }
            } catch (e: Exception) {
                _uiState.update {
                    if (it.user != null) {
                        it.copy(loading = false)
                    } else {
                        it.copy(loading = false, error = e.message ?: "加载失败")
                    }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun refresh() = loadUser(isRefresh = true)

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncPreferences.setCloudSyncEnabled(enabled)
            if (enabled) {
                syncNow()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(syncing = true, syncMessage = null) }
            runCatching { syncCoordinator.syncAll() }
                .onSuccess { result ->
                    val msg = when {
                        result.conflicts.isNotEmpty() -> "同步完成，有 ${result.conflicts.size} 处冲突待处理"
                        result.applied > 0 -> "已同步 ${result.applied} 项变更"
                        else -> "已是最新"
                    }
                    _uiState.update { it.copy(syncing = false, syncMessage = msg) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(syncing = false, syncMessage = e.message ?: "同步失败")
                    }
                }
        }
    }

    fun resolveConflict(conflict: SyncConflict, resolution: String) {
        viewModelScope.launch {
            syncEngine.resolveConflict(conflict, resolution)
            syncNow()
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            loaded = false
            onDone()
        }
    }
}

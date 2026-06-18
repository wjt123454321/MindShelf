package com.example.mindshelf.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.UserDto
import com.example.mindshelf.data.repository.AuthRepository
import com.example.mindshelf.data.repository.SessionExpiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = false,
    val user: UserDto? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var loaded = false

    init {
        loadUser()
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

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            loaded = false
            onDone()
        }
    }
}

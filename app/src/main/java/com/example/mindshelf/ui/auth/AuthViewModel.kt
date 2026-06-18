package com.example.mindshelf.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = false,
    val checkingSession: Boolean = true,
    val error: String? = null,
    val info: String? = null,
    val loggedIn: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedIn
                .distinctUntilChanged()
                .collect { token ->
                    if (token == null) {
                        _uiState.update { it.copy(checkingSession = false, loggedIn = false) }
                        return@collect
                    }
                    _uiState.update { it.copy(checkingSession = true) }
                    val valid = authRepository.validateSession()
                    _uiState.update {
                        it.copy(checkingSession = false, loggedIn = valid)
                    }
                    if (valid) {
                        authRepository.syncIfLoggedIn()
                    }
                }
        }
    }

    private fun runAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, info = null) }
            try {
                block()
                _uiState.update { it.copy(loading = false, loggedIn = true, checkingSession = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "操作失败") }
            }
        }
    }

    fun register(email: String, password: String, code: String, username: String?) =
        runAuth { authRepository.register(email, password, code, username) }

    fun login(account: String, password: String) =
        runAuth { authRepository.login(account, password) }

    fun sendCode(email: String, purpose: String = "login") {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, info = null) }
            try {
                authRepository.sendCode(email, purpose)
                _uiState.update { it.copy(info = "验证码已发送，请查收邮件") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun loginWithCode(email: String, code: String) =
        runAuth { authRepository.loginWithCode(email, code) }
}

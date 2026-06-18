package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.TokenStore
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.sync.SyncCoordinator
import com.example.mindshelf.data.remote.dto.LoginCodeRequest
import com.example.mindshelf.data.remote.dto.LoginRequest
import com.example.mindshelf.data.remote.dto.RefreshRequest
import com.example.mindshelf.data.remote.dto.RegisterRequest
import com.example.mindshelf.data.remote.dto.SendCodeRequest
import com.example.mindshelf.data.remote.dto.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: MindShelfApi,
    private val tokenStore: TokenStore,
    private val syncCoordinator: SyncCoordinator,
) {
    val isLoggedIn: Flow<String?> = tokenStore.accessToken

    suspend fun register(email: String, password: String, code: String, username: String?): UserDto {
        val result = api.register(RegisterRequest(email, password, code, username)).data
        tokenStore.save(result.accessToken, result.refreshToken)
        tokenStore.saveUser(result.user)
        syncAfterLogin()
        return result.user
    }

    suspend fun login(account: String, password: String): UserDto {
        val result = api.login(LoginRequest(account, password)).data
        tokenStore.save(result.accessToken, result.refreshToken)
        tokenStore.saveUser(result.user)
        syncAfterLogin()
        return result.user
    }

    suspend fun sendCode(email: String, purpose: String = "login") {
        api.sendCode(SendCodeRequest(email, purpose))
    }

    suspend fun loginWithCode(email: String, code: String): UserDto {
        val result = api.loginWithCode(LoginCodeRequest(email, code)).data
        tokenStore.save(result.accessToken, result.refreshToken)
        tokenStore.saveUser(result.user)
        syncAfterLogin()
        return result.user
    }

    /** 校验本地 token；无效则清除并返回 false。 */
    suspend fun validateSession(): Boolean {
        if (tokenStore.getAccessToken() == null) return false
        return try {
            withTimeout(5_000) {
                val user = api.me().data
                tokenStore.saveUser(user)
            }
            true
        } catch (e: HttpException) {
            if (e.code() == 401) {
                refreshSession() || run {
                    tokenStore.clear()
                    false
                }
            } else {
                // 网络错误等：保留 token，允许离线进入
                true
            }
        } catch (_: Exception) {
            true
        }
    }

    suspend fun me(): UserDto {
        if (tokenStore.getAccessToken() == null) {
            throw SessionExpiredException()
        }
        return try {
            val user = api.me().data
            tokenStore.saveUser(user)
            user
        } catch (e: HttpException) {
            if (e.code() == 401 && refreshSession()) {
                val user = api.me().data
                tokenStore.saveUser(user)
                user
            } else if (e.code() == 401) {
                tokenStore.clear()
                throw SessionExpiredException()
            } else {
                tokenStore.getCachedUser() ?: throw e
            }
        } catch (e: Exception) {
            tokenStore.getCachedUser() ?: throw e
        }
    }

    suspend fun getCachedUser(): UserDto? = tokenStore.getCachedUser()

    suspend fun logout() {
        tokenStore.clear()
    }

    suspend fun syncIfLoggedIn() {
        if (tokenStore.getAccessToken() != null) {
            try {
                syncCoordinator.syncAll()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun refreshSession(): Boolean {
        val refresh = tokenStore.getRefreshToken() ?: return false
        return try {
            val result = api.refresh(RefreshRequest(refresh)).data
            tokenStore.save(result.accessToken, result.refreshToken)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun syncAfterLogin() {
        try {
            syncCoordinator.afterLogin()
        } catch (_: Exception) {
        }
    }
}

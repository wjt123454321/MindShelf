package com.example.mindshelf.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mindshelf.data.remote.dto.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("auth")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val userIdKey = stringPreferencesKey("user_id")
    private val userEmailKey = stringPreferencesKey("user_email")
    private val userNameKey = stringPreferencesKey("user_username")
    private val userCreatedKey = longPreferencesKey("user_created_at")

    val accessToken: Flow<String?> = context.dataStore.data.map { it[accessKey] }

    suspend fun save(access: String, refresh: String) {
        context.dataStore.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
        }
    }

    suspend fun saveUser(user: UserDto) {
        context.dataStore.edit {
            it[userIdKey] = user.id
            it[userEmailKey] = user.email
            it[userNameKey] = user.username.orEmpty()
            it[userCreatedKey] = user.createdAt
        }
    }

    suspend fun getCachedUser(): UserDto? {
        val prefs = context.dataStore.data.first()
        val id = prefs[userIdKey] ?: return null
        return UserDto(
            id = id,
            email = prefs[userEmailKey].orEmpty(),
            username = prefs[userNameKey]?.takeIf { it.isNotBlank() },
            createdAt = prefs[userCreatedKey] ?: 0L,
        )
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun getAccessToken(): String? =
        context.dataStore.data.first()[accessKey]

    suspend fun getRefreshToken(): String? =
        context.dataStore.data.first()[refreshKey]
}

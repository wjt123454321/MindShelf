package com.example.mindshelf.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 本机加密存储敏感数据（如自定义 API Key），不上云、不同步。 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mindshelf_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString(keyFor(providerId), apiKey).apply()
    }

    fun getApiKey(providerId: String): String? =
        prefs.getString(keyFor(providerId), null)?.takeIf { it.isNotBlank() }

    fun deleteApiKey(providerId: String) {
        prefs.edit().remove(keyFor(providerId)).apply()
    }

    private fun keyFor(providerId: String) = "ai_key_$providerId"
}

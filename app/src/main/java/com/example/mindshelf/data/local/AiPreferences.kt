package com.example.mindshelf.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiPrefs by preferencesDataStore("ai_settings")

/** AI 通道与对话选项（不含 API Key）。 */
@Singleton
class AiPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val channelKey = stringPreferencesKey("channel")
    private val enableToolsKey = booleanPreferencesKey("enable_tools")
    private val enableSearchKey = booleanPreferencesKey("enable_search")
    private val builtinModelKey = stringPreferencesKey("builtin_model")
    private val autoReadRepliesKey = booleanPreferencesKey("auto_read_replies")

    val channel: Flow<String> = context.aiPrefs.data.map { it[channelKey] ?: CHANNEL_BUILTIN }
    val enableTools: Flow<Boolean> = context.aiPrefs.data.map { it[enableToolsKey] ?: true }
    val enableSearch: Flow<Boolean> = context.aiPrefs.data.map { it[enableSearchKey] ?: false }
    val builtinModel: Flow<String> = context.aiPrefs.data.map { it[builtinModelKey] ?: MODEL_FLASH }
    val autoReadReplies: Flow<Boolean> = context.aiPrefs.data.map { it[autoReadRepliesKey] ?: false }

    suspend fun getChannel(): String =
        context.aiPrefs.data.first()[channelKey] ?: CHANNEL_BUILTIN

    suspend fun setChannel(value: String) {
        context.aiPrefs.edit { it[channelKey] = value }
    }

    suspend fun isToolsEnabled(): Boolean =
        context.aiPrefs.data.first()[enableToolsKey] ?: true

    suspend fun setToolsEnabled(enabled: Boolean) {
        context.aiPrefs.edit { it[enableToolsKey] = enabled }
    }

    suspend fun isSearchEnabled(): Boolean =
        context.aiPrefs.data.first()[enableSearchKey] ?: false

    suspend fun setSearchEnabled(enabled: Boolean) {
        context.aiPrefs.edit { it[enableSearchKey] = enabled }
    }

    suspend fun getBuiltinModel(): String =
        context.aiPrefs.data.first()[builtinModelKey] ?: MODEL_FLASH

    suspend fun setBuiltinModel(model: String) {
        if (model !in BUILTIN_MODELS) return
        context.aiPrefs.edit { it[builtinModelKey] = model }
    }

    suspend fun isAutoReadRepliesEnabled(): Boolean =
        context.aiPrefs.data.first()[autoReadRepliesKey] ?: false

    suspend fun setAutoReadReplies(enabled: Boolean) {
        context.aiPrefs.edit { it[autoReadRepliesKey] = enabled }
    }

    companion object {
        const val CHANNEL_BUILTIN = "builtin"
        const val MODEL_FLASH = "deepseek-v4-flash"
        const val MODEL_V4_PRO = "deepseek-v4-pro"
        val BUILTIN_MODELS = setOf(MODEL_FLASH, MODEL_V4_PRO)

        fun builtinModelLabel(model: String): String = when (model) {
            MODEL_V4_PRO -> "V4 Pro"
            MODEL_FLASH -> "Flash"
            else -> model
        }
    }
}

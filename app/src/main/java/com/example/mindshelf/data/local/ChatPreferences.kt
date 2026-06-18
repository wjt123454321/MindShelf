package com.example.mindshelf.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.chatPrefs by preferencesDataStore("chat_settings")

data class LastActiveChat(
    val conversationId: String,
    val branchId: String,
)

/** 上次打开的会话与分支，用于返回对话页时恢复上下文。 */
@Singleton
class ChatPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val conversationIdKey = stringPreferencesKey("last_conversation_id")
    private val branchIdKey = stringPreferencesKey("last_branch_id")

    suspend fun getLastActiveChat(): LastActiveChat? {
        val prefs = context.chatPrefs.data.first()
        val conversationId = prefs[conversationIdKey] ?: return null
        val branchId = prefs[branchIdKey] ?: return null
        return LastActiveChat(conversationId, branchId)
    }

    suspend fun setLastActiveChat(conversationId: String, branchId: String) {
        context.chatPrefs.edit {
            it[conversationIdKey] = conversationId
            it[branchIdKey] = branchId
        }
    }

    suspend fun clearLastActiveChat() {
        context.chatPrefs.edit {
            it.remove(conversationIdKey)
            it.remove(branchIdKey)
        }
    }
}

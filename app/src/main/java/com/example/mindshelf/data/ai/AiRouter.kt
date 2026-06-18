package com.example.mindshelf.data.ai

import com.example.mindshelf.data.local.AiPreferences
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.repository.ChatStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** 内置与自定义 API 统一经客户端 ToolLoopEngine。 */
@Singleton
class AiRouter @Inject constructor(
    private val toolLoopEngine: ToolLoopEngine,
    private val aiPreferences: AiPreferences,
    private val aiProviderRepository: com.example.mindshelf.data.repository.AiProviderRepository,
) {
    suspend fun getActiveChannelLabel(): String {
        val channel = aiPreferences.getChannel()
        if (channel == AiPreferences.CHANNEL_BUILTIN) return "内置服务"
        return aiProviderRepository.getProvider(channel)?.name ?: "内置服务"
    }

    fun streamChat(
        conversationId: String,
        branchId: String,
        parentMessageId: String?,
        content: String,
        history: List<MessageDto> = emptyList(),
    ): Flow<ChatStreamEvent> = flow {
        val enableTools = aiPreferences.isToolsEnabled()
        val enableSearch = aiPreferences.isSearchEnabled()
        toolLoopEngine.streamChat(history, content, enableTools, enableSearch).collect { emit(it) }
    }

    fun resumeAfterToolConfirm(pendingId: String, approved: Boolean): Flow<ChatStreamEvent> =
        toolLoopEngine.resumeAfterConfirm(pendingId, approved)
}

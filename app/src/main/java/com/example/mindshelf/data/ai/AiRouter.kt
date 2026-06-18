package com.example.mindshelf.data.ai

import com.example.mindshelf.data.local.AiPreferences
import com.example.mindshelf.data.remote.dto.ChatOptions
import com.example.mindshelf.data.repository.AiProviderRepository
import com.example.mindshelf.data.repository.ChatRepository
import com.example.mindshelf.data.repository.ChatStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** 内置服务与自定义 API 双通道路由。 */
@Singleton
class AiRouter @Inject constructor(
    private val chatRepository: ChatRepository,
    private val customAiClient: CustomAiClient,
    private val aiProviderRepository: AiProviderRepository,
    private val aiPreferences: AiPreferences,
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
        history: List<com.example.mindshelf.data.remote.dto.MessageDto> = emptyList(),
    ): Flow<ChatStreamEvent> = flow {
        val channel = aiPreferences.getChannel()
        val enableTools = aiPreferences.isToolsEnabled()
        val searchEnabled = aiPreferences.isSearchEnabled()
        val enableSearch = channel == AiPreferences.CHANNEL_BUILTIN && searchEnabled
        val builtinModel = aiPreferences.getBuiltinModel()
        val chatOptions = ChatOptions(
            enableTools = enableTools,
            enableSearch = enableSearch,
            model = if (channel == AiPreferences.CHANNEL_BUILTIN) builtinModel else null,
        )
        if (channel == AiPreferences.CHANNEL_BUILTIN) {
            chatRepository.streamChat(
                conversationId,
                branchId,
                parentMessageId,
                content,
                chatOptions,
            ).collect { emit(it) }
            return@flow
        }

        val provider = aiProviderRepository.getProvider(channel)
            ?: run {
                emit(ChatStreamEvent.Error("自定义 API 不存在，已回退内置服务"))
                chatRepository.streamChat(
                    conversationId,
                    branchId,
                    parentMessageId,
                    content,
                    chatOptions,
                ).collect { emit(it) }
                return@flow
            }
        val apiKey = aiProviderRepository.getApiKey(channel)
            ?: run {
                emit(ChatStreamEvent.Error("未配置 API Key"))
                return@flow
            }

        customAiClient.streamChat(provider, apiKey, history, content).collect { emit(it) }
    }

    suspend fun confirmTool(pendingId: String, approved: Boolean) =
        chatRepository.confirmTool(pendingId, approved)

    fun streamToolResume(pendingId: String): Flow<ChatStreamEvent> = flow {
        val channel = aiPreferences.getChannel()
        val enableTools = aiPreferences.isToolsEnabled()
        val searchEnabled = aiPreferences.isSearchEnabled()
        val enableSearch = channel == AiPreferences.CHANNEL_BUILTIN && searchEnabled
        val builtinModel = aiPreferences.getBuiltinModel()
        chatRepository.streamToolResume(
            pendingId,
            ChatOptions(
                enableTools = enableTools,
                enableSearch = enableSearch,
                model = if (channel == AiPreferences.CHANNEL_BUILTIN) builtinModel else null,
            ),
        ).collect { emit(it) }
    }
}

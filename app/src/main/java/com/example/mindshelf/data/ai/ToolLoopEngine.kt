package com.example.mindshelf.data.ai

import com.example.mindshelf.data.local.AiPreferences
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.ToolPreview
import com.example.mindshelf.data.repository.AiProviderRepository
import com.example.mindshelf.data.repository.ChatStreamEvent
import com.example.mindshelf.data.repository.SearchSource
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PendingToolState(
    val id: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val preview: ToolPreview,
    val workingMessages: JsonArray,
    val toolCallId: String,
    val partialContent: String,
    val partialReasoning: String,
    val model: String,
    val useBuiltin: Boolean,
    val customProviderId: String?,
    val enableSearch: Boolean,
)

@Singleton
class ToolLoopEngine @Inject constructor(
    private val builtinLlm: BuiltinLlmClient,
    private val customAiClient: CustomAiClient,
    private val dispatcher: ClientToolDispatcher,
    private val aiPreferences: AiPreferences,
    private val aiProviderRepository: AiProviderRepository,
) {
    private val gson = Gson()
    private val pendingStates = ConcurrentHashMap<String, PendingToolState>()

    fun streamChat(
        history: List<MessageDto>,
        userContent: String,
        enableTools: Boolean,
        enableSearch: Boolean,
    ): Flow<ChatStreamEvent> = flow {
        val channel = aiPreferences.getChannel()
        val useBuiltin = channel == AiPreferences.CHANNEL_BUILTIN
        val model = if (useBuiltin) aiPreferences.getBuiltinModel() else {
            aiProviderRepository.getProvider(channel)?.model ?: AiPreferences.MODEL_FLASH
        }
        val tools = if (enableTools) ToolSchemas.schemas(enableSearch) else null
        val messages = buildMessages(history, userContent, enableSearch)
        runLoop(
            messages = messages,
            model = model,
            tools = tools,
            useBuiltin = useBuiltin,
            customProviderId = if (useBuiltin) null else channel,
            enableSearch = enableSearch,
            enableTools = enableTools,
            startRound = 0,
            initialContent = "",
            initialReasoning = "",
        ) { emit(it) }
    }

    fun resumeAfterConfirm(pendingId: String, approved: Boolean): Flow<ChatStreamEvent> = flow {
        val state = pendingStates[pendingId] ?: run {
            emit(ChatStreamEvent.Error("待确认操作已过期"))
            return@flow
        }
        if (!approved) {
            pendingStates.remove(pendingId)
            emit(ChatStreamEvent.Status("thinking"))
            return@flow
        }
        val result = dispatcher.executeWrite(state.toolName, state.arguments)
        emit(ChatStreamEvent.ToolExecuted(state.toolName, state.preview, result))
        val toolMsg = JsonObject().apply {
            addProperty("role", "tool")
            addProperty("tool_call_id", state.toolCallId)
            addProperty("content", dispatcher.toolResultJson(result))
        }
        state.workingMessages.add(toolMsg)
        val tools = ToolSchemas.schemas(state.enableSearch)
        try {
            runLoop(
                messages = state.workingMessages,
                model = state.model,
                tools = tools,
                useBuiltin = state.useBuiltin,
                customProviderId = state.customProviderId,
                enableSearch = state.enableSearch,
                enableTools = true,
                startRound = 0,
                initialContent = state.partialContent,
                initialReasoning = state.partialReasoning,
            ) { emit(it) }
        } finally {
            pendingStates.remove(pendingId)
        }
    }

    private suspend fun runLoop(
        messages: JsonArray,
        model: String,
        tools: JsonArray?,
        useBuiltin: Boolean,
        customProviderId: String?,
        enableSearch: Boolean,
        enableTools: Boolean,
        startRound: Int,
        initialContent: String,
        initialReasoning: String,
        emit: suspend (ChatStreamEvent) -> Unit,
    ) {
        var workingMessages = messages.deepCopy()
        var fullContent = initialContent
        var fullReasoning = initialReasoning
        val maxRounds = startRound + 6

        for (round in startRound until maxRounds) {
            if (round > startRound && fullReasoning.isNotBlank()) {
                fullReasoning += "\n\n---\n\n"
                emit(ChatStreamEvent.ReasoningRoundStart(round + 1))
            } else if (round == startRound && fullReasoning.isBlank()) {
                emit(ChatStreamEvent.Status("thinking"))
            }

            var roundContent = ""
            var roundReasoning = ""
            val toolCalls = mutableListOf<LlmToolCall>()
            var roundFailed = false

            val roundFlow = if (useBuiltin) {
                builtinLlm.completeRound(workingMessages, model, if (enableTools) tools else null)
            } else {
                val provider = customProviderId?.let { aiProviderRepository.getProvider(it) }
                val apiKey = customProviderId?.let { aiProviderRepository.getApiKey(it) }
                if (provider == null || apiKey == null) {
                    emit(ChatStreamEvent.Error("自定义 API 不可用"))
                    return
                }
                customAiClient.completeRound(provider, apiKey, workingMessages, model, if (enableTools) tools else null)
            }

            roundFlow.collect { delta ->
                when (delta) {
                    is LlmStreamDelta.Reasoning -> {
                        roundReasoning += delta.text
                        fullReasoning += delta.text
                        emit(ChatStreamEvent.ReasoningDelta(delta.text))
                    }
                    is LlmStreamDelta.Content -> {
                        roundContent += delta.text
                        fullContent += delta.text
                        emit(ChatStreamEvent.Delta(delta.text))
                    }
                    is LlmStreamDelta.RoundComplete -> toolCalls.addAll(delta.toolCalls)
                    is LlmStreamDelta.Error -> {
                        emit(ChatStreamEvent.Error(delta.message))
                        roundFailed = true
                        return@collect
                    }
                }
            }
            if (roundFailed) return

            if (toolCalls.isEmpty()) {
                emit(ChatStreamEvent.StreamComplete)
                return
            }

            val assistantMsg = JsonObject().apply {
                addProperty("role", "assistant")
                if (roundContent.isNotBlank()) addProperty("content", roundContent)
                val tcArr = JsonArray()
                toolCalls.forEach { tc ->
                    tcArr.add(JsonObject().apply {
                        addProperty("id", tc.id)
                        addProperty("type", "function")
                        add("function", JsonObject().apply {
                            addProperty("name", tc.name)
                            addProperty("arguments", tc.arguments)
                        })
                    })
                }
                add("tool_calls", tcArr)
            }
            workingMessages.add(assistantMsg)

            for (tc in toolCalls) {
                val args = dispatcher.parseArguments(tc.arguments)
                if (tc.name == "web_search") {
                    emit(ChatStreamEvent.Status("searching"))
                }
                emit(ChatStreamEvent.Status("tool", tc.name))

                if (ToolSchemas.isWriteTool(tc.name)) {
                    val preview = dispatcher.buildWritePreview(tc.name, args)
                    val pendingId = UUID.randomUUID().toString()
                    pendingStates[pendingId] = PendingToolState(
                        id = pendingId,
                        toolName = tc.name,
                        arguments = args,
                        preview = preview,
                        workingMessages = workingMessages.deepCopy(),
                        toolCallId = tc.id,
                        partialContent = fullContent,
                        partialReasoning = fullReasoning,
                        model = model,
                        useBuiltin = useBuiltin,
                        customProviderId = customProviderId,
                        enableSearch = enableSearch,
                    )
                    emit(ChatStreamEvent.ToolPending(pendingId, tc.name, preview))
                    return
                }

                val result = dispatcher.executeRead(tc.name, args)
                if (tc.name == "web_search") {
                    @Suppress("UNCHECKED_CAST")
                    val results = result["results"] as? List<Map<String, Any?>>
                    if (!results.isNullOrEmpty()) {
                        val sources = results.mapNotNull { item ->
                            val title = item["title"]?.toString() ?: return@mapNotNull null
                            val url = item["url"]?.toString() ?: return@mapNotNull null
                            SearchSource(
                                title = title,
                                url = url,
                                snippet = item["snippet"]?.toString().orEmpty(),
                                content = item["content"]?.toString().orEmpty(),
                            )
                        }
                        if (sources.isNotEmpty()) {
                            emit(ChatStreamEvent.SearchResult(result["query"]?.toString().orEmpty(), sources))
                        }
                    }
                }
                workingMessages.add(JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("tool_call_id", tc.id)
                    addProperty("content", dispatcher.toolResultJson(result))
                })
            }
        }
    }

    private fun buildMessages(history: List<MessageDto>, userContent: String, enableSearch: Boolean): JsonArray {
        val system = buildString {
            append("你是 MindShelf 知识库助手，简洁准确地回答用户问题。")
            append("工具 search_knowledge_bases、search_notes 用于检索本机知识库与笔记。")
            append("删除或修改内容前需用户明确意图。")
            if (enableSearch) {
                append("用户已开启联网搜索；外部信息请用 web_search，本地内容用 search_notes/search_knowledge_bases。")
                append("引用外部来源使用 Markdown 链接 [标题](url)。")
            }
        }
        val arr = JsonArray()
        arr.add(msg("system", system))
        history.filter { it.role == "user" || it.role == "assistant" }.forEach { m ->
            arr.add(msg(m.role, messageTextForLlm(m)))
        }
        val last = history.lastOrNull()
        if (last?.role != "user" || messageTextForLlm(last) != userContent) {
            arr.add(msg("user", userContent))
        }
        return arr
    }

    private fun messageTextForLlm(message: MessageDto): String {
        val segmentContent = message.segments
            ?.filter { it.type == "content" }
            ?.joinToString("") { it.text }
            .orEmpty()
        if (segmentContent.isNotBlank()) return segmentContent
        return message.content.orEmpty()
    }

    private fun msg(role: String, content: String): JsonObject =
        JsonObject().apply {
            addProperty("role", role)
            addProperty("content", content)
        }

    private fun JsonArray.deepCopy(): JsonArray =
        JsonParser.parseString(gson.toJson(this)).asJsonArray
}

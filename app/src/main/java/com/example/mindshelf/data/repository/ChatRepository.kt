package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.dao.ChatDao
import com.example.mindshelf.data.local.entity.ToolActionEntity
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.local.toDto
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.BranchDto
import com.example.mindshelf.data.remote.dto.ChatMessagePayload
import com.example.mindshelf.data.remote.dto.ChatOptions
import com.example.mindshelf.data.remote.dto.ChatStreamRequest
import com.example.mindshelf.data.remote.dto.ConversationDto
import com.example.mindshelf.data.remote.dto.CreateBranchRequest
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.chat.buildBranchPathMessages
import com.example.mindshelf.data.remote.dto.PendingToolDto
import com.example.mindshelf.data.remote.dto.ToolConfirmResultDto
import com.example.mindshelf.data.remote.dto.ToolPreview
import com.example.mindshelf.data.remote.dto.ToolResumeRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.BufferedSource
import java.io.EOFException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChatStreamEvent {
    data class Status(val phase: String, val tool: String? = null) : ChatStreamEvent()
    data class ReasoningRoundStart(val round: Int) : ChatStreamEvent()
    data class ReasoningDelta(val content: String) : ChatStreamEvent()
    data class Delta(val content: String) : ChatStreamEvent()
    data class MessageDone(val message: MessageDto) : ChatStreamEvent()
    data class SearchResult(
        val query: String,
        val results: List<SearchSource>,
    ) : ChatStreamEvent()
    data class ToolPending(
        val pendingId: String,
        val tool: String,
        val preview: ToolPreview,
    ) : ChatStreamEvent()
    data class Error(val message: String) : ChatStreamEvent()
    data class Done(val conversationId: String, val branchId: String) : ChatStreamEvent()
}

data class SearchSource(
    val title: String,
    val url: String,
    val snippet: String = "",
    /** 网页正文摘录（服务端抓取） */
    val content: String = "",
)

@Singleton
class ChatRepository @Inject constructor(
    private val api: MindShelfApi,
    private val chatDao: ChatDao,
) {
    private val gson = Gson()

    suspend fun listConversationsLocal(): List<ConversationDto> =
        chatDao.getConversations().map {
            withDisplayTitle(ConversationDto(it.id, it.title, it.createdAt, it.updatedAt))
        }

    suspend fun listConversations(): List<ConversationDto> =
        try {
            val remote = api.listConversations().data
            chatDao.upsertConversations(remote.map { it.toEntity() })
            remote.map { withDisplayTitle(it) }
        } catch (_: Exception) {
            listConversationsLocal()
        }

    private suspend fun withDisplayTitle(conv: ConversationDto): ConversationDto {
        if (conv.title != "新对话") return conv
        val preview = chatDao.getFirstUserMessage(conv.id)?.content?.trim()?.take(40)
        return if (preview.isNullOrBlank()) conv else conv.copy(title = preview)
    }

    suspend fun isConversationEmpty(conversationId: String): Boolean =
        chatDao.countMessages(conversationId) == 0

    /** 返回列表中第一个无消息的空对话，若无则 null。 */
    suspend fun findEmptyConversation(): ConversationDto? =
        listConversationsLocal().firstOrNull { isConversationEmpty(it.id) }

    suspend fun createConversation(title: String = "新对话"): ConversationDto {
        val remote = api.createConversation(
            com.example.mindshelf.data.remote.dto.CreateConversationRequest(title = title),
        ).data
        chatDao.upsertConversations(listOf(remote.toEntity()))
        return remote
    }

    suspend fun deleteConversation(id: String) {
        try {
            api.deleteConversation(id)
        } catch (_: Exception) {
            // 离线时仍清理本地缓存
        }
        chatDao.deleteToolActions(id)
        chatDao.deleteAllMessages(id)
        chatDao.deleteBranches(id)
        chatDao.deleteConversation(id)
    }

    suspend fun listBranchesLocal(conversationId: String): List<BranchDto> =
        chatDao.getBranches(conversationId).map {
            BranchDto(it.id, it.conversationId, it.label, it.rootMessageId, it.createdAt)
        }

    suspend fun listBranches(conversationId: String): List<BranchDto> =
        try {
            val remote = api.listBranches(conversationId).data
            chatDao.upsertBranches(remote.map { it.toEntity() })
            remote
        } catch (_: Exception) {
            listBranchesLocal(conversationId)
        }

    suspend fun createBranch(
        conversationId: String,
        forkFromMessageId: String,
        label: String = "新分支",
    ): BranchDto {
        val branch = api.createBranch(
            conversationId,
            CreateBranchRequest(label = label, forkFromMessageId = forkFromMessageId),
        ).data
        chatDao.upsertBranches(listOf(branch.toEntity()))
        return branch
    }

    suspend fun upsertMessages(messages: List<MessageDto>) {
        if (messages.isEmpty()) return
        chatDao.upsertMessages(
            messages.map { dto ->
                val existing = chatDao.getMessageById(dto.id)
                dto.toEntity(existing?.searchSourcesJson ?: "[]")
            },
        )
    }

    suspend fun saveMessageSearchSources(messageId: String, sources: List<SearchSource>) {
        if (sources.isEmpty()) return
        chatDao.updateMessageSearchSources(messageId, gson.toJson(sources))
    }

    suspend fun loadMessageSearchSources(conversationId: String): Map<String, List<SearchSource>> =
        chatDao.getMessageSearchSourceRows(conversationId)
            .mapNotNull { row ->
                val sources = runCatching {
                    gson.fromJson(row.searchSourcesJson, Array<SearchSource>::class.java)?.toList()
                }.getOrNull().orEmpty()
                if (sources.isEmpty()) null else row.id to sources
            }
            .toMap()

    suspend fun listMessagesLocal(conversationId: String, branchId: String): List<MessageDto> {
        val all = listAllMessagesLocal(conversationId)
        val branches = listBranchesLocal(conversationId)
        return buildBranchPathMessages(all, branchId, branches)
    }

    suspend fun listMessages(conversationId: String, branchId: String): List<MessageDto> =
        try {
            val remote = api.listMessages(conversationId, branchId).data
            chatDao.upsertMessages(
                remote.map { dto ->
                    val existing = chatDao.getMessageById(dto.id)
                    dto.toEntity(existing?.searchSourcesJson ?: "[]")
                },
            )
            remote
        } catch (_: Exception) {
            listMessagesLocal(conversationId, branchId)
        }

    suspend fun listAllMessagesLocal(conversationId: String): List<MessageDto> =
        chatDao.getAllMessages(conversationId).map { it.toDto() }

    suspend fun listAllMessages(conversationId: String): List<MessageDto> =
        try {
            val remote = api.listAllMessages(conversationId).data
            chatDao.upsertMessages(remote.map { dto ->
                val existing = chatDao.getMessageById(dto.id)
                dto.toEntity(existing?.searchSourcesJson ?: "[]")
            })
            remote
        } catch (_: Exception) {
            listAllMessagesLocal(conversationId)
        }

    fun streamChat(
        conversationId: String,
        branchId: String,
        parentMessageId: String?,
        content: String,
        options: ChatOptions = ChatOptions(),
    ): Flow<ChatStreamEvent> = flow {
        try {
            val body = api.chatStream(
                body = ChatStreamRequest(
                    conversationId = conversationId,
                    branchId = branchId,
                    parentMessageId = parentMessageId,
                    message = ChatMessagePayload(content = content),
                    options = options,
                ),
            )
            body.source().use { source ->
                parseSseSource(source).forEach { emit(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: EOFException) {
            // 服务端正常关闭 SSE 连接时 OkHttp 可能抛出 EOF，视为流结束
        } catch (e: IOException) {
            emit(ChatStreamEvent.Error(e.message ?: "连接已断开"))
        }
    }.flowOn(Dispatchers.IO)

    fun streamToolResume(
        pendingId: String,
        options: ChatOptions = ChatOptions(enableTools = true),
    ): Flow<ChatStreamEvent> = flow {
        try {
            val body = api.resumeToolStream(
                body = ToolResumeRequest(pendingId = pendingId, options = options),
            )
            body.source().use { source ->
                parseSseSource(source).forEach { emit(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: EOFException) {
        } catch (e: retrofit2.HttpException) {
            val errMsg = e.response()?.errorBody()?.string()?.let { body ->
                runCatching {
                    gson.fromJson(body, com.example.mindshelf.data.remote.dto.ApiErrorBody::class.java)
                        .error?.message
                }.getOrNull()
            }
            emit(ChatStreamEvent.Error(errMsg ?: e.message() ?: "继续对话失败"))
        } catch (e: IOException) {
            emit(ChatStreamEvent.Error(e.message ?: "连接已断开"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun listPendingTools(conversationId: String, branchId: String? = null): List<PendingToolDto> =
        try {
            api.listPendingTools(conversationId, branchId).data
        } catch (_: Exception) {
            emptyList()
        }

    suspend fun upsertToolAction(
        id: String,
        conversationId: String,
        branchId: String,
        anchorMessageId: String?,
        segmentIndex: Int? = null,
        tool: String,
        preview: ToolPreview,
        status: String,
        resultMessage: String? = null,
        errorMessage: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        chatDao.upsertToolActions(
            listOf(
                ToolActionEntity(
                    id = id,
                    conversationId = conversationId,
                    branchId = branchId,
                    anchorMessageId = anchorMessageId,
                    segmentIndex = segmentIndex,
                    tool = tool,
                    previewJson = gson.toJson(preview),
                    status = status,
                    resultMessage = resultMessage,
                    errorMessage = errorMessage,
                    createdAt = createdAt,
                ),
            ),
        )
    }

    suspend fun listToolActions(conversationId: String, branchId: String): List<ToolActionEntity> =
        chatDao.getToolActions(conversationId, branchId)

    private fun parseSseSource(source: BufferedSource): Sequence<ChatStreamEvent> = sequence {
        var eventType = ""
        readSseLines(source).forEach { line ->
            when {
                line.startsWith(":") -> Unit
                line.startsWith("event: ") -> eventType = line.removePrefix("event: ").trim()
                line.startsWith("data: ") -> {
                    val json = line.removePrefix("data: ")
                    parseSseEvent(eventType, json)?.let { yield(it) }
                }
            }
        }
    }

    private fun parseSseEvent(eventType: String, json: String): ChatStreamEvent? = when (eventType) {
        "status" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val phase = obj.get("phase")?.takeIf { !it.isJsonNull }?.asString ?: return null
            val tool = obj.get("tool")?.takeIf { !it.isJsonNull }?.asString
            ChatStreamEvent.Status(phase, tool)
        }
        "reasoning_round_start" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val round = obj.get("round")?.takeIf { !it.isJsonNull }?.asInt ?: 1
            ChatStreamEvent.ReasoningRoundStart(round)
        }
        "reasoning_delta" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val delta = obj.get("content")?.takeIf { !it.isJsonNull }?.asString ?: ""
            if (delta.isNotEmpty()) ChatStreamEvent.ReasoningDelta(delta) else null
        }
        "message_delta" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val delta = obj.get("content")?.takeIf { !it.isJsonNull }?.asString ?: ""
            if (delta.isNotEmpty()) ChatStreamEvent.Delta(delta) else null
        }
        "message_done" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val msgEl = obj.get("message")?.takeIf { !it.isJsonNull } ?: return null
            val msg = gson.fromJson(msgEl, MessageDto::class.java) ?: return null
            ChatStreamEvent.MessageDone(msg)
        }
        "tool_pending" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val pendingId = obj.get("pending_id")?.asString ?: return null
            val tool = obj.get("tool")?.asString ?: return null
            val previewEl = obj.get("preview") ?: return null
            val preview = gson.fromJson(previewEl, ToolPreview::class.java) ?: ToolPreview()
            ChatStreamEvent.ToolPending(pendingId, tool, preview)
        }
        "search_result" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val query = obj.get("query")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val resultsArr = obj.getAsJsonArray("results") ?: return null
            val results = resultsArr.mapNotNull { el ->
                val item = el.asJsonObject
                val title = item.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                val url = item.get("url")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                val snippet = item.get("snippet")?.takeIf { !it.isJsonNull }?.asString ?: ""
                val pageContent = item.get("content")?.takeIf { !it.isJsonNull }?.asString ?: ""
                SearchSource(title = title, url = url, snippet = snippet, content = pageContent)
            }
            if (results.isNotEmpty()) ChatStreamEvent.SearchResult(query, results) else null
        }
        "error" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val errMsg = obj.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "未知错误"
            ChatStreamEvent.Error(errMsg)
        }
        "done" -> {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val convId = obj.get("conversation_id")?.takeIf { !it.isJsonNull }?.asString
            val brId = obj.get("branch_id")?.takeIf { !it.isJsonNull }?.asString
            if (convId != null && brId != null) ChatStreamEvent.Done(convId, brId) else null
        }
        else -> null
    }

    /** 逐行读取 SSE，连接关闭时不抛未捕获异常。 */
    private fun readSseLines(source: BufferedSource): Sequence<String> = sequence {
        while (true) {
            val line = try {
                if (source.exhausted()) break
                source.readUtf8Line()
            } catch (_: EOFException) {
                break
            } ?: break
            yield(line)
        }
    }

    suspend fun confirmTool(pendingId: String, approved: Boolean): ToolConfirmResultDto =
        api.confirmTool(
            com.example.mindshelf.data.remote.dto.ToolConfirmRequest(pendingId, approved),
        ).data
}

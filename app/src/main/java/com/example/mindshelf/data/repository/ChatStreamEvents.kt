package com.example.mindshelf.data.repository

import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.ToolPreview

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
    data class ToolExecuted(
        val tool: String,
        val preview: ToolPreview,
        val result: Map<String, Any?>,
    ) : ChatStreamEvent()
    data class Error(val message: String) : ChatStreamEvent()
    data class Done(val conversationId: String, val branchId: String) : ChatStreamEvent()
    /** 客户端 tool loop 正常结束（无更多 tool_calls）。 */
    data object StreamComplete : ChatStreamEvent()
}

data class SearchSource(
    val title: String,
    val url: String,
    val snippet: String = "",
    val content: String = "",
)

package com.example.mindshelf.ui.chat

import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.ToolPreview

enum class ToolActionStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    REJECTED,
    FAILED,
}

data class ToolActionItem(
    val id: String,
    val conversationId: String,
    val branchId: String,
    val tool: String,
    val preview: ToolPreview,
    val status: ToolActionStatus,
    /** 触发该工具的助手消息 ID，用于在对话流中内联展示 */
    val anchorMessageId: String? = null,
    /** 插入到助手 segments 之间的位置（在该 index 的 segment 之前渲染） */
    val segmentIndex: Int? = null,
    val resultMessage: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

sealed class ChatTimelineItem {
    abstract val sortKey: Long
    abstract val key: String

    data class Message(val message: MessageDto) : ChatTimelineItem() {
        override val sortKey: Long get() = message.createdAt
        override val key: String get() = "msg-${message.id}"
    }

    data class ToolAction(val action: ToolActionItem) : ChatTimelineItem() {
        override val sortKey: Long get() = action.createdAt
        override val key: String get() = "tool-${action.id}"
    }
}

/** 将消息与工具操作按时间线交错排列；锚定在 assistant 上的工具在气泡内渲染，不在此重复。 */
fun buildChatTimeline(
    messages: List<MessageDto>,
    toolActions: List<ToolActionItem>,
): List<ChatTimelineItem> {
    val orderedMessages = messages.distinctBy { it.id }.sortedBy { it.createdAt }
    val assistantIds = orderedMessages.filter { it.role == "assistant" }.map { it.id }.toSet()
    val tools = toolActions
        .distinctBy { it.id }
        .filter { tool ->
            val anchor = resolveAnchorMessageId(tool, orderedMessages)
            anchor == null || anchor !in assistantIds
        }
        .sortedBy { it.createdAt }
    val toolsByAnchor = tools.groupBy { resolveAnchorMessageId(it, orderedMessages) }
    val placedToolIds = mutableSetOf<String>()
    val timeline = mutableListOf<ChatTimelineItem>()

    orderedMessages.forEach { message ->
        timeline.add(ChatTimelineItem.Message(message))
        toolsByAnchor[message.id]?.forEach { tool ->
            timeline.add(ChatTimelineItem.ToolAction(tool))
            placedToolIds.add(tool.id)
        }
    }

    tools.filter { it.id !in placedToolIds }.forEach { tool ->
        val insertIndex = timeline.indexOfLast { item ->
            item is ChatTimelineItem.Message && item.message.createdAt <= tool.createdAt
        }.let { if (it < 0) timeline.size else it + 1 }
        timeline.add(insertIndex, ChatTimelineItem.ToolAction(tool))
    }

    return timeline
}

/** 某条 assistant 消息内联展示的工具卡片（按 segmentIndex 排序）。 */
fun inlineToolsForMessage(
    messageId: String,
    toolActions: List<ToolActionItem>,
): List<ToolActionItem> =
    toolActions
        .filter { it.anchorMessageId == messageId }
        .sortedWith(compareBy({ it.segmentIndex ?: Int.MAX_VALUE }, { it.createdAt }))

private fun resolveAnchorMessageId(tool: ToolActionItem, messages: List<MessageDto>): String? {
    val messageIds = messages.map { it.id }.toSet()
    tool.anchorMessageId?.takeIf { it in messageIds }?.let { return it }
    // 工具锚定在用户消息时，优先挂在对应用户消息之后
    val userBefore = messages
        .filter { it.role == "user" && it.createdAt <= tool.createdAt }
        .maxByOrNull { it.createdAt }
    if (userBefore != null) return userBefore.id
    return messages
        .filter { it.role == "assistant" && it.createdAt <= tool.createdAt }
        .maxByOrNull { it.createdAt }
        ?.id
        ?: messages.lastOrNull { it.role == "assistant" }?.id
}

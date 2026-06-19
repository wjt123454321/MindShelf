package com.example.mindshelf.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val data: T,
    val meta: Meta? = null,
)

data class Meta(
    val page: Int? = null,
    @SerializedName("page_size") val pageSize: Int? = null,
    val total: Int? = null,
)

data class ApiErrorBody(
    val error: ApiError?,
)

data class ApiError(
    val code: String,
    val message: String,
)

data class UserDto(
    val id: String,
    val email: String,
    val username: String?,
    @SerializedName("created_at") val createdAt: Long,
)

data class AuthResultDto(
    val user: UserDto,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Int,
)

data class TokenRefreshResultDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Int,
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val code: String,
    val username: String? = null,
)

data class LoginRequest(
    val account: String,
    val password: String,
)

data class SendCodeRequest(
    val email: String,
    val purpose: String = "login",
)

data class LoginCodeRequest(
    val email: String,
    val code: String,
)

data class NoteDto(
    val id: String,
    val title: String,
    val content: String,
    @SerializedName("knowledge_base_ids") val knowledgeBaseIds: List<String>,
    @SerializedName("sync_version") val syncVersion: Int,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("deleted_at") val deletedAt: Long?,
)

data class CreateNoteRequest(
    val id: String? = null,
    val title: String,
    val content: String,
    @SerializedName("knowledge_base_ids") val knowledgeBaseIds: List<String> = emptyList(),
)

data class UpdateNoteRequest(
    val title: String? = null,
    val content: String? = null,
    @SerializedName("knowledge_base_ids") val knowledgeBaseIds: List<String>? = null,
    @SerializedName("sync_version") val syncVersion: Int? = null,
)

data class KnowledgeBaseDto(
    val id: String,
    val name: String,
    val description: String,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("note_count") val noteCount: Int,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("deleted_at") val deletedAt: Long?,
)

data class CreateKbRequest(
    val id: String? = null,
    val name: String,
    val description: String = "",
    @SerializedName("sort_order") val sortOrder: Int = 0,
)

data class UpdateKbRequest(
    val name: String? = null,
    val description: String? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null,
)

data class ConversationDto(
    val id: String,
    val title: String,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
)

data class BranchDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    val label: String,
    @SerializedName("root_message_id") val rootMessageId: String?,
    @SerializedName("created_at") val createdAt: Long,
)

data class MessageSegment(
    val type: String,
    val text: String,
)

data class MessageDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("branch_id") val branchId: String,
    @SerializedName("parent_id") val parentId: String?,
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val segments: List<MessageSegment>? = null,
    @SerializedName("created_at") val createdAt: Long,
)

data class CreateConversationRequest(
    val id: String? = null,
    val title: String = "新对话",
    @SerializedName("branch_id") val branchId: String? = null,
)

data class CreateBranchRequest(
    val id: String? = null,
    val label: String,
    @SerializedName("fork_from_message_id") val forkFromMessageId: String,
)

data class ChatStreamRequest(
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("branch_id") val branchId: String,
    @SerializedName("parent_message_id") val parentMessageId: String?,
    val message: ChatMessagePayload,
    val options: ChatOptions = ChatOptions(),
)

data class ChatMessagePayload(
    val id: String? = null,
    val content: String,
)

data class ChatOptions(
    @SerializedName("enable_tools") val enableTools: Boolean = false,
    @SerializedName("enable_search") val enableSearch: Boolean = false,
    val model: String? = null,
)

data class ToolConfirmRequest(
    @SerializedName("pending_id") val pendingId: String,
    val approved: Boolean,
)

data class ToolConfirmResultDto(
    val executed: Boolean,
    val result: Map<String, Any>? = null,
    val message: String? = null,
    @SerializedName("pending_id") val pendingId: String? = null,
)

data class PendingToolDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("branch_id") val branchId: String,
    val tool: String,
    val preview: ToolPreview,
    @SerializedName("created_at") val createdAt: Long,
)

data class ToolResumeRequest(
    @SerializedName("pending_id") val pendingId: String,
    val options: ChatOptions = ChatOptions(enableTools = true),
)

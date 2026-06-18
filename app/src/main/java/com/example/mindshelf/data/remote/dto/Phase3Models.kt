package com.example.mindshelf.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NoteVersionDto(
    val id: String,
    @SerializedName("note_id") val noteId: String,
    val title: String,
    val content: String,
    @SerializedName("created_at") val createdAt: Long,
)

data class TrashItemDto(
    @SerializedName("entity_type") val entityType: String,
    val entity: Map<String, Any?>,
    @SerializedName("deleted_at") val deletedAt: Long,
    @SerializedName("expires_at") val expiresAt: Long,
)

data class TrashRestoreRequest(
    @SerializedName("entity_type") val entityType: String,
    val id: String,
)

data class ShareLinkDto(
    val id: String,
    val token: String,
    val url: String,
    @SerializedName("resource_type") val resourceType: String,
    @SerializedName("resource_id") val resourceId: String,
    val revoked: Boolean,
    @SerializedName("created_at") val createdAt: Long,
)

data class CreateShareLinkRequest(
    @SerializedName("resource_type") val resourceType: String,
    @SerializedName("resource_id") val resourceId: String,
)

data class SyncPullData(
    @SerializedName("server_time") val serverTime: Long,
    val notes: List<NoteDto>,
    @SerializedName("knowledge_bases") val knowledgeBases: List<KnowledgeBaseDto>,
    @SerializedName("note_kb_links") val noteKbLinks: List<NoteKbLinkDto>,
    val conversations: List<ConversationDto> = emptyList(),
    val branches: List<BranchDto> = emptyList(),
    val messages: List<MessageDto> = emptyList(),
    val pages: List<CustomPageDto> = emptyList(),
    val tombstones: List<TombstoneDto>,
)

data class NoteKbLinkDto(
    @SerializedName("note_id") val noteId: String,
    @SerializedName("kb_id") val kbId: String,
)

data class TombstoneDto(
    val entity: String,
    val id: String,
    @SerializedName("deleted_at") val deletedAt: Long,
)

data class SyncPushRequest(
    @SerializedName("client_time") val clientTime: Long,
    val notes: List<SyncNotePushItem> = emptyList(),
    @SerializedName("knowledge_bases") val knowledgeBases: List<SyncKbPushItem> = emptyList(),
    val conversations: List<SyncConversationPushItem> = emptyList(),
    val branches: List<SyncBranchPushItem> = emptyList(),
    val messages: List<SyncMessagePushItem> = emptyList(),
    val pages: List<SyncPagePushItem> = emptyList(),
    val deletes: List<TombstoneDto> = emptyList(),
)

data class SyncConversationPushItem(
    val id: String,
    val title: String,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
)

data class SyncBranchPushItem(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    val label: String,
    @SerializedName("root_message_id") val rootMessageId: String? = null,
    @SerializedName("created_at") val createdAt: Long,
)

data class SyncMessagePushItem(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("branch_id") val branchId: String,
    @SerializedName("parent_id") val parentId: String? = null,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val segments: List<MessageSegment> = emptyList(),
    @SerializedName("created_at") val createdAt: Long,
)

data class SyncNotePushItem(
    val id: String,
    val title: String,
    val content: String,
    @SerializedName("knowledge_base_ids") val knowledgeBaseIds: List<String>,
    @SerializedName("sync_version") val syncVersion: Int,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("deleted_at") val deletedAt: Long? = null,
    @SerializedName("base_sync_version") val baseSyncVersion: Int? = null,
    @SerializedName("base_title") val baseTitle: String? = null,
    @SerializedName("base_content") val baseContent: String? = null,
)

data class SyncKbPushItem(
    val id: String,
    val name: String,
    val description: String,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("deleted_at") val deletedAt: Long? = null,
)

data class SyncPushResult(
    @SerializedName("server_time") val serverTime: Long,
    val applied: List<SyncAppliedItem>,
    val conflicts: List<SyncConflict>,
)

data class SyncAppliedItem(
    val entity: String,
    val id: String,
    @SerializedName("sync_version") val syncVersion: Int? = null,
)

data class SyncConflict(
    val entity: String,
    val id: String,
    val base: Map<String, Any?>? = null,
    val local: Map<String, Any?>,
    val remote: Map<String, Any?>,
)

data class SyncResolveRequest(
    val entity: String,
    val id: String,
    val resolution: String,
    val merged: Map<String, Any?>? = null,
    val local: Map<String, Any?>? = null,
)

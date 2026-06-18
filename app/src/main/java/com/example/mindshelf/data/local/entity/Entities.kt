package com.example.mindshelf.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val syncVersion: Int,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
)

@Entity(tableName = "knowledge_bases")
data class KnowledgeBaseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val sortOrder: Int,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
)

@Entity(
    tableName = "note_kb",
    primaryKeys = ["noteId", "kbId"],
)
data class NoteKbCrossRef(
    val noteId: String,
    val kbId: String,
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val label: String,
    val rootMessageId: String?,
    val createdAt: Long,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val branchId: String,
    val parentId: String?,
    val role: String,
    val content: String,
    val reasoning: String = "",
    val segmentsJson: String = "[]",
    val searchSourcesJson: String = "[]",
    val createdAt: Long,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val isDefault: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "tool_actions")
data class ToolActionEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val branchId: String,
    val anchorMessageId: String?,
    val segmentIndex: Int? = null,
    val tool: String,
    val previewJson: String,
    val status: String,
    val resultMessage: String?,
    val errorMessage: String?,
    val createdAt: Long,
)

@Entity(tableName = "custom_pages")
data class CustomPageEntity(
    @PrimaryKey val id: String,
    val name: String,
    val schemaJson: String,
    val dataBindings: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: SyncStatus,
)

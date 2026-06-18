package com.example.mindshelf.data.local

import com.example.mindshelf.data.local.entity.BranchEntity
import com.example.mindshelf.data.local.entity.ConversationEntity
import com.example.mindshelf.data.local.entity.KnowledgeBaseEntity
import com.example.mindshelf.data.local.entity.MessageEntity
import com.example.mindshelf.data.local.entity.NoteEntity
import com.example.mindshelf.data.local.entity.NoteKbCrossRef
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.remote.dto.BranchDto
import com.example.mindshelf.data.remote.dto.ConversationDto
import com.example.mindshelf.data.remote.dto.KnowledgeBaseDto
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.MessageSegment
import com.example.mindshelf.data.remote.dto.NoteDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

fun NoteDto.toEntity(status: SyncStatus = SyncStatus.SYNCED) = NoteEntity(
    id = id,
    title = title,
    content = content,
    syncVersion = syncVersion,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = status,
)

fun NoteEntity.toDto(kbIds: List<String>) = NoteDto(
    id = id,
    title = title,
    content = content,
    knowledgeBaseIds = kbIds,
    syncVersion = syncVersion,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun KnowledgeBaseDto.toEntity(status: SyncStatus = SyncStatus.SYNCED) = KnowledgeBaseEntity(
    id = id,
    name = name,
    description = description,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = status,
)

fun KnowledgeBaseEntity.toDto(noteCount: Int) = KnowledgeBaseDto(
    id = id,
    name = name,
    description = description,
    sortOrder = sortOrder,
    noteCount = noteCount,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun ConversationDto.toEntity(status: SyncStatus = SyncStatus.SYNCED) = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = status,
)

fun ConversationEntity.toDto() = ConversationDto(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BranchDto.toEntity(status: SyncStatus = SyncStatus.SYNCED) = BranchEntity(
    id = id,
    conversationId = conversationId,
    label = label,
    rootMessageId = rootMessageId,
    createdAt = createdAt,
    syncStatus = status,
)

private val segmentListType = object : TypeToken<List<MessageSegment>>() {}.type
private val gson = Gson()

private fun segmentsToJson(segments: List<MessageSegment>?): String =
    gson.toJson(segments.orEmpty())

private fun segmentsFromJson(json: String): List<MessageSegment>? {
    if (json.isBlank() || json == "[]") return null
    return runCatching { gson.fromJson<List<MessageSegment>>(json, segmentListType) }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
}

fun MessageDto.toEntity(
    searchSourcesJson: String = "[]",
    status: SyncStatus = SyncStatus.SYNCED,
) = MessageEntity(
    id = id,
    conversationId = conversationId,
    branchId = branchId,
    parentId = parentId,
    role = role,
    content = content,
    reasoning = reasoning.orEmpty(),
    segmentsJson = segmentsToJson(segments),
    searchSourcesJson = searchSourcesJson,
    createdAt = createdAt,
    syncStatus = status,
)

fun MessageEntity.toDto() = MessageDto(
    id = id,
    conversationId = conversationId,
    branchId = branchId,
    parentId = parentId,
    role = role,
    content = content,
    reasoning = reasoning.takeIf { it.isNotBlank() },
    segments = segmentsFromJson(segmentsJson),
    createdAt = createdAt,
)

fun kbLinks(noteId: String, kbIds: List<String>) =
    kbIds.map { NoteKbCrossRef(noteId, it) }

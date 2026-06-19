package com.example.mindshelf.data.repository

import com.example.mindshelf.data.chat.BranchDeduplicator
import com.example.mindshelf.data.local.SyncPreferences
import com.example.mindshelf.data.local.dao.ChatDao
import com.example.mindshelf.data.local.dao.KnowledgeBaseDao
import com.example.mindshelf.data.local.dao.NoteDao
import com.example.mindshelf.data.local.dao.NoteKbDao
import com.example.mindshelf.data.local.dao.PageDao
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.local.kbLinks
import com.example.mindshelf.data.local.toDto
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.SyncBranchPushItem
import com.example.mindshelf.data.remote.dto.SyncConflict
import com.example.mindshelf.data.remote.dto.SyncConversationPushItem
import com.example.mindshelf.data.remote.dto.SyncKbPushItem
import com.example.mindshelf.data.remote.dto.SyncMessagePushItem
import com.example.mindshelf.data.remote.dto.SyncNotePushItem
import com.example.mindshelf.data.remote.dto.SyncPagePushItem
import com.example.mindshelf.data.remote.dto.SyncPushRequest
import com.example.mindshelf.data.remote.dto.SyncResolveRequest
import com.example.mindshelf.data.remote.dto.TombstoneDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val applied: Int = 0,
    val conflicts: List<SyncConflict> = emptyList(),
)

/** 双向云同步：Pull → Push，冲突时返回待解决列表。 */
@Singleton
class SyncEngine @Inject constructor(
    private val api: MindShelfApi,
    private val syncPreferences: SyncPreferences,
    private val noteDao: NoteDao,
    private val kbDao: KnowledgeBaseDao,
    private val noteKbDao: NoteKbDao,
    private val chatDao: ChatDao,
    private val pageDao: PageDao,
    private val branchDeduplicator: BranchDeduplicator,
) {
    private val _conflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val conflicts: StateFlow<List<SyncConflict>> = _conflicts.asStateFlow()

    suspend fun sync(): SyncResult {
        if (!syncPreferences.isCloudSyncEnabled()) return SyncResult()

        val since = syncPreferences.getLastSyncedAt()
        val pull = api.syncPull(since).data

        for (note in pull.notes) {
            if (note.deletedAt != null) {
                noteDao.markDeleted(note.id, note.deletedAt, note.updatedAt, SyncStatus.SYNCED)
            } else {
                noteDao.upsert(note.toEntity(SyncStatus.SYNCED))
            }
        }
        for (kb in pull.knowledgeBases) {
            if (kb.deletedAt != null) {
                kbDao.markDeleted(kb.id, kb.deletedAt, kb.updatedAt, SyncStatus.SYNCED)
            } else {
                kbDao.upsert(kb.toEntity(SyncStatus.SYNCED))
            }
        }
        for (link in pull.noteKbLinks) {
            noteKbDao.insertAll(kbLinks(link.noteId, listOf(link.kbId)))
        }
        for (tomb in pull.tombstones) {
            when (tomb.entity) {
                "note" -> noteDao.markDeleted(tomb.id, tomb.deletedAt, tomb.deletedAt, SyncStatus.SYNCED)
                "knowledge_base" -> kbDao.markDeleted(tomb.id, tomb.deletedAt, tomb.deletedAt, SyncStatus.SYNCED)
                "page" -> pageDao.markDeleted(tomb.id, tomb.deletedAt, tomb.deletedAt, SyncStatus.SYNCED)
                "conversation" -> {
                    chatDao.deleteToolActions(tomb.id)
                    chatDao.deleteAllMessages(tomb.id)
                    chatDao.deleteBranches(tomb.id)
                    chatDao.deleteConversation(tomb.id)
                }
            }
        }

        for (conv in pull.conversations) {
            chatDao.upsertConversations(listOf(conv.toEntity(SyncStatus.SYNCED)))
        }
        for (branch in pull.branches) {
            chatDao.upsertBranches(listOf(branch.toEntity(SyncStatus.SYNCED)))
        }
        val convIdsToDedupe = (pull.conversations.map { it.id } + pull.branches.map { it.conversationId })
            .distinct()
        for (convId in convIdsToDedupe) {
            branchDeduplicator.deduplicateMainBranches(convId)
        }
        for (msg in pull.messages) {
            val existing = chatDao.getMessageById(msg.id)
            chatDao.upsertMessages(
                listOf(msg.toEntity(existing?.searchSourcesJson ?: "[]", SyncStatus.SYNCED)),
            )
        }
        for (page in pull.pages) {
            if (page.deletedAt != null) {
                pageDao.markDeleted(page.id, page.deletedAt, page.updatedAt, SyncStatus.SYNCED)
            } else {
                if (page.pinned) pageDao.clearOtherPins(page.id)
                pageDao.upsert(page.toEntity(SyncStatus.SYNCED))
            }
        }

        val pendingNotes = noteDao.getPendingSync()
        val pendingDeletes = noteDao.getPendingDeletes()
        val pendingKbs = kbDao.getPendingSync()
        val pendingKbDeletes = kbDao.getPendingDeletes()
        val pendingPages = pageDao.getPendingSync()
        val pendingPageDeletes = pageDao.getPendingDeletes()

        val pushNotes = pendingNotes.map { entity ->
            val kbIds = noteKbDao.getKbIdsForNote(entity.id)
            SyncNotePushItem(
                id = entity.id,
                title = entity.title,
                content = entity.content,
                knowledgeBaseIds = kbIds,
                syncVersion = entity.syncVersion,
                updatedAt = entity.updatedAt,
            )
        }
        val pushDeletes = buildList {
            pendingDeletes.forEach { entity ->
                add(TombstoneDto("note", entity.id, entity.deletedAt ?: System.currentTimeMillis()))
            }
            pendingKbDeletes.forEach { entity ->
                add(TombstoneDto("knowledge_base", entity.id, entity.deletedAt ?: System.currentTimeMillis()))
            }
            pendingPageDeletes.forEach { entity ->
                add(TombstoneDto("page", entity.id, entity.deletedAt ?: System.currentTimeMillis()))
            }
        }
        val pushKbs = pendingKbs.map { entity ->
            SyncKbPushItem(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                sortOrder = entity.sortOrder,
                updatedAt = entity.updatedAt,
            )
        }
        val pushConversations = chatDao.getPendingConversations().map { entity ->
            SyncConversationPushItem(
                id = entity.id,
                title = entity.title,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }
        chatDao.getPendingBranches()
            .map { it.conversationId }
            .distinct()
            .forEach { convId -> branchDeduplicator.deduplicateMainBranches(convId) }
        val pushBranches = chatDao.getPendingBranches().map { entity ->
            SyncBranchPushItem(
                id = entity.id,
                conversationId = entity.conversationId,
                label = entity.label,
                rootMessageId = entity.rootMessageId,
                createdAt = entity.createdAt,
            )
        }
        val pushMessages = chatDao.getPendingMessages().map { entity ->
            val dto = entity.toDto()
            SyncMessagePushItem(
                id = entity.id,
                conversationId = entity.conversationId,
                branchId = entity.branchId,
                parentId = entity.parentId,
                role = entity.role,
                content = entity.content,
                reasoning = entity.reasoning,
                segments = dto.segments.orEmpty(),
                createdAt = entity.createdAt,
            )
        }
        val pushPages = pendingPages.map { entity ->
            val dto = entity.toDto()
            SyncPagePushItem(
                id = entity.id,
                name = entity.name,
                schemaJson = dto.schemaJson,
                dataBindings = dto.dataBindings,
                pinned = entity.pinned,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }

        val pushResult = api.syncPush(
            SyncPushRequest(
                clientTime = System.currentTimeMillis(),
                notes = pushNotes,
                knowledgeBases = pushKbs,
                conversations = pushConversations,
                branches = pushBranches,
                messages = pushMessages,
                pages = pushPages,
                deletes = pushDeletes,
            ),
        ).data

        for (applied in pushResult.applied) {
            when (applied.entity) {
                "note" -> {
                    val entity = noteDao.getByIdIncludingDeleted(applied.id) ?: continue
                    noteDao.upsert(
                        entity.copy(
                            syncVersion = applied.syncVersion ?: entity.syncVersion,
                            syncStatus = SyncStatus.SYNCED,
                        ),
                    )
                }
                "knowledge_base" -> {
                    val entity = kbDao.getByIdIncludingDeleted(applied.id) ?: continue
                    kbDao.upsert(entity.copy(syncStatus = SyncStatus.SYNCED))
                }
                "conversation" -> {
                    val entity = chatDao.getConversation(applied.id) ?: continue
                    chatDao.upsertConversations(listOf(entity.copy(syncStatus = SyncStatus.SYNCED)))
                }
                "branch" -> {
                    val entity = chatDao.getBranch(applied.id) ?: continue
                    chatDao.upsertBranches(listOf(entity.copy(syncStatus = SyncStatus.SYNCED)))
                }
                "message" -> {
                    val entity = chatDao.getMessageById(applied.id) ?: continue
                    chatDao.upsertMessages(listOf(entity.copy(syncStatus = SyncStatus.SYNCED)))
                }
                "page" -> {
                    val entity = pageDao.getByIdIncludingDeleted(applied.id) ?: continue
                    pageDao.upsert(entity.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }

        _conflicts.value = pushResult.conflicts
        syncPreferences.setLastSyncedAt(pull.serverTime)
        return SyncResult(applied = pushResult.applied.size, conflicts = pushResult.conflicts)
    }

    suspend fun resolveConflict(conflict: SyncConflict, resolution: String, merged: Map<String, Any?>? = null) {
        api.syncResolve(
            SyncResolveRequest(
                entity = conflict.entity,
                id = conflict.id,
                resolution = resolution,
                merged = merged,
                local = conflict.local,
            ),
        )
        _conflicts.value = _conflicts.value.filterNot { it.id == conflict.id && it.entity == conflict.entity }
        sync()
    }

    suspend fun clearConflicts() {
        _conflicts.value = emptyList()
    }
}

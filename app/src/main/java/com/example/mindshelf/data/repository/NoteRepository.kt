package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.dao.NoteDao
import com.example.mindshelf.data.local.dao.NoteKbDao
import com.example.mindshelf.data.local.entity.NoteEntity
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.local.kbLinks
import com.example.mindshelf.data.local.toDto
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.CreateNoteRequest
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.data.remote.dto.UpdateNoteRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val api: MindShelfApi,
    private val noteDao: NoteDao,
    private val noteKbDao: NoteKbDao,
) {
    fun observeNotes(): Flow<List<NoteDto>> =
        combine(noteDao.observeActive(), noteKbDao.observeAll()) { notes, links ->
            notes.map { note ->
                val kbIds = links.filter { it.noteId == note.id }.map { it.kbId }
                note.toDto(kbIds)
            }
        }

    suspend fun get(id: String): NoteDto? {
        val entity = noteDao.getById(id) ?: return try {
            api.getNote(id).data.also { saveRemote(it) }
        } catch (_: Exception) {
            null
        }
        return entity.toDto(noteKbDao.getKbIdsForNote(id))
    }

    suspend fun create(title: String, content: String, kbIds: List<String> = emptyList()): NoteDto {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = NoteEntity(id, title, content, 1, now, null, SyncStatus.PENDING_CREATE)
        noteDao.upsert(entity)
        noteKbDao.deleteForNote(id)
        noteKbDao.insertAll(kbLinks(id, kbIds))
        pushCreate(entity, kbIds)
        return noteDao.getById(id)?.toDto(kbIds) ?: entity.toDto(kbIds)
    }

    suspend fun update(
        id: String,
        title: String,
        content: String,
        syncVersion: Int,
        kbIds: List<String>,
    ): NoteDto {
        val now = System.currentTimeMillis()
        val entity = (noteDao.getById(id) ?: NoteEntity(id, title, content, syncVersion, now, null, SyncStatus.PENDING_UPDATE))
            .copy(title = title, content = content, syncVersion = syncVersion, updatedAt = now, syncStatus = SyncStatus.PENDING_UPDATE)
        noteDao.upsert(entity)
        noteKbDao.deleteForNote(id)
        noteKbDao.insertAll(kbLinks(id, kbIds))
        pushUpdate(entity, kbIds)
        return noteDao.getById(id)?.toDto(kbIds) ?: entity.toDto(kbIds)
    }

    suspend fun delete(id: String) {
        val now = System.currentTimeMillis()
        noteDao.markDeleted(id, now, now, SyncStatus.PENDING_DELETE)
        try {
            api.deleteNote(id)
        } catch (_: Exception) {
            // 离线时保留本地软删除，后续 Phase 3 同步
        }
    }

    suspend fun deleteAll(ids: List<String>) {
        ids.forEach { delete(it) }
    }

    suspend fun getNotesForKb(kbId: String): List<NoteDto> =
        noteKbDao.getNoteIdsForKb(kbId).mapNotNull { get(it) }

    suspend fun syncAllPending(): Int {
        val pending = noteDao.getPendingSync()
        var synced = 0
        for (entity in pending) {
            if (ensureSyncedOnServer(entity.id)) synced++
        }
        return synced
    }

    suspend fun ensureSyncedOnServer(id: String): Boolean {
        val entity = noteDao.getById(id)
        if (entity == null) {
            return fetchAndSaveRemote(id)
        }
        if (entity.syncStatus == SyncStatus.SYNCED) return true

        val kbIds = noteKbDao.getKbIdsForNote(id)
        val pushed = when (entity.syncStatus) {
            SyncStatus.PENDING_CREATE -> pushCreate(entity, kbIds)
            SyncStatus.PENDING_UPDATE -> pushUpdate(entity, kbIds)
            SyncStatus.PENDING_DELETE -> return false
            SyncStatus.SYNCED -> return true
        }
        if (pushed) return true

        return fetchAndSaveRemote(id)
    }

    suspend fun applyFromToolResult(result: Map<String, Any>) {
        val noteId = result["note_id"]?.toString() ?: return
        val title = result["title"]?.toString() ?: return
        val content = result["content"]?.toString() ?: return
        val syncVersion = (result["sync_version"] as? Number)?.toInt() ?: 1
        val updatedAt = (result["updated_at"] as? Number)?.toLong() ?: System.currentTimeMillis()
        @Suppress("UNCHECKED_CAST")
        val kbIds = resolveKnowledgeBaseIds(noteId, result)
        saveRemote(
            NoteDto(
                id = noteId,
                title = title,
                content = content,
                knowledgeBaseIds = kbIds,
                syncVersion = syncVersion,
                createdAt = noteDao.getById(noteId)?.updatedAt ?: updatedAt,
                updatedAt = updatedAt,
                deletedAt = null,
            ),
        )
    }

    suspend fun refreshFromServer() {
        try {
            api.listNotes().data.forEach { saveRemote(it) }
        } catch (_: Exception) {
        }
    }

    suspend fun markDeletedFromServer(id: String) {
        val now = System.currentTimeMillis()
        noteDao.markDeleted(id, now, now, SyncStatus.SYNCED)
    }

    private suspend fun pushCreate(entity: NoteEntity, kbIds: List<String>): Boolean {
        return try {
            val remote = api.createNote(
                CreateNoteRequest(entity.id, entity.title, entity.content, kbIds),
            ).data
            saveRemote(remote)
            true
        } catch (e: HttpException) {
            fetchAndSaveRemote(entity.id)
        } catch (_: Exception) {
            fetchAndSaveRemote(entity.id)
        }
    }

    private suspend fun pushUpdate(entity: NoteEntity, kbIds: List<String>): Boolean {
        return try {
            val remote = api.updateNote(
                entity.id,
                UpdateNoteRequest(entity.title, entity.content, kbIds, entity.syncVersion),
            ).data
            saveRemote(remote)
            true
        } catch (_: HttpException) {
            fetchAndSaveRemote(entity.id)
        } catch (_: Exception) {
            fetchAndSaveRemote(entity.id)
        }
    }

    private suspend fun fetchAndSaveRemote(id: String): Boolean =
        runCatching {
            api.getNote(id).data.also { saveRemote(it) }
            true
        }.getOrDefault(false)

    private suspend fun resolveKnowledgeBaseIds(noteId: String, result: Map<String, Any>): List<String> {
        val raw = result["knowledge_base_ids"] ?: return noteKbDao.getKbIdsForNote(noteId)
        val parsed = when (raw) {
            is List<*> -> raw.mapNotNull { it?.toString() }
            else -> return noteKbDao.getKbIdsForNote(noteId)
        }
        if (parsed.isEmpty()) {
            val existing = noteKbDao.getKbIdsForNote(noteId)
            if (existing.isNotEmpty()) return existing
        }
        return parsed
    }

    private suspend fun saveRemote(dto: NoteDto) {
        noteDao.upsert(dto.toEntity(SyncStatus.SYNCED))
        noteKbDao.deleteForNote(dto.id)
        noteKbDao.insertAll(kbLinks(dto.id, dto.knowledgeBaseIds))
    }
}

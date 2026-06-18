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
import com.example.mindshelf.data.sync.SyncCoordinator
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
    private val syncCoordinator: SyncCoordinator,
) {
    fun observeNotes(): Flow<List<NoteDto>> =
        combine(noteDao.observeActive(), noteKbDao.observeAll()) { notes, links ->
            notes.map { note ->
                val kbIds = links.filter { it.noteId == note.id }.map { it.kbId }
                note.toDto(kbIds)
            }
        }

    suspend fun listActiveNotes(): List<NoteDto> =
        noteDao.getAllActive().map { note ->
            note.toDto(noteKbDao.getKbIdsForNote(note.id))
        }

    suspend fun searchNotes(query: String, kbId: String? = null, noteId: String? = null): Map<String, Any?> {
        if (!noteId.isNullOrBlank()) {
            val note = noteDao.getById(noteId) ?: return mapOf("error" to "笔记不存在")
            return mapOf(
                "note" to mapOf(
                    "id" to note.id,
                    "title" to note.title,
                    "content" to note.content,
                    "knowledge_base_ids" to noteKbDao.getKbIdsForNote(note.id),
                ),
            )
        }
        var notes = noteDao.getAllActive()
        if (!kbId.isNullOrBlank()) {
            val ids = noteKbDao.getNoteIdsForKb(kbId).toSet()
            notes = notes.filter { it.id in ids }
        }
        val q = query.trim()
        if (q.isNotEmpty()) {
            notes = notes.filter {
                it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
            }
        }
        return mapOf(
            "items" to notes.take(20).map {
                mapOf("id" to it.id, "title" to it.title, "snippet" to it.content.take(200))
            },
        )
    }

    suspend fun get(id: String): NoteDto? {
        val entity = noteDao.getById(id) ?: return if (syncCoordinator.shouldWriteRemote()) {
            try {
                api.getNote(id).data.also { saveRemote(it) }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        return entity.toDto(noteKbDao.getKbIdsForNote(id))
    }

    suspend fun create(
        title: String,
        content: String,
        kbIds: List<String> = emptyList(),
        id: String? = null,
    ): NoteDto {
        val noteId = id ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = NoteEntity(noteId, title, content, 1, now, null, SyncStatus.PENDING_CREATE)
        noteDao.upsert(entity)
        noteKbDao.deleteForNote(noteId)
        noteKbDao.insertAll(kbLinks(noteId, kbIds))
        if (syncCoordinator.shouldWriteRemote()) {
            pushCreate(entity, kbIds)
        }
        return noteDao.getById(noteId)?.toDto(kbIds) ?: entity.toDto(kbIds)
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
        if (syncCoordinator.shouldWriteRemote()) {
            pushUpdate(entity, kbIds)
        }
        return noteDao.getById(id)?.toDto(kbIds) ?: entity.toDto(kbIds)
    }

    suspend fun delete(id: String) {
        val now = System.currentTimeMillis()
        noteDao.markDeleted(id, now, now, SyncStatus.PENDING_DELETE)
        if (syncCoordinator.shouldWriteRemote()) {
            try {
                api.deleteNote(id)
                noteDao.markDeleted(id, now, now, SyncStatus.SYNCED)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun deleteAll(ids: List<String>) {
        ids.forEach { delete(it) }
    }

    suspend fun getNotesForKb(kbId: String): List<NoteDto> =
        noteKbDao.getNoteIdsForKb(kbId).mapNotNull { get(it) }

    suspend fun syncAllPending(): Int {
        if (!syncCoordinator.shouldWriteRemote()) return 0
        var synced = 0
        for (entity in noteDao.getPendingSync()) {
            if (ensureSyncedOnServer(entity.id)) synced++
        }
        return synced
    }

    suspend fun syncPendingDeletes(): Int {
        if (!syncCoordinator.shouldWriteRemote()) return 0
        var synced = 0
        for (entity in noteDao.getPendingDeletes()) {
            try {
                api.deleteNote(entity.id)
                noteDao.markDeleted(entity.id, entity.deletedAt ?: System.currentTimeMillis(), entity.updatedAt, SyncStatus.SYNCED)
                synced++
            } catch (_: Exception) {
            }
        }
        return synced
    }

    suspend fun ensureSyncedOnServer(id: String): Boolean {
        if (!syncCoordinator.shouldWriteRemote()) return false
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
        val kbIds = resolveKnowledgeBaseIds(noteId, result)
        val status = if (syncCoordinator.shouldWriteRemote()) SyncStatus.SYNCED else SyncStatus.PENDING_UPDATE
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
            status,
        )
    }

    suspend fun refreshFromServer() {
        if (!syncCoordinator.shouldWriteRemote()) return
        try {
            api.listNotes().data.forEach { saveRemote(it) }
        } catch (_: Exception) {
        }
    }

    suspend fun markDeletedFromServer(id: String) {
        val now = System.currentTimeMillis()
        noteDao.markDeleted(id, now, now, SyncStatus.SYNCED)
    }

    suspend fun restoreFromTrash(id: String) {
        val now = System.currentTimeMillis()
        val status = if (syncCoordinator.shouldWriteRemote()) SyncStatus.SYNCED else SyncStatus.PENDING_UPDATE
        noteDao.restore(id, now, status)
        if (syncCoordinator.shouldWriteRemote()) {
            runCatching {
                api.restoreTrash(
                    com.example.mindshelf.data.remote.dto.TrashRestoreRequest("note", id),
                )
            }
        }
    }

    suspend fun purgeLocal(id: String) {
        noteKbDao.deleteForNote(id)
        noteDao.purge(id)
    }

    suspend fun saveFromRemote(dto: NoteDto) {
        saveRemote(dto)
    }

    suspend fun countPending(): Int =
        noteDao.getPendingSync().size + noteDao.getPendingDeletes().size

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

    private suspend fun saveRemote(dto: NoteDto, status: SyncStatus = SyncStatus.SYNCED) {
        noteDao.upsert(dto.toEntity(status))
        noteKbDao.deleteForNote(dto.id)
        noteKbDao.insertAll(kbLinks(dto.id, dto.knowledgeBaseIds))
    }
}

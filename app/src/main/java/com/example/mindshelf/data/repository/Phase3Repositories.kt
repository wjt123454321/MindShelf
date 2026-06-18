package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.dao.KnowledgeBaseDao
import com.example.mindshelf.data.local.dao.NoteDao
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.TrashItemDto
import com.example.mindshelf.data.remote.dto.TrashRestoreRequest
import com.example.mindshelf.data.sync.SyncCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepository @Inject constructor(
    private val api: MindShelfApi,
    private val noteDao: NoteDao,
    private val kbDao: KnowledgeBaseDao,
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val syncCoordinator: SyncCoordinator,
) {
    suspend fun listTrash(): List<TrashItemDto> {
        if (syncCoordinator.shouldWriteRemote()) {
            return runCatching { api.listTrash().data }.getOrElse { listLocalTrash() }
        }
        return listLocalTrash()
    }

    suspend fun restore(entityType: String, id: String) {
        when (entityType) {
            "note" -> noteRepository.restoreFromTrash(id)
            "knowledge_base" -> knowledgeRepository.restoreFromTrash(id)
        }
    }

    suspend fun purge(entityType: String, id: String) {
        if (syncCoordinator.shouldWriteRemote()) {
            runCatching { api.purgeTrash(entityType, id) }
        }
        when (entityType) {
            "note" -> noteRepository.purgeLocal(id)
            "knowledge_base" -> knowledgeRepository.purgeLocal(id)
        }
    }

    private suspend fun listLocalTrash(): List<TrashItemDto> {
        val retentionMs = 30L * 24 * 60 * 60 * 1000
        val items = mutableListOf<TrashItemDto>()
        for (note in noteDao.getDeleted()) {
            val deletedAt = note.deletedAt ?: continue
            items.add(
                TrashItemDto(
                    entityType = "note",
                    entity = mapOf(
                        "id" to note.id,
                        "title" to note.title,
                        "content" to note.content,
                    ),
                    deletedAt = deletedAt,
                    expiresAt = deletedAt + retentionMs,
                ),
            )
        }
        for (kb in kbDao.getDeleted()) {
            val deletedAt = kb.deletedAt ?: continue
            items.add(
                TrashItemDto(
                    entityType = "knowledge_base",
                    entity = mapOf(
                        "id" to kb.id,
                        "name" to kb.name,
                        "description" to kb.description,
                    ),
                    deletedAt = deletedAt,
                    expiresAt = deletedAt + retentionMs,
                ),
            )
        }
        return items.sortedByDescending { it.deletedAt }
    }
}

@Singleton
class VersionRepository @Inject constructor(
    private val api: MindShelfApi,
    private val noteRepository: NoteRepository,
    private val syncCoordinator: SyncCoordinator,
) {
    suspend fun listVersions(noteId: String): List<com.example.mindshelf.data.remote.dto.NoteVersionDto> {
        if (!syncCoordinator.shouldWriteRemote()) return emptyList()
        return api.listNoteVersions(noteId).data
    }

    suspend fun restoreVersion(noteId: String, versionId: String) {
        if (!syncCoordinator.shouldWriteRemote()) return
        val note = api.restoreNoteVersion(noteId, versionId).data
        noteRepository.saveFromRemote(note)
    }
}

@Singleton
class ShareRepository @Inject constructor(
    private val api: MindShelfApi,
    private val syncCoordinator: SyncCoordinator,
) {
    suspend fun createLink(resourceType: String, resourceId: String): com.example.mindshelf.data.remote.dto.ShareLinkDto {
        if (!syncCoordinator.shouldWriteRemote()) {
            error("分享需要网络且开启云同步")
        }
        return api.createShareLink(
            com.example.mindshelf.data.remote.dto.CreateShareLinkRequest(resourceType, resourceId),
        ).data
    }

    suspend fun listLinks(): List<com.example.mindshelf.data.remote.dto.ShareLinkDto> =
        if (syncCoordinator.shouldWriteRemote()) api.listShareLinks().data else emptyList()

    suspend fun revoke(linkId: String) {
        if (syncCoordinator.shouldWriteRemote()) api.revokeShareLink(linkId)
    }
}

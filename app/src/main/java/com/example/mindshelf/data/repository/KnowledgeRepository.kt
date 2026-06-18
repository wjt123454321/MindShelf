package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.dao.KnowledgeBaseDao
import com.example.mindshelf.data.local.dao.NoteKbDao
import com.example.mindshelf.data.local.entity.KnowledgeBaseEntity
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.local.toDto
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.CreateKbRequest
import com.example.mindshelf.data.remote.dto.KnowledgeBaseDto
import com.example.mindshelf.data.remote.dto.TrashRestoreRequest
import com.example.mindshelf.data.remote.dto.UpdateKbRequest
import com.example.mindshelf.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRepository @Inject constructor(
    private val api: MindShelfApi,
    private val kbDao: KnowledgeBaseDao,
    private val noteKbDao: NoteKbDao,
    private val syncCoordinator: SyncCoordinator,
) {
    fun observeKnowledgeBases(): Flow<List<KnowledgeBaseDto>> =
        combine(kbDao.observeActive(), noteKbDao.observeAll()) { kbs, links ->
            kbs.map { kb ->
                val count = links.count { it.kbId == kb.id }
                kb.toDto(count)
            }
        }

    suspend fun listActiveKnowledgeBases(): List<KnowledgeBaseDto> =
        kbDao.getAllActive().map { kb ->
            kb.toDto(noteKbDao.getNoteIdsForKb(kb.id).size)
        }

    suspend fun searchKnowledgeBases(query: String): Map<String, Any?> {
        var items = kbDao.getAllActive()
        val q = query.trim()
        if (q.isNotEmpty()) {
            items = items.filter {
                it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
            }
        }
        return mapOf(
            "items" to items.take(20).map {
                mapOf("id" to it.id, "name" to it.name, "description" to it.description)
            },
        )
    }

    suspend fun create(name: String, id: String? = null, description: String = ""): KnowledgeBaseDto {
        val kbId = id ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = KnowledgeBaseEntity(kbId, name, description, 0, now, null, SyncStatus.PENDING_CREATE)
        kbDao.upsert(entity)
        if (syncCoordinator.shouldWriteRemote()) {
            return try {
                val remote = api.createKnowledgeBase(CreateKbRequest(kbId, name, description)).data
                kbDao.upsert(remote.toEntity())
                remote
            } catch (_: Exception) {
                entity.toDto(0)
            }
        }
        return entity.toDto(0)
    }

    suspend fun update(id: String, name: String, description: String): KnowledgeBaseDto {
        val now = System.currentTimeMillis()
        val existing = kbDao.getById(id)
        val entity = (existing ?: KnowledgeBaseEntity(id, name, description, 0, now, null, SyncStatus.PENDING_UPDATE))
            .copy(name = name, description = description, updatedAt = now, syncStatus = SyncStatus.PENDING_UPDATE)
        kbDao.upsert(entity)
        val noteCount = noteKbDao.getNoteIdsForKb(id).size
        if (syncCoordinator.shouldWriteRemote()) {
            return try {
                val remote = api.updateKnowledgeBase(id, UpdateKbRequest(name, description)).data
                kbDao.upsert(remote.toEntity())
                remote.copy(noteCount = noteCount)
            } catch (_: Exception) {
                entity.toDto(noteCount)
            }
        }
        return entity.toDto(noteCount)
    }

    suspend fun delete(id: String) {
        val now = System.currentTimeMillis()
        kbDao.markDeleted(id, now, now, SyncStatus.PENDING_DELETE)
        if (syncCoordinator.shouldWriteRemote()) {
            try {
                api.deleteKnowledgeBase(id)
                kbDao.markDeleted(id, now, now, SyncStatus.SYNCED)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun refreshFromServer() {
        if (!syncCoordinator.shouldWriteRemote()) return
        try {
            api.listKnowledgeBases().data.forEach { kbDao.upsert(it.toEntity()) }
        } catch (_: Exception) {
        }
    }

    suspend fun markDeletedFromServer(id: String) {
        val now = System.currentTimeMillis()
        kbDao.markDeleted(id, now, now, SyncStatus.SYNCED)
    }

    suspend fun restoreFromTrash(id: String) {
        val now = System.currentTimeMillis()
        val status = if (syncCoordinator.shouldWriteRemote()) SyncStatus.SYNCED else SyncStatus.PENDING_UPDATE
        kbDao.restore(id, now, status)
        if (syncCoordinator.shouldWriteRemote()) {
            runCatching { api.restoreTrash(TrashRestoreRequest("knowledge_base", id)) }
        }
    }

    suspend fun purgeLocal(id: String) {
        kbDao.purge(id)
    }

    suspend fun syncAllPending(): Int {
        if (!syncCoordinator.shouldWriteRemote()) return 0
        var synced = 0
        for (entity in kbDao.getPendingSync()) {
            if (ensureSyncedOnServer(entity.id)) synced++
        }
        return synced
    }

    suspend fun syncPendingDeletes(): Int {
        if (!syncCoordinator.shouldWriteRemote()) return 0
        var synced = 0
        for (entity in kbDao.getPendingDeletes()) {
            try {
                api.deleteKnowledgeBase(entity.id)
                kbDao.markDeleted(entity.id, entity.deletedAt ?: System.currentTimeMillis(), entity.updatedAt, SyncStatus.SYNCED)
                synced++
            } catch (_: Exception) {
            }
        }
        return synced
    }

    suspend fun ensureSyncedOnServer(id: String): Boolean {
        if (!syncCoordinator.shouldWriteRemote()) return false
        val entity = kbDao.getById(id) ?: return runCatching {
            api.listKnowledgeBases().data.find { it.id == id }?.let { kbDao.upsert(it.toEntity()) }
        }.isSuccess
        if (entity.syncStatus == SyncStatus.SYNCED) return true
        return when (entity.syncStatus) {
            SyncStatus.PENDING_CREATE -> {
                runCatching {
                    api.createKnowledgeBase(CreateKbRequest(entity.id, entity.name)).data
                }.onSuccess { kbDao.upsert(it.toEntity()) }.isSuccess
            }
            SyncStatus.PENDING_UPDATE -> {
                runCatching {
                    api.updateKnowledgeBase(
                        entity.id,
                        UpdateKbRequest(entity.name, entity.description),
                    ).data
                }.onSuccess { kbDao.upsert(it.toEntity()) }.isSuccess
            }
            SyncStatus.PENDING_DELETE -> false
            SyncStatus.SYNCED -> true
        }
    }

    suspend fun countPending(): Int =
        kbDao.getPendingSync().size + kbDao.getPendingDeletes().size

    suspend fun deleteAll(ids: List<String>) {
        ids.forEach { delete(it) }
    }
}

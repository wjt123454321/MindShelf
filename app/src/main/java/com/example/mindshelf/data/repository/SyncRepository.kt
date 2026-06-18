package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.SyncPreferences
import com.example.mindshelf.data.local.dao.KnowledgeBaseDao
import com.example.mindshelf.data.local.dao.NoteDao
import com.example.mindshelf.data.local.dao.NoteKbDao
import com.example.mindshelf.data.local.kbLinks
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.sync.SyncCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/** 登录后与云同步编排。 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: MindShelfApi,
    private val noteDao: NoteDao,
    private val kbDao: KnowledgeBaseDao,
    private val noteKbDao: NoteKbDao,
    private val syncPreferences: SyncPreferences,
    private val syncCoordinator: SyncCoordinator,
) {
    suspend fun pullFromRemote() {
        syncCoordinator.syncAll()
    }

    suspend fun syncIfEnabled() = syncCoordinator.syncAll()

    /** 首次登录或需要全量对齐时使用。 */
    suspend fun fullReplaceFromServer() {
        if (!syncPreferences.isCloudSyncEnabled()) return
        val notes = api.listNotes().data
        val kbs = api.listKnowledgeBases().data
        noteDao.deleteAll()
        kbDao.deleteAll()
        noteKbDao.deleteAll()
        noteDao.upsertAll(notes.map { it.toEntity() })
        kbDao.upsertAll(kbs.map { it.toEntity() })
        noteKbDao.insertAll(notes.flatMap { kbLinks(it.id, it.knowledgeBaseIds) })
    }
}

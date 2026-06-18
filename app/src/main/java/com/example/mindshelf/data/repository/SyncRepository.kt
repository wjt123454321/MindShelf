package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.dao.KnowledgeBaseDao
import com.example.mindshelf.data.local.dao.NoteDao
import com.example.mindshelf.data.local.dao.NoteKbDao
import com.example.mindshelf.data.local.kbLinks
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.remote.MindShelfApi
import javax.inject.Inject
import javax.inject.Singleton

/** 登录后从服务端拉取数据写入本地 Room。 */
@Singleton
class SyncRepository @Inject constructor(
    private val api: MindShelfApi,
    private val noteDao: NoteDao,
    private val kbDao: KnowledgeBaseDao,
    private val noteKbDao: NoteKbDao,
) {
    suspend fun pullFromRemote() {
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

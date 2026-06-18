package com.example.mindshelf.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/** 笔记与知识库的离线变更上行同步。 */
@Singleton
class ContentSyncRepository @Inject constructor(
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
) {
    suspend fun syncAllPending(): Int =
        noteRepository.syncAllPending() + knowledgeRepository.syncAllPending()
}

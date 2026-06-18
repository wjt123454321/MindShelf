package com.example.mindshelf.data.sync

import com.example.mindshelf.data.local.SyncPreferences
import com.example.mindshelf.data.local.TokenStore
import com.example.mindshelf.data.repository.KnowledgeRepository
import com.example.mindshelf.data.repository.NoteRepository
import com.example.mindshelf.data.repository.SyncEngine
import com.example.mindshelf.data.repository.SyncResult
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/** 统一云同步决策：本地优先，按需写远程。 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val tokenStore: TokenStore,
    private val syncPreferences: SyncPreferences,
    private val networkMonitor: NetworkMonitor,
    private val syncEngine: SyncEngine,
    private val noteRepository: Lazy<NoteRepository>,
    private val knowledgeRepository: Lazy<KnowledgeRepository>,
) {
    suspend fun shouldWriteRemote(): Boolean =
        tokenStore.getAccessToken() != null &&
            syncPreferences.isCloudSyncEnabled() &&
            networkMonitor.isOnline()

    suspend fun flushPending(): Int {
        val notes = noteRepository.get().syncAllPending()
        val kbs = knowledgeRepository.get().syncAllPending()
        val deletes = noteRepository.get().syncPendingDeletes() +
            knowledgeRepository.get().syncPendingDeletes()
        return notes + kbs + deletes
    }

    suspend fun syncAll(): SyncResult {
        if (!syncPreferences.isCloudSyncEnabled()) return SyncResult()
        flushPending()
        return syncEngine.sync()
    }

    suspend fun afterLogin() {
        syncPreferences.setCloudSyncEnabled(true)
        syncAll()
    }
}

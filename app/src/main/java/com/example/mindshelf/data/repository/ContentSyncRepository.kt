package com.example.mindshelf.data.repository

import com.example.mindshelf.data.sync.SyncCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/** 将待同步内容推送到服务端。 */
@Singleton
class ContentSyncRepository @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
) {
    suspend fun syncAllPending(): Int = syncCoordinator.flushPending()

    suspend fun syncAll(): SyncResult = syncCoordinator.syncAll()
}

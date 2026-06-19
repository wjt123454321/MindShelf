package com.example.mindshelf.data.chat

import com.example.mindshelf.data.local.dao.ChatDao
import com.example.mindshelf.data.local.entity.BranchEntity
import com.example.mindshelf.data.local.entity.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton

/** 合并同会话内重复的「主分支」，仅依赖 ChatDao，避免 SyncEngine ↔ ChatRepository 循环注入。 */
@Singleton
class BranchDeduplicator @Inject constructor(
    private val chatDao: ChatDao,
) {
    suspend fun deduplicateMainBranches(conversationId: String) {
        val branches = chatDao.getBranches(conversationId)
        val mainBranches = branches.filter {
            it.label == "主分支" && it.rootMessageId.isNullOrBlank()
        }
        if (mainBranches.size <= 1) return

        val messageCounts = mainBranches.associate { branch ->
            branch.id to chatDao.countMessagesForBranch(conversationId, branch.id)
        }
        val canonical = mainBranches.maxWithOrNull(
            compareBy<BranchEntity> { messageCounts[it.id] ?: 0 }
                .thenBy { if (it.syncStatus == SyncStatus.SYNCED) 0 else 1 }
                .thenBy { it.createdAt },
        ) ?: return

        for (dup in mainBranches) {
            if (dup.id == canonical.id) continue
            chatDao.reassignMessageBranch(conversationId, dup.id, canonical.id)
            chatDao.reassignToolActionBranch(conversationId, dup.id, canonical.id)
            chatDao.deleteBranchById(dup.id)
        }
    }
}

package com.example.mindshelf.data.repository

import com.example.mindshelf.data.local.dao.ChatDao
import com.example.mindshelf.data.local.entity.SyncStatus
import com.example.mindshelf.data.local.entity.ToolActionEntity
import com.example.mindshelf.data.local.toDto
import com.example.mindshelf.data.local.toEntity
import com.example.mindshelf.data.remote.MindShelfApi
import com.example.mindshelf.data.remote.dto.BranchDto
import com.example.mindshelf.data.remote.dto.ConversationDto
import com.example.mindshelf.data.remote.dto.CreateBranchRequest
import com.example.mindshelf.data.remote.dto.CreateConversationRequest
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.chat.buildBranchPathMessages
import com.example.mindshelf.data.remote.dto.ToolPreview
import com.example.mindshelf.data.sync.SyncCoordinator
import com.google.gson.Gson
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: MindShelfApi,
    private val chatDao: ChatDao,
    private val syncCoordinator: SyncCoordinator,
) {
    private val gson = Gson()

    suspend fun listConversationsLocal(): List<ConversationDto> =
        chatDao.getConversations().map {
            withDisplayTitle(it.toDto())
        }

    suspend fun listConversations(): List<ConversationDto> =
        try {
            if (syncCoordinator.shouldWriteRemote()) {
                val remote = api.listConversations().data
                chatDao.upsertConversations(remote.map { it.toEntity(SyncStatus.SYNCED) })
                remote.map { withDisplayTitle(it) }
            } else {
                listConversationsLocal()
            }
        } catch (_: Exception) {
            listConversationsLocal()
        }

    private suspend fun withDisplayTitle(conv: ConversationDto): ConversationDto {
        if (conv.title != "新对话") return conv
        val preview = chatDao.getFirstUserMessage(conv.id)?.content?.trim()?.take(40)
        return if (preview.isNullOrBlank()) conv else conv.copy(title = preview)
    }

    suspend fun isConversationEmpty(conversationId: String): Boolean =
        chatDao.countMessages(conversationId) == 0

    suspend fun findEmptyConversation(): ConversationDto? =
        listConversationsLocal().firstOrNull { isConversationEmpty(it.id) }

    suspend fun createConversation(title: String = "新对话"): ConversationDto {
        val convId = UUID.randomUUID().toString()
        val branchId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val conv = ConversationDto(convId, title, now, now)
        val branch = BranchDto(branchId, convId, "主分支", null, now)
        chatDao.upsertConversations(listOf(conv.toEntity(SyncStatus.PENDING_CREATE)))
        chatDao.upsertBranches(listOf(branch.toEntity(SyncStatus.PENDING_CREATE)))

        if (syncCoordinator.shouldWriteRemote()) {
            try {
                val remote = api.createConversation(
                    CreateConversationRequest(id = convId, title = title),
                ).data
                val branches = api.listBranches(convId).data
                chatDao.upsertConversations(listOf(remote.toEntity(SyncStatus.SYNCED)))
                chatDao.upsertBranches(branches.map { it.toEntity(SyncStatus.SYNCED) })
                return remote
            } catch (_: Exception) {
            }
        }
        return conv
    }

    suspend fun touchConversation(conversationId: String, title: String? = null) {
        val existing = chatDao.getConversation(conversationId) ?: return
        val now = System.currentTimeMillis()
        val newTitle = title ?: existing.title
        val status = when (existing.syncStatus) {
            SyncStatus.PENDING_CREATE -> SyncStatus.PENDING_CREATE
            else -> SyncStatus.PENDING_UPDATE
        }
        chatDao.upsertConversations(
            listOf(existing.copy(title = newTitle, updatedAt = now, syncStatus = status)),
        )
    }

    suspend fun deleteConversation(id: String) {
        if (syncCoordinator.shouldWriteRemote()) {
            try {
                api.deleteConversation(id)
            } catch (_: Exception) {
            }
        }
        chatDao.deleteToolActions(id)
        chatDao.deleteAllMessages(id)
        chatDao.deleteBranches(id)
        chatDao.deleteConversation(id)
    }

    suspend fun listBranchesLocal(conversationId: String): List<BranchDto> =
        chatDao.getBranches(conversationId).map {
            BranchDto(it.id, it.conversationId, it.label, it.rootMessageId, it.createdAt)
        }

    suspend fun listBranches(conversationId: String): List<BranchDto> {
        val local = listBranchesLocal(conversationId)
        return try {
            if (syncCoordinator.shouldWriteRemote()) {
                val remote = api.listBranches(conversationId).data
                chatDao.upsertBranches(remote.map { it.toEntity(SyncStatus.SYNCED) })
                mergeBranchLists(local, listBranchesLocal(conversationId))
            } else {
                local
            }
        } catch (_: Exception) {
            local
        }
    }

    suspend fun getBranchLocal(conversationId: String, branchId: String): BranchDto? =
        chatDao.getBranch(branchId)?.takeIf { it.conversationId == conversationId }?.let {
            BranchDto(it.id, it.conversationId, it.label, it.rootMessageId, it.createdAt)
        }

    private fun mergeBranchLists(vararg lists: List<BranchDto>): List<BranchDto> {
        val merged = linkedMapOf<String, BranchDto>()
        lists.forEach { list ->
            list.forEach { branch -> merged[branch.id] = branch }
        }
        return merged.values.sortedBy { it.createdAt }
    }

    suspend fun createBranch(
        conversationId: String,
        forkFromMessageId: String,
        label: String = "新分支",
        rootMessageId: String? = null,
    ): BranchDto {
        val branchId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val forkMsg = chatDao.getMessageById(forkFromMessageId)
        val rootId = rootMessageId ?: forkMsg?.parentId
        val local = BranchDto(
            id = branchId,
            conversationId = conversationId,
            label = label,
            rootMessageId = rootId,
            createdAt = now,
        )
        chatDao.upsertBranches(listOf(local.toEntity(SyncStatus.PENDING_CREATE)))

        if (syncCoordinator.shouldWriteRemote()) {
            try {
                val remote = api.createBranch(
                    conversationId,
                    CreateBranchRequest(id = branchId, label = label, forkFromMessageId = forkFromMessageId),
                ).data
                val merged = remote.copy(rootMessageId = remote.rootMessageId ?: rootId)
                chatDao.upsertBranches(listOf(merged.toEntity(SyncStatus.SYNCED)))
                return merged
            } catch (_: Exception) {
            }
        }
        return local
    }

    suspend fun upsertMessages(messages: List<MessageDto>) {
        if (messages.isEmpty()) return
        chatDao.upsertMessages(
            messages.map { dto ->
                val existing = chatDao.getMessageById(dto.id)
                val status = existing?.syncStatus ?: SyncStatus.PENDING_CREATE
                dto.toEntity(existing?.searchSourcesJson ?: "[]", status)
            },
        )
        messages.firstOrNull()?.conversationId?.let { convId ->
            val firstUser = messages.firstOrNull { it.role == "user" }
            if (firstUser != null) {
                val conv = chatDao.getConversation(convId)
                if (conv?.title == "新对话") {
                    val preview = firstUser.content.trim().take(40)
                    if (preview.isNotBlank()) touchConversation(convId, preview)
                } else {
                    touchConversation(convId)
                }
            }
        }
    }

    suspend fun saveMessageSearchSources(messageId: String, sources: List<SearchSource>) {
        if (sources.isEmpty()) return
        chatDao.updateMessageSearchSources(messageId, gson.toJson(sources))
    }

    suspend fun loadMessageSearchSources(conversationId: String): Map<String, List<SearchSource>> =
        chatDao.getMessageSearchSourceRows(conversationId)
            .mapNotNull { row ->
                val sources = runCatching {
                    gson.fromJson(row.searchSourcesJson, Array<SearchSource>::class.java)?.toList()
                }.getOrNull().orEmpty()
                if (sources.isEmpty()) null else row.id to sources
            }
            .toMap()

    suspend fun listMessagesLocal(conversationId: String, branchId: String): List<MessageDto> {
        val all = listAllMessagesLocal(conversationId)
        val branches = listBranchesLocal(conversationId)
        val branch = branches.find { it.id == branchId }
            ?: getBranchLocal(conversationId, branchId)
        return buildBranchPathMessages(all, branchId, branches, branchOverride = branch)
    }

    suspend fun listMessages(conversationId: String, branchId: String): List<MessageDto> =
        try {
            if (syncCoordinator.shouldWriteRemote()) {
                val remote = api.listMessages(conversationId, branchId).data
                chatDao.upsertMessages(
                    remote.map { dto ->
                        val existing = chatDao.getMessageById(dto.id)
                        dto.toEntity(existing?.searchSourcesJson ?: "[]", SyncStatus.SYNCED)
                    },
                )
                remote
            } else {
                listMessagesLocal(conversationId, branchId)
            }
        } catch (_: Exception) {
            listMessagesLocal(conversationId, branchId)
        }

    suspend fun listAllMessagesLocal(conversationId: String): List<MessageDto> =
        chatDao.getAllMessages(conversationId).map { it.toDto() }

    suspend fun listAllMessages(conversationId: String): List<MessageDto> =
        try {
            if (syncCoordinator.shouldWriteRemote()) {
                val remote = api.listAllMessages(conversationId).data
                chatDao.upsertMessages(
                    remote.map { dto ->
                        val existing = chatDao.getMessageById(dto.id)
                        dto.toEntity(existing?.searchSourcesJson ?: "[]", SyncStatus.SYNCED)
                    },
                )
                remote
            } else {
                listAllMessagesLocal(conversationId)
            }
        } catch (_: Exception) {
            listAllMessagesLocal(conversationId)
        }

    suspend fun upsertToolAction(
        id: String,
        conversationId: String,
        branchId: String,
        anchorMessageId: String?,
        segmentIndex: Int? = null,
        tool: String,
        preview: ToolPreview,
        status: String,
        resultMessage: String? = null,
        errorMessage: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        chatDao.upsertToolActions(
            listOf(
                ToolActionEntity(
                    id = id,
                    conversationId = conversationId,
                    branchId = branchId,
                    anchorMessageId = anchorMessageId,
                    segmentIndex = segmentIndex,
                    tool = tool,
                    previewJson = gson.toJson(preview),
                    status = status,
                    resultMessage = resultMessage,
                    errorMessage = errorMessage,
                    createdAt = createdAt,
                ),
            ),
        )
    }

    suspend fun listToolActions(conversationId: String, branchId: String): List<ToolActionEntity> =
        chatDao.getToolActions(conversationId, branchId)
}

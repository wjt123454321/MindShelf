package com.example.mindshelf.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.BranchDto
import com.example.mindshelf.data.remote.dto.ConversationDto
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.MessageSegment
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.data.remote.dto.ToolPreview
import com.example.mindshelf.data.ai.AiRouter
import com.example.mindshelf.data.repository.ChatRepository
import com.example.mindshelf.data.repository.ChatStreamEvent
import com.example.mindshelf.data.repository.SearchSource
import com.example.mindshelf.data.local.AiPreferences
import com.example.mindshelf.data.local.ChatPreferences
import com.example.mindshelf.data.chat.buildBranchPathMessages
import com.example.mindshelf.data.repository.ContentSyncRepository
import com.example.mindshelf.data.repository.KnowledgeRepository
import com.example.mindshelf.data.repository.NoteRepository
import com.example.mindshelf.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ActiveChat(
    val conversationId: String,
    val branchId: String,
    val title: String,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val aiRouter: AiRouter,
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val contentSyncRepository: ContentSyncRepository,
    private val aiPreferences: AiPreferences,
    private val chatPreferences: ChatPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private var streamJob: Job? = null
    private var activeStreamGeneration = 0

    private val _conversations = MutableStateFlow<List<ConversationDto>>(emptyList())
    val conversations: StateFlow<List<ConversationDto>> = _conversations.asStateFlow()

    private val _branches = MutableStateFlow<List<BranchDto>>(emptyList())
    val branches: StateFlow<List<BranchDto>> = _branches.asStateFlow()

    private val _activeChat = MutableStateFlow<ActiveChat?>(null)
    val activeChat: StateFlow<ActiveChat?> = _activeChat.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()

    private val _allMessages = MutableStateFlow<List<MessageDto>>(emptyList())
    val allMessages: StateFlow<List<MessageDto>> = _allMessages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _initializing = MutableStateFlow(false)
    val initializing: StateFlow<Boolean> = _initializing.asStateFlow()

    private val _activeChannelLabel = MutableStateFlow("内置服务")
    val activeChannelLabel: StateFlow<String> = _activeChannelLabel.asStateFlow()

    private val _toolActions = MutableStateFlow<List<ToolActionItem>>(emptyList())
    val toolActions: StateFlow<List<ToolActionItem>> = _toolActions.asStateFlow()

    private val _messageSearchSources = MutableStateFlow<Map<String, List<SearchSource>>>(emptyMap())
    val messageSearchSources: StateFlow<Map<String, List<SearchSource>>> = _messageSearchSources.asStateFlow()

    private val _streamStatusHint = MutableStateFlow<String?>(null)
    val streamStatusHint: StateFlow<String?> = _streamStatusHint.asStateFlow()

    private val _reasoningStreaming = MutableStateFlow(false)
    val reasoningStreaming: StateFlow<Boolean> = _reasoningStreaming.asStateFlow()

    val notes: StateFlow<List<NoteDto>> = noteRepository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoReadReplies: StateFlow<Boolean> = aiPreferences.autoReadReplies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val enableSearch: StateFlow<Boolean> = aiPreferences.enableSearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val builtinModel: StateFlow<String> = aiPreferences.builtinModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferences.MODEL_FLASH)

    val isBuiltinChannel: StateFlow<Boolean> = aiPreferences.channel
        .map { it == AiPreferences.CHANNEL_BUILTIN }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 重新提问后首条消息的 parent_message_id */
    private var pendingSendParentId: String? = null

    fun loadConversations() {
        viewModelScope.launch {
            _conversations.value = chatRepository.listConversations()
        }
    }

    fun ensureActiveChat() {
        if (_initializing.value) return
        viewModelScope.launch { _activeChannelLabel.value = aiRouter.getActiveChannelLabel() }
        if (_activeChat.value != null) {
            viewModelScope.launch {
                contentSyncRepository.syncAllPending()
                restoreLastActiveBranchIfNeeded()
                refreshActiveChatFromRemote()
                _activeChat.value?.let { loadPendingTools(it) }
            }
            return
        }
        _initializing.value = true
        viewModelScope.launch {
            try {
                contentSyncRepository.syncAllPending()
                restoreActiveChat(localOnly = true)
            } finally {
                _initializing.value = false
            }
            refreshActiveChatFromRemote()
        }
    }

    fun switchToConversation(conv: ConversationDto) {
        viewModelScope.launch {
            cancelActiveStream()
            val branches = chatRepository.listBranches(conv.id)
            if (branches.isEmpty()) return@launch
            val preservedBranchId = _activeChat.value
                ?.takeIf { it.conversationId == conv.id }
                ?.branchId
                ?.takeIf { id -> branches.any { it.id == id } }
            val branchId = resolveActiveBranchId(
                conversationId = conv.id,
                preferredBranchId = preservedBranchId,
                branches = branches,
            ) ?: return@launch
            _branches.value = branches
            pendingSendParentId = null
            commitActiveChat(ActiveChat(conv.id, branchId, conv.title))
            reloadBranchMessages(ActiveChat(conv.id, branchId, conv.title))
            loadSearchSources(conv.id)
            loadPendingTools(ActiveChat(conv.id, branchId, conv.title))
        }
    }

    fun switchBranch(branchId: String) {
        val chat = _activeChat.value ?: return
        viewModelScope.launch {
            pendingSendParentId = null
            val active = chat.copy(branchId = branchId)
            commitActiveChat(active)
            reloadBranchMessages(active)
            loadSearchSources(chat.conversationId)
            loadPendingTools(active)
        }
    }

    fun switchToSibling(sibling: MessageDto) {
        switchBranch(sibling.branchId)
    }

    fun getSiblings(
        message: MessageDto,
        branchMessages: List<MessageDto>,
        allMessages: List<MessageDto>,
        knownBranchIds: Set<String> = emptySet(),
    ): List<MessageDto> {
        val filterByBranch = knownBranchIds.isNotEmpty()
        return (allMessages + branchMessages)
            .distinctBy { it.id }
            .filter {
                it.role == message.role &&
                    it.parentId == message.parentId &&
                    (!filterByBranch || it.branchId in knownBranchIds)
            }
            .sortedBy { it.createdAt }
    }

    fun forkFromMessage(message: MessageDto) {
        val chat = _activeChat.value ?: return
        viewModelScope.launch {
            ensureForkContextPersisted(chat, message)
            val label = "分支 ${_branches.value.size + 1}"
            val branch = chatRepository.createBranch(
                chat.conversationId,
                message.id,
                label,
                rootMessageId = message.parentId,
            )
            _branches.value = chatRepository.listBranchesLocal(chat.conversationId)
            pendingSendParentId = message.parentId
            commitActiveChat(chat.copy(branchId = branch.id))
            reloadBranchMessages(chat.copy(branchId = branch.id))
            _allMessages.value = chatRepository.listAllMessagesLocal(chat.conversationId)
            loadPendingTools(chat.copy(branchId = branch.id))
        }
    }

    fun editMessage(message: MessageDto, newContent: String) {
        val content = newContent.trim()
        if (content.isBlank() || _streaming.value) return
        viewModelScope.launch {
            try {
                val chat = _activeChat.value ?: return@launch
                _allMessages.value = chatRepository.listAllMessagesLocal(chat.conversationId)
                val forkTarget = _allMessages.value.find { it.id == message.id }
                    ?: _messages.value.find { it.id == message.id }
                    ?: message
                ensureForkContextPersisted(chat, forkTarget)
                val label = "分支 ${_branches.value.size + 1}"
                val branch = chatRepository.createBranch(
                    chat.conversationId,
                    forkTarget.id,
                    label,
                    rootMessageId = forkTarget.parentId,
                )
                _branches.value = chatRepository.listBranchesLocal(chat.conversationId)
                val active = chat.copy(branchId = branch.id)
                commitActiveChat(active)
                reloadBranchMessages(active)
                loadPendingTools(active)
                launchStream { generation ->
                    streamChat(active, forkTarget.parentId, content, generation)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 分支创建或发送失败时不让协程异常冒泡导致闪退
            }
        }
    }

    fun regenerate(assistantMessage: MessageDto) {
        val userMsg = _messages.value.find { it.id == assistantMessage.parentId }
            ?: _allMessages.value.find { it.id == assistantMessage.parentId }
        if (userMsg == null || _streaming.value) return
        viewModelScope.launch {
            val chat = _activeChat.value ?: return@launch
            ensureForkContextPersisted(chat, userMsg)
            val label = "重新生成 ${_branches.value.size + 1}"
            val branch = chatRepository.createBranch(
                chat.conversationId,
                userMsg.id,
                label,
                rootMessageId = userMsg.parentId,
            )
            _branches.value = chatRepository.listBranchesLocal(chat.conversationId)
            val active = chat.copy(branchId = branch.id)
            commitActiveChat(active)
            reloadBranchMessages(active)
            launchStream { generation ->
                streamChat(active, userMsg.parentId, userMsg.content, generation)
            }
        }
    }

    fun saveAsNewNote(content: String) {
        val title = content.lineSequence().firstOrNull()?.take(40)?.ifBlank { "AI 回答" } ?: "AI 回答"
        viewModelScope.launch {
            noteRepository.create(title, content)
        }
    }

    fun appendToNote(note: NoteDto, content: String) {
        viewModelScope.launch {
            val appended = if (note.content.isBlank()) content else "${note.content}\n\n$content"
            noteRepository.update(note.id, note.title, appended, note.syncVersion, note.knowledgeBaseIds)
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            if (_activeChat.value != null && _messages.value.isEmpty() && !_streaming.value) {
                return@launch
            }
            val existingEmpty = chatRepository.findEmptyConversation()
            if (existingEmpty != null) {
                switchToConversation(existingEmpty)
                return@launch
            }
            startNewConversationInternal()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            _conversations.value = chatRepository.listConversations()
            if (_activeChat.value?.conversationId == id) {
                restoreActiveChat(localOnly = true)
                syncActiveChatFromRemote()
            }
        }
    }

    private suspend fun startNewConversationInternal() {
        val conv = chatRepository.createConversation()
        val branches = chatRepository.listBranches(conv.id)
        val branchId = branches.first().id
        pendingSendParentId = null
        _branches.value = branches
        commitActiveChat(ActiveChat(conv.id, branchId, conv.title))
        _messages.value = emptyList()
        _allMessages.value = emptyList()
        _input.value = ""
        loadConversations()
    }

    private suspend fun restoreActiveChat(localOnly: Boolean = false) {
        _conversations.value = if (localOnly) {
            chatRepository.listConversationsLocal()
        } else {
            chatRepository.listConversations()
        }
        val saved = chatPreferences.getLastActiveChat()
        val targetId = _activeChat.value?.conversationId ?: saved?.conversationId
        val recent = targetId?.let { id -> _conversations.value.find { it.id == id } }
            ?: _conversations.value.firstOrNull()
        if (recent != null) {
            val branches = if (localOnly) {
                chatRepository.listBranchesLocal(recent.id)
            } else {
                chatRepository.listBranches(recent.id)
            }
            if (branches.isEmpty()) {
                pendingSendParentId = null
                commitActiveChat(null)
                _branches.value = emptyList()
                _messages.value = emptyList()
                _allMessages.value = emptyList()
                return
            }
            val branchId = resolveActiveBranchId(
                conversationId = recent.id,
                preferredBranchId = _activeChat.value
                    ?.takeIf { it.conversationId == recent.id }
                    ?.branchId
                    ?: saved?.takeIf { it.conversationId == recent.id }?.branchId,
                branches = branches,
            ) ?: branches.first().id
            pendingSendParentId = null
            _branches.value = branches
            commitActiveChat(ActiveChat(recent.id, branchId, recent.title))
            _messages.value = if (localOnly) {
                chatRepository.listMessagesLocal(recent.id, branchId)
            } else {
                chatRepository.listMessages(recent.id, branchId)
            }
            _allMessages.value = if (localOnly) {
                chatRepository.listAllMessagesLocal(recent.id)
            } else {
                chatRepository.listAllMessages(recent.id)
            }
            loadPendingTools(ActiveChat(recent.id, branchId, recent.title))
            loadSearchSources(recent.id)
        } else {
            pendingSendParentId = null
            commitActiveChat(null)
            _branches.value = emptyList()
            _messages.value = emptyList()
            _allMessages.value = emptyList()
        }
    }

    private fun syncActiveChatFromRemote() {
        viewModelScope.launch {
            try {
                refreshActiveChatFromRemote()
            } catch (_: Exception) {
            }
        }
    }

    /** 刷新当前会话，不切换到列表第一条。 */
    private suspend fun refreshActiveChatFromRemote() {
        _conversations.value = chatRepository.listConversations()
        val chat = _activeChat.value
        if (chat == null) {
            restoreActiveChat(localOnly = false)
            return
        }
        val conv = _conversations.value.find { it.id == chat.conversationId }
        if (conv == null) {
            restoreActiveChat(localOnly = false)
            return
        }
        _branches.value = chatRepository.listBranches(chat.conversationId)
        val branchId = resolveActiveBranchId(
            conversationId = chat.conversationId,
            preferredBranchId = chat.branchId,
            branches = _branches.value,
        ) ?: return
        val active = chat.copy(title = conv.title, branchId = branchId)
        commitActiveChat(active)
        reloadBranchMessages(active)
        loadSearchSources(chat.conversationId)
    }

    private suspend fun refreshBranchDisplay(chat: ActiveChat) {
        _allMessages.value = chatRepository.listAllMessagesLocal(chat.conversationId)
        _branches.value = chatRepository.listBranchesLocal(chat.conversationId)
        val path = resolveBranchPath(chat)
        _messages.value = mergeBranchMessages(_messages.value, path)
    }

    private suspend fun finalizeStreamMessages(
        chat: ActiveChat,
        tempUserId: String,
        assistantId: String,
        segments: MutableList<MessageSegment>,
        pendingContent: String,
        pendingSearchSources: List<SearchSource>,
    ) {
        if (pendingContent.isNotBlank()) {
            appendSegment(segments, "content", pendingContent)
        }
        dedupeContentSegments(segments)
        collapseReasoningContentOverlap(segments)
        val assistantDraft = _messages.value.find { it.id == assistantId }
        val hasOutput = segments.isNotEmpty() ||
            !assistantDraft?.content.isNullOrBlank() ||
            !assistantDraft?.reasoning.isNullOrBlank()
        val awaitingTool = _toolActions.value.any {
            it.anchorMessageId == assistantId &&
                (it.status == ToolActionStatus.PENDING || it.status == ToolActionStatus.EXECUTING)
        }
        if (hasOutput || awaitingTool) {
            val assistant = buildStreamingAssistant(assistantId, chat, tempUserId, segments)
            _messages.update { list -> list.upsertMessage(assistant) }
            if (pendingSearchSources.isNotEmpty()) {
                applySearchSources(assistantId, pendingSearchSources)
            }
        } else {
            _messages.update { list ->
                list.map { msg ->
                    if (msg.id == assistantId) msg.copy(content = "AI 未返回内容") else msg
                }
            }
        }
        chatRepository.upsertMessages(_messages.value)
        syncAllMessagesFromCurrent()
        refreshBranchDisplay(chat)
    }

    private suspend fun reloadBranchMessages(chat: ActiveChat) {
        _allMessages.value = chatRepository.listAllMessagesLocal(chat.conversationId)
        _branches.value = chatRepository.listBranchesLocal(chat.conversationId)
        _messages.value = resolveBranchPath(chat)
    }

    private suspend fun commitActiveChat(chat: ActiveChat?) {
        _activeChat.value = chat
        if (chat != null) {
            chatPreferences.setLastActiveChat(chat.conversationId, chat.branchId)
        } else {
            chatPreferences.clearLastActiveChat()
        }
    }

    /** 优先保留当前/已保存分支，避免刷新或返回页面时回退到主分支。 */
    private suspend fun resolveActiveBranchId(
        conversationId: String,
        preferredBranchId: String?,
        branches: List<BranchDto>,
    ): String? {
        preferredBranchId?.takeIf { id -> branches.any { it.id == id } }?.let { return it }
        preferredBranchId?.let { id ->
            chatRepository.getBranchLocal(conversationId, id)?.let { branch ->
                if (_branches.value.none { it.id == branch.id }) {
                    _branches.value = _branches.value + branch
                }
                return branch.id
            }
        }
        chatPreferences.getLastActiveChat()
            ?.takeIf { it.conversationId == conversationId }
            ?.branchId
            ?.let { savedId ->
                if (branches.any { it.id == savedId }) return savedId
                chatRepository.getBranchLocal(conversationId, savedId)?.let { branch ->
                    if (_branches.value.none { it.id == branch.id }) {
                        _branches.value = _branches.value + branch
                    }
                    return branch.id
                }
            }
        return branches.firstOrNull()?.id
    }

    private suspend fun restoreLastActiveBranchIfNeeded() {
        val chat = _activeChat.value ?: return
        val saved = chatPreferences.getLastActiveChat() ?: return
        if (saved.conversationId != chat.conversationId || saved.branchId == chat.branchId) return
        _branches.value = chatRepository.listBranchesLocal(chat.conversationId)
        val branchId = resolveActiveBranchId(
            conversationId = chat.conversationId,
            preferredBranchId = saved.branchId,
            branches = _branches.value,
        ) ?: return
        if (branchId == chat.branchId) return
        commitActiveChat(chat.copy(branchId = branchId))
    }

    private suspend fun resolveBranchPath(chat: ActiveChat): List<MessageDto> {
        val branch = _branches.value.find { it.id == chat.branchId }
            ?: chatRepository.getBranchLocal(chat.conversationId, chat.branchId)?.also { found ->
                if (_branches.value.none { it.id == found.id }) {
                    _branches.value = _branches.value + found
                }
            }
        return buildBranchPathMessages(
            allMessages = _allMessages.value,
            branchId = chat.branchId,
            branches = _branches.value,
            branchOverride = branch,
        )
    }

    /** 确保 fork 点及其祖先链已落库，供分支路径计算与服务端 createBranch 使用。 */
    private suspend fun ensureForkContextPersisted(chat: ActiveChat, forkMessage: MessageDto) {
        val all = chatRepository.listAllMessagesLocal(chat.conversationId)
        val branches = chatRepository.listBranchesLocal(chat.conversationId)
        val path = buildBranchPathMessages(all, chat.branchId, branches)
        val toPersist = buildList {
            for (msg in path) {
                add(msg)
                if (msg.id == forkMessage.id) break
            }
            if (none { it.id == forkMessage.id }) {
                add(forkMessage)
            }
        }
        if (toPersist.isNotEmpty()) {
            chatRepository.upsertMessages(toPersist)
        }
        _allMessages.value = chatRepository.listAllMessagesLocal(chat.conversationId)
        syncAllMessagesFromCurrent()
    }

    private fun cancelActiveStream() {
        streamJob?.cancel()
        activeStreamGeneration++
        _streaming.value = false
        _streamStatusHint.value = null
        _reasoningStreaming.value = false
    }

    private fun isStreamActive(chat: ActiveChat, streamGeneration: Int): Boolean {
        val active = _activeChat.value ?: return false
        return streamGeneration == activeStreamGeneration &&
            active.conversationId == chat.conversationId &&
            active.branchId == chat.branchId
    }

    private suspend fun refreshBranchesAndAllMessagesLocal() {
        val chat = _activeChat.value ?: return
        _branches.value = chatRepository.listBranchesLocal(chat.conversationId)
        _allMessages.value = chatRepository.listAllMessagesLocal(chat.conversationId)
    }

    private suspend fun createConversationForFirstMessage(): ActiveChat {
        val conv = chatRepository.createConversation()
        val branches = chatRepository.listBranches(conv.id)
        val branchId = branches.first().id
        pendingSendParentId = null
        _branches.value = branches
        val active = ActiveChat(conv.id, branchId, conv.title)
        commitActiveChat(active)
        _messages.value = emptyList()
        _allMessages.value = emptyList()
        _conversations.value = chatRepository.listConversations()
        return active
    }

    private suspend fun refreshBranchesAndAllMessages() {
        val chat = _activeChat.value ?: return
        _branches.value = chatRepository.listBranches(chat.conversationId)
        _allMessages.value = chatRepository.listAllMessages(chat.conversationId)
    }

    private suspend fun refreshActiveTitle(conversationId: String) {
        val conv = chatRepository.listConversations().find { it.id == conversationId }
        if (conv != null) {
            _activeChat.update { it?.takeIf { a -> a.conversationId == conversationId }?.copy(title = conv.title) }
            _conversations.value = chatRepository.listConversations()
        }
    }

    fun setInput(value: String) {
        _input.value = value
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferences.setSearchEnabled(enabled)
        }
    }

    fun setBuiltinModel(model: String) {
        viewModelScope.launch {
            aiPreferences.setBuiltinModel(model)
        }
    }

    fun confirmTool(pendingId: String, approved: Boolean) {
        val item = _toolActions.value.find { it.id == pendingId } ?: return
        if (item.status != ToolActionStatus.PENDING && item.status != ToolActionStatus.EXECUTING) return
        val chat = _activeChat.value ?: return
        if (!approved) {
            updateToolAction(pendingId) {
                it.copy(status = ToolActionStatus.REJECTED, resultMessage = "已取消")
            }
            viewModelScope.launch {
                aiRouter.resumeAfterToolConfirm(pendingId, false).collect { }
            }
            return
        }
        viewModelScope.launch {
            updateToolAction(pendingId) { it.copy(status = ToolActionStatus.EXECUTING) }
            launchStream { generation -> resumeAfterTool(chat, pendingId, generation) }
            contentSyncRepository.syncAll()
        }
    }

    fun send() {
        val content = _input.value.trim()
        if (content.isBlank() || _streaming.value) return

        viewModelScope.launch {
            val chat = _activeChat.value ?: createConversationForFirstMessage()
            commitActiveChat(chat)
            reloadBranchMessages(chat)
            val parentId = pendingSendParentId.also { pendingSendParentId = null }
                ?: _messages.value.lastOrNull { it.role == "assistant" }?.id
                ?: _messages.value.lastOrNull()?.id
            _input.value = ""
            launchStream { generation -> streamChat(chat, parentId, content, generation) }
        }
    }

    /** 在 Application 作用域运行流式请求，避免切后台或页面生命周期影响 SSE 读取。 */
    private fun launchStream(block: suspend (Int) -> Unit) {
        streamJob?.cancel()
        val generation = ++activeStreamGeneration
        streamJob = applicationScope.launch {
            try {
                block(generation)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun streamChat(
        chat: ActiveChat,
        parentId: String?,
        content: String,
        streamGeneration: Int,
    ) {
        pendingSendParentId = null
        val tempId = UUID.randomUUID().toString()
        val userMsg = MessageDto(
            id = tempId,
            conversationId = chat.conversationId,
            branchId = chat.branchId,
            parentId = parentId,
            role = "user",
            content = content,
            createdAt = System.currentTimeMillis(),
        )
        val path = resolveBranchPath(chat)
        _messages.value = path + userMsg
        syncAllMessagesFromCurrent()
        chatRepository.upsertMessages(listOf(userMsg))
        contentSyncRepository.syncAllPending()
        _streaming.value = true
        _streamStatusHint.value = null
        _reasoningStreaming.value = true

        var assistantContent = ""
        var assistantReasoning = ""
        var pendingContent = ""
        val segments = mutableListOf<MessageSegment>()
        val assistantId = UUID.randomUUID().toString()
        _messages.value = _messages.value + buildStreamingAssistant(assistantId, chat, tempId, segments)
        val llmHistory = path.filter { it.role == "user" || it.role == "assistant" }
        var receivedMessageDone = false
        var pendingSearchSources: List<SearchSource> = emptyList()
        try {
            aiRouter.streamChat(
                chat.conversationId,
                chat.branchId,
                parentId,
                content,
                llmHistory,
            ).collect { event ->
                if (!isStreamActive(chat, streamGeneration)) return@collect
                when (event) {
                    is ChatStreamEvent.Status -> applyStreamStatus(event.phase, event.tool)
                    is ChatStreamEvent.ReasoningRoundStart -> {
                        if (assistantReasoning.isNotBlank()) {
                            assistantReasoning += "\n\n---\n\n"
                        }
                        _reasoningStreaming.value = true
                        _streamStatusHint.value = null
                    }
                    is ChatStreamEvent.ReasoningDelta -> {
                        _streamStatusHint.value = null
                        _reasoningStreaming.value = true
                        if (assistantReasoning.isBlank() && assistantContent.isNotBlank()) {
                            pendingContent = assistantContent + pendingContent
                            assistantContent = ""
                        }
                        assistantReasoning += event.content
                        appendSegment(segments, "reasoning", event.content)
                        _messages.update { list ->
                            list.upsertMessage(
                                buildStreamingAssistant(assistantId, chat, tempId, segments, pendingContent),
                            )
                        }
                    }
                    is ChatStreamEvent.Delta -> {
                        _streamStatusHint.value = null
                        if (event.content.isNotEmpty()) {
                            if (_reasoningStreaming.value) {
                                _reasoningStreaming.value = false
                            }
                            if (pendingContent.isNotBlank()) {
                                appendSegment(segments, "content", pendingContent)
                                pendingContent = ""
                            }
                            appendSegment(segments, "content", event.content)
                            assistantContent = contentFromSegments(segments)
                        }
                        _messages.update { list ->
                            list.upsertMessage(
                                buildStreamingAssistant(assistantId, chat, tempId, segments, pendingContent),
                            )
                        }
                    }
                    is ChatStreamEvent.MessageDone -> {
                        receivedMessageDone = true
                        if (pendingContent.isNotBlank()) {
                            appendSegment(segments, "content", pendingContent)
                            pendingContent = ""
                        }
                        dedupeContentSegments(segments)
                        collapseReasoningContentOverlap(segments)
                        val doneMsg = event.message.copy(
                            content = event.message.content.orEmpty().ifBlank { contentFromSegments(segments) },
                            reasoning = event.message.reasoning?.takeIf { it.isNotBlank() }
                                ?: assistantReasoning.takeIf { it.isNotBlank() },
                            segments = event.message.segments?.takeIf { it.isNotEmpty() } ?: segments.toList(),
                        )
                        if (pendingSearchSources.isNotEmpty()) {
                            applySearchSources(doneMsg.id, pendingSearchSources)
                            _messageSearchSources.update { it - assistantId }
                        }
                        val serverUserId = doneMsg.parentId
                        if (serverUserId != null && serverUserId != tempId) {
                            rebindMessageAnchors(tempId, serverUserId)
                        }
                        _messages.update { list ->
                            list.map { msg ->
                                when {
                                    msg.id == tempId -> msg.copy(id = doneMsg.parentId ?: msg.id)
                                    msg.id == assistantId -> doneMsg
                                    else -> msg
                                }
                            }.distinctBy { it.id }.sortedBy { it.createdAt }
                        }
                        rebindToolAnchors(assistantId, doneMsg.id)
                        syncAllMessagesFromCurrent()
                        chatRepository.upsertMessages(_messages.value)
                    }
                    is ChatStreamEvent.ToolPending -> {
                        if (pendingContent.isNotBlank()) {
                            appendSegment(segments, "content", pendingContent)
                            pendingContent = ""
                        }
                        _messages.update { list ->
                            list.upsertMessage(
                                buildStreamingAssistant(assistantId, chat, tempId, segments),
                            )
                        }
                        val toolItem = ToolActionItem(
                            id = event.pendingId,
                            conversationId = chat.conversationId,
                            branchId = chat.branchId,
                            tool = event.tool,
                            preview = event.preview,
                            status = ToolActionStatus.PENDING,
                            anchorMessageId = assistantId,
                            segmentIndex = segments.size,
                            createdAt = System.currentTimeMillis(),
                        )
                        _toolActions.update { current ->
                            if (current.any { it.id == event.pendingId }) current
                            else current + toolItem
                        }
                        persistToolAction(toolItem)
                    }
                    is ChatStreamEvent.SearchResult -> {
                        pendingSearchSources = mergeSearchSources(pendingSearchSources, event.results)
                        applySearchSources(assistantId, pendingSearchSources)
                        _streamStatusHint.value = "正在整理搜索结果…"
                    }
                    is ChatStreamEvent.Error -> {
                        val errAssistant = MessageDto(
                            id = assistantId,
                            conversationId = chat.conversationId,
                            branchId = chat.branchId,
                            parentId = tempId,
                            role = "assistant",
                            content = "错误: ${event.message}",
                            createdAt = System.currentTimeMillis(),
                        )
                        _messages.update { list -> list.upsertMessage(errAssistant) }
                    }
                    is ChatStreamEvent.StreamComplete -> Unit
                    is ChatStreamEvent.Done -> Unit
                    is ChatStreamEvent.ToolExecuted -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 流异常已在 Repository 层处理；此处兜底避免协程未捕获异常导致闪退
        } finally {
            if (streamGeneration == activeStreamGeneration) {
                _streaming.value = false
                _streamStatusHint.value = null
                _reasoningStreaming.value = false
            }
            if (streamGeneration != activeStreamGeneration) return
            if (!isStreamActive(chat, streamGeneration)) return
            try {
                if (!receivedMessageDone) {
                    finalizeStreamMessages(
                        chat = chat,
                        tempUserId = tempId,
                        assistantId = assistantId,
                        segments = segments,
                        pendingContent = pendingContent,
                        pendingSearchSources = pendingSearchSources,
                    )
                } else {
                    chatRepository.upsertMessages(_messages.value)
                    syncAllMessagesFromCurrent()
                    refreshBranchDisplay(chat)
                }
                refreshActiveTitle(chat.conversationId)
                loadPendingTools(chat)
                contentSyncRepository.syncAll()
            } catch (_: Exception) {
                // 保留流式过程中的内存消息
            }
        }
    }

    private suspend fun resumeAfterTool(
        chat: ActiveChat,
        pendingId: String,
        streamGeneration: Int,
    ) {
        val existing = findAssistantForToolResume(chat, pendingId) ?: run {
            appendToolResumeError(pendingId, "无法继续对话：缺少助手消息")
            return
        }
        val assistantId = existing.id
        val parentId = existing.parentId
            ?: _messages.value.lastOrNull { it.role == "user" }?.id
            ?: run {
                appendToolResumeError(pendingId, "无法继续对话：缺少上下文")
                return
            }
        _streaming.value = true
        _streamStatusHint.value = null
        _reasoningStreaming.value = true

        val segments = existing.segments.orEmpty().toMutableList()
        var assistantContent = existing.content.orEmpty()
        var assistantReasoning = existing.reasoning.orEmpty()
        var pendingContent = ""
        var receivedMessageDone = false

        try {
            aiRouter.resumeAfterToolConfirm(pendingId, true).collect { event ->
                if (!isStreamActive(chat, streamGeneration)) return@collect
                when (event) {
                    is ChatStreamEvent.ToolExecuted -> {
                        val result = event.result
                        if (result["error"] != null) {
                            updateToolAction(pendingId) {
                                it.copy(
                                    status = ToolActionStatus.FAILED,
                                    errorMessage = result["error"]?.toString(),
                                )
                            }
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            applyToolSync(event.tool, result as Map<String, Any>)
                            updateToolAction(pendingId) {
                                it.copy(
                                    status = ToolActionStatus.SUCCESS,
                                    resultMessage = buildToolSuccessMessage(event.tool, event.preview, result),
                                )
                            }
                        }
                    }
                    is ChatStreamEvent.Status -> applyStreamStatus(event.phase, event.tool)
                    is ChatStreamEvent.ReasoningRoundStart -> {
                        if (assistantReasoning.isNotBlank()) {
                            assistantReasoning += "\n\n---\n\n"
                        }
                        _reasoningStreaming.value = true
                        _streamStatusHint.value = null
                    }
                    is ChatStreamEvent.ReasoningDelta -> {
                        _streamStatusHint.value = null
                        _reasoningStreaming.value = true
                        assistantReasoning += event.content
                        appendSegment(segments, "reasoning", event.content)
                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == assistantId) buildStreamingAssistant(assistantId, chat, parentId, segments)
                                else msg
                            }
                        }
                    }
                    is ChatStreamEvent.Delta -> {
                        _streamStatusHint.value = null
                        _reasoningStreaming.value = false
                        appendSegment(segments, "content", event.content)
                        assistantContent = contentFromSegments(segments)
                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == assistantId) buildStreamingAssistant(assistantId, chat, parentId, segments)
                                else msg
                            }
                        }
                    }
                    is ChatStreamEvent.MessageDone -> {
                        receivedMessageDone = true
                        dedupeContentSegments(segments)
                        collapseReasoningContentOverlap(segments)
                        val doneMsg = event.message.copy(
                            content = event.message.content.orEmpty().ifBlank { contentFromSegments(segments) },
                            reasoning = event.message.reasoning?.takeIf { it.isNotBlank() }
                                ?: assistantReasoning.takeIf { it.isNotBlank() },
                            segments = event.message.segments?.takeIf { it.isNotEmpty() } ?: segments.toList(),
                        )
                        _messages.update { list ->
                            list.map { msg ->
                                if (msg.id == assistantId) doneMsg else msg
                            }.distinctBy { it.id }.sortedBy { it.createdAt }
                        }
                        rebindToolAnchors(assistantId, doneMsg.id)
                        syncAllMessagesFromCurrent()
                        chatRepository.upsertMessages(_messages.value)
                    }
                    is ChatStreamEvent.ToolPending -> {
                        val toolItem = ToolActionItem(
                            id = event.pendingId,
                            conversationId = chat.conversationId,
                            branchId = chat.branchId,
                            tool = event.tool,
                            preview = event.preview,
                            status = ToolActionStatus.PENDING,
                            anchorMessageId = assistantId,
                            segmentIndex = segments.size,
                            createdAt = System.currentTimeMillis(),
                        )
                        _toolActions.update { current ->
                            if (current.any { it.id == event.pendingId }) current
                            else current + toolItem
                        }
                        persistToolAction(toolItem)
                    }
                    is ChatStreamEvent.Error -> {
                        appendToolResumeError(pendingId, event.message)
                        if (assistantContent.isBlank() && segments.isEmpty()) {
                            _messages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantId) {
                                        msg.copy(content = "错误: ${event.message}")
                                    } else {
                                        msg
                                    }
                                }
                            }
                        }
                    }
                    is ChatStreamEvent.Done -> Unit
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            appendToolResumeError(pendingId, e.message ?: "继续对话失败")
        } finally {
            if (streamGeneration == activeStreamGeneration) {
                _streaming.value = false
                _streamStatusHint.value = null
                _reasoningStreaming.value = false
            }
            if (streamGeneration != activeStreamGeneration) return
            if (!isStreamActive(chat, streamGeneration)) return
            try {
                if (!receivedMessageDone) {
                    finalizeStreamMessages(
                        chat = chat,
                        tempUserId = parentId,
                        assistantId = assistantId,
                        segments = segments,
                        pendingContent = pendingContent,
                        pendingSearchSources = emptyList(),
                    )
                } else {
                    chatRepository.upsertMessages(_messages.value)
                    syncAllMessagesFromCurrent()
                    refreshBranchDisplay(chat)
                }
                loadPendingTools(chat)
                contentSyncRepository.syncAll()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun loadPendingTools(chat: ActiveChat) {
        val stored = chatRepository.listToolActions(chat.conversationId, chat.branchId).map { entity ->
            ToolActionItem(
                id = entity.id,
                conversationId = entity.conversationId,
                branchId = entity.branchId,
                tool = entity.tool,
                preview = runCatching {
                    com.google.gson.Gson().fromJson(entity.previewJson, ToolPreview::class.java)
                }.getOrDefault(ToolPreview()),
                status = runCatching { ToolActionStatus.valueOf(entity.status) }
                    .getOrDefault(ToolActionStatus.PENDING),
                anchorMessageId = entity.anchorMessageId,
                segmentIndex = entity.segmentIndex,
                resultMessage = entity.resultMessage,
                errorMessage = entity.errorMessage,
                createdAt = entity.createdAt,
            )
        }
        _toolActions.update { current ->
            val other = current.filter {
                it.conversationId != chat.conversationId || it.branchId != chat.branchId
            }
            other + stored
        }
    }

    private fun appendSegment(segments: MutableList<MessageSegment>, type: String, text: String) {
        if (text.isEmpty()) return
        if (segments.lastOrNull()?.type == type) {
            val last = segments.last()
            val merged = mergeStreamingText(last.text, text)
            if (merged == last.text) return
            segments[segments.lastIndex] = last.copy(text = merged)
        } else {
            segments.add(MessageSegment(type = type, text = text))
        }
    }

    private fun contentFromSegments(segments: List<MessageSegment>): String =
        segments.filter { it.type == "content" }.joinToString("") { it.text }

    /** 流式文本合并：模型重发整段时不拼接成双倍内容。 */
    private fun mergeStreamingText(existing: String, chunk: String): String {
        if (chunk.isEmpty()) return existing
        if (existing.isEmpty()) return chunk
        if (chunk == existing) return existing
        if (chunk.startsWith(existing)) return chunk
        if (existing.startsWith(chunk)) return existing
        if (existing.endsWith(chunk)) return existing
        return existing + chunk
    }

    /** 合并连续重复的 content 段（模型偶发整段重发）。 */
    private fun dedupeContentSegments(segments: MutableList<MessageSegment>) {
        val merged = mutableListOf<MessageSegment>()
        for (segment in segments) {
            if (segment.type == "content" && merged.lastOrNull()?.type == "content") {
                val prev = merged.last()
                val combined = mergeStreamingText(prev.text, segment.text)
                if (combined == prev.text) continue
                merged[merged.lastIndex] = prev.copy(text = collapseExactDuplicate(combined))
            } else if (segment.type == "content") {
                merged.add(segment.copy(text = collapseExactDuplicate(segment.text)))
            } else {
                merged.add(segment)
            }
        }
        segments.clear()
        segments.addAll(merged)
    }

    /** 正文与思考完全一致时只保留正文，避免同一段出现两次。 */
    private fun collapseReasoningContentOverlap(segments: MutableList<MessageSegment>) {
        val reasoning = segments.filter { it.type == "reasoning" }.joinToString("") { it.text }.trim()
        val content = segments.filter { it.type == "content" }.joinToString("") { it.text }.trim()
        if (reasoning.isNotBlank() && reasoning == content) {
            segments.removeAll { it.type == "reasoning" }
        }
    }

    private fun collapseExactDuplicate(text: String): String {
        val trimmed = text.trim()
        if (trimmed.length < 2 || trimmed.length % 2 != 0) return text
        val half = trimmed.length / 2
        return if (trimmed.substring(0, half) == trimmed.substring(half)) {
            trimmed.substring(0, half)
        } else {
            text
        }
    }

    /**
     * 合并内存消息与服务端列表：优先采用服务端 ID，保留服务端尚未落库的本地消息。
     */
    private fun mergeBranchMessages(
        local: List<MessageDto>,
        remote: List<MessageDto>,
    ): List<MessageDto> {
        if (remote.isEmpty()) return local
        val remoteIds = remote.map { it.id }.toSet()
        val mergedRemote = if (remote.any { it.role == "assistant" }) {
            remote.map { remoteMsg ->
                val localMatch = local.find { it.id == remoteMsg.id }
                    ?: local.find { it.role == "assistant" && it.content == remoteMsg.content }
                remoteMsg.copy(
                    content = preferRicherText(remoteMsg.content, localMatch?.content),
                    reasoning = remoteMsg.reasoning?.takeIf { it.isNotBlank() }
                        ?: localMatch?.reasoning?.takeIf { it.isNotBlank() },
                    segments = remoteMsg.segments?.takeIf { it.isNotEmpty() }
                        ?: localMatch?.segments?.takeIf { it.isNotEmpty() },
                )
            }
        } else {
            val remoteUsersByContent = remote
                .filter { it.role == "user" }
                .associateBy { it.content.trim() }
            val users = (remote.filter { it.role == "user" } + local.filter { it.role == "user" })
                .distinctBy { it.content.trim() }
                .map { user -> remoteUsersByContent[user.content.trim()] ?: user }
            val localAssistants = local.filter { it.role == "assistant" && it.id !in remoteIds }
            users + localAssistants
        }
        val localOnly = local.filter { msg ->
            msg.id !in remoteIds && when (msg.role) {
                "user" -> true
                "assistant" -> true
                else -> false
            }
        }
        return (mergedRemote + localOnly).distinctBy { it.id }.sortedBy { it.createdAt }
    }

    /** 批准写工具后刷新分支消息，避免乐观 ID 与服务器不一致导致 resume 静默失败。 */
    private suspend fun syncBranchMessagesForToolResume(chat: ActiveChat) {
        val refreshed = try {
            chatRepository.listMessages(chat.conversationId, chat.branchId)
        } catch (_: Exception) {
            chatRepository.listMessagesLocal(chat.conversationId, chat.branchId)
        }
        _messages.value = mergeBranchMessages(_messages.value, refreshed)
        chatRepository.upsertMessages(_messages.value)
        val lastAssistantId = _messages.value.lastOrNull { it.role == "assistant" }?.id
        if (lastAssistantId != null) {
            _toolActions.update { tools ->
                tools.map { tool ->
                    if (tool.conversationId == chat.conversationId &&
                        tool.branchId == chat.branchId &&
                        tool.anchorMessageId != null &&
                        _messages.value.none { it.id == tool.anchorMessageId }
                    ) {
                        tool.copy(anchorMessageId = lastAssistantId)
                    } else {
                        tool
                    }
                }
            }
        }
    }

    private suspend fun findAssistantForToolResume(chat: ActiveChat, pendingId: String): MessageDto? {
        fun resolveAssistant(): MessageDto? {
            val toolItem = _toolActions.value.find { it.id == pendingId }
            toolItem?.anchorMessageId?.let { anchorId ->
                _messages.value.find { it.id == anchorId }?.let { return it }
            }
            return _messages.value.lastOrNull { it.role == "assistant" }
        }
        resolveAssistant()?.let { return it }
        syncBranchMessagesForToolResume(chat)
        return resolveAssistant()
    }

    private fun toolStatusPriority(status: ToolActionStatus): Int = when (status) {
        ToolActionStatus.SUCCESS -> 4
        ToolActionStatus.EXECUTING -> 3
        ToolActionStatus.PENDING -> 2
        ToolActionStatus.FAILED -> 1
        ToolActionStatus.REJECTED -> 0
    }

    private fun applyStreamStatus(phase: String, tool: String? = null) {
        _streamStatusHint.value = when (phase) {
            "searching" -> "联网搜索中…"
            "search_no_results" -> "未找到相关网页"
            "search_unavailable" -> "联网搜索未启用"
            "thinking" -> null
            "tool" -> when (tool) {
                "web_search" -> "联网搜索中…"
                "search_notes" -> "正在搜索笔记…"
                "search_knowledge_bases" -> "正在搜索知识库…"
                else -> "正在调用工具…"
            }
            else -> _streamStatusHint.value
        }
    }

    /** 写工具已执行成功时，resume 失败不应把卡片改回 FAILED。 */
    private fun appendToolResumeError(pendingId: String, message: String) {
        updateToolAction(pendingId) { item ->
            if (item.status == ToolActionStatus.SUCCESS) {
                item.copy(errorMessage = message)
            } else {
                item.copy(status = ToolActionStatus.FAILED, errorMessage = message)
            }
        }
    }

    private fun rebindToolAnchors(oldAssistantId: String, newAssistantId: String) {
        if (oldAssistantId == newAssistantId) return
        var changed = false
        _toolActions.update { tools ->
            tools.map { tool ->
                if (tool.anchorMessageId == oldAssistantId) {
                    changed = true
                    tool.copy(anchorMessageId = newAssistantId)
                } else {
                    tool
                }
            }
        }
        if (changed) {
            viewModelScope.launch {
                _toolActions.value
                    .filter { it.anchorMessageId == newAssistantId }
                    .forEach { persistToolAction(it) }
            }
        }
    }

    /** 用户消息临时 ID 与服务端 ID 对齐时，同步工具卡片与搜索来源锚点。 */
    private fun rebindMessageAnchors(oldId: String, newId: String) {
        if (oldId == newId) return
        _messages.update { list ->
            list.map { if (it.id == oldId) it.copy(id = newId) else it }
        }
        _allMessages.update { list ->
            list.map { if (it.id == oldId) it.copy(id = newId) else it }
                .distinctBy { it.id }
                .sortedBy { it.createdAt }
        }
        var toolChanged = false
        _toolActions.update { tools ->
            tools.map { tool ->
                if (tool.anchorMessageId == oldId) {
                    toolChanged = true
                    tool.copy(anchorMessageId = newId)
                } else {
                    tool
                }
            }
        }
        if (toolChanged) {
            viewModelScope.launch {
                _toolActions.value
                    .filter { it.anchorMessageId == newId }
                    .forEach { persistToolAction(it) }
            }
        }
        _messageSearchSources.update { current ->
            val sources = current[oldId]
            if (sources == null) current else current - oldId + (newId to sources)
        }
    }

    private fun applySearchSources(messageId: String, sources: List<SearchSource>) {
        if (sources.isEmpty()) return
        _messageSearchSources.update { it + (messageId to sources) }
        viewModelScope.launch {
            chatRepository.saveMessageSearchSources(messageId, sources)
        }
    }

    private fun mergeSearchSources(
        existing: List<SearchSource>,
        incoming: List<SearchSource>,
    ): List<SearchSource> {
        val merged = linkedMapOf<String, SearchSource>()
        for (source in existing + incoming) {
            val key = source.url.ifBlank { "${source.title}:${source.snippet}" }
            merged[key] = source
        }
        return merged.values.toList()
    }

    private suspend fun loadSearchSources(conversationId: String) {
        val stored = chatRepository.loadMessageSearchSources(conversationId)
        if (stored.isNotEmpty()) {
            _messageSearchSources.update { current -> current + stored }
        }
    }

    private fun syncAllMessagesFromCurrent() {
        _allMessages.update { current ->
            val merged = current.associateBy { it.id }.toMutableMap()
            _messages.value.forEach { msg -> merged[msg.id] = msg }
            merged.values.sortedBy { it.createdAt }
        }
    }

    private fun preferRicherText(remote: String?, local: String?): String {
        val remoteText = remote.orEmpty()
        val localText = local.orEmpty()
        return if (localText.length > remoteText.length) localText else remoteText
    }

    private fun List<MessageDto>.upsertMessage(message: MessageDto): List<MessageDto> {
        val index = indexOfFirst { it.id == message.id }
        return if (index >= 0) {
            toMutableList().apply { this[index] = message }
        } else {
            this + message
        }
    }

    private fun buildStreamingAssistant(
        assistantId: String,
        chat: ActiveChat,
        parentId: String,
        segments: List<MessageSegment>,
        pendingContent: String = "",
    ): MessageDto {
        val segmentContent = contentFromSegments(segments)
        val content = segmentContent + pendingContent
        val reasoning = segments
            .filter { it.type == "reasoning" }
            .joinToString("\n\n---\n\n") { it.text }
            .takeIf { it.isNotBlank() }
        return MessageDto(
            id = assistantId,
            conversationId = chat.conversationId,
            branchId = chat.branchId,
            parentId = parentId,
            role = "assistant",
            content = content,
            reasoning = reasoning,
            segments = segments.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun updateToolAction(id: String, transform: (ToolActionItem) -> ToolActionItem) {
        var updatedItem: ToolActionItem? = null
        _toolActions.update { list ->
            list.map { item ->
                if (item.id == id) {
                    transform(item).also { updatedItem = it }
                } else {
                    item
                }
            }
        }
        updatedItem?.let { item ->
            viewModelScope.launch { persistToolAction(item) }
        }
    }

    private suspend fun persistToolAction(item: ToolActionItem) {
        chatRepository.upsertToolAction(
            id = item.id,
            conversationId = item.conversationId,
            branchId = item.branchId,
            anchorMessageId = item.anchorMessageId,
            segmentIndex = item.segmentIndex,
            tool = item.tool,
            preview = item.preview,
            status = item.status.name,
            resultMessage = item.resultMessage,
            errorMessage = item.errorMessage,
            createdAt = item.createdAt,
        )
    }

    private suspend fun applyToolSync(tool: String, result: Map<String, Any>?) {
        if (result == null) return
        when (tool) {
            "mutate_knowledge_base" -> {
                if (result["deleted"] == true) {
                    val kbId = result["kb_id"]?.toString() ?: return
                    knowledgeRepository.markDeletedFromServer(kbId)
                } else {
                    knowledgeRepository.refreshFromServer()
                }
            }
            "mutate_note" -> {
                if (result["deleted"] == true) {
                    val noteId = result["note_id"]?.toString() ?: return
                    noteRepository.markDeletedFromServer(noteId)
                } else {
                    noteRepository.applyFromToolResult(result)
                }
            }
        }
    }

    private fun buildToolSuccessMessage(
        tool: String,
        preview: ToolPreview,
        result: Map<String, Any>?,
    ): String = when {
        preview.action == "delete" -> when (tool) {
            "mutate_note" -> "笔记已删除"
            "mutate_knowledge_base" -> "知识库已删除"
            else -> "已删除"
        }
        preview.action == "create" -> when (tool) {
            "mutate_note" -> "笔记「${result?.get("title") ?: preview.after?.title ?: ""}」已创建"
            "mutate_knowledge_base" -> "知识库「${result?.get("name") ?: preview.after?.name ?: ""}」已创建"
            else -> "已创建"
        }
        preview.action == "update" -> when (tool) {
            "mutate_note" -> "笔记已更新"
            "mutate_knowledge_base" -> "知识库已更新"
            else -> "已更新"
        }
        else -> "操作已完成"
    }
}

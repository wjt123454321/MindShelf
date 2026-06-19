package com.example.mindshelf.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindshelf.R
import com.example.mindshelf.data.remote.dto.ConversationDto
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.ui.components.ChatInputBar
import com.example.mindshelf.ui.components.ConfirmDeleteDialog
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.MindShelfAlertDialog
import com.example.mindshelf.ui.components.MindShelfDropdownMenu
import com.example.mindshelf.ui.components.MindShelfDropdownMenuItem
import com.example.mindshelf.ui.components.SwipeDeleteBackground
import com.example.mindshelf.ui.voice.SpeechToTextController
import com.example.mindshelf.ui.voice.SpeechUiState
import com.example.mindshelf.ui.voice.isNetworkAvailable
import com.example.mindshelf.ui.voice.plainTextForSpeech
import com.example.mindshelf.ui.voice.rememberSpeechToTextController
import com.example.mindshelf.ui.voice.rememberTextToSpeechController
import com.example.mindshelf.ui.voice.speechErrorMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenList: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val initializing by viewModel.initializing.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val allMessages by viewModel.allMessages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val activeChannelLabel by viewModel.activeChannelLabel.collectAsStateWithLifecycle()
    val toolActions by viewModel.toolActions.collectAsStateWithLifecycle()
    val messageSearchSources by viewModel.messageSearchSources.collectAsStateWithLifecycle()
    val streamStatusHint by viewModel.streamStatusHint.collectAsStateWithLifecycle()
    val reasoningStreaming by viewModel.reasoningStreaming.collectAsStateWithLifecycle()
    val enableSearch by viewModel.enableSearch.collectAsStateWithLifecycle()
    val builtinModel by viewModel.builtinModel.collectAsStateWithLifecycle()
    val isBuiltinChannel by viewModel.isBuiltinChannel.collectAsStateWithLifecycle()
    val activeChatId = activeChat?.conversationId
    val activeBranchId = activeChat?.branchId
    val branchIds = remember(branches) { branches.map { it.id }.toSet() }
    val branchToolActions = toolActions.filter {
        it.conversationId == activeChatId && it.branchId == activeBranchId
    }
    val timeline = remember(messages, branchToolActions) {
        buildChatTimeline(messages, branchToolActions)
    }
    val autoReadReplies by viewModel.autoReadReplies.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val speechController = rememberSpeechToTextController()
    val ttsController = rememberTextToSpeechController(
        onError = {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_tts_not_available))
            }
        },
    )
    val ttsState = ttsController.state
    var speechUi by remember { mutableStateOf(SpeechUiState()) }
    var showedOfflineHint by rememberSaveable { mutableStateOf(false) }
    var wasStreaming by remember { mutableStateOf(false) }
    var stickToBottom by remember { mutableStateOf(true) }
    var scrollToBottomNonce by remember { mutableIntStateOf(0) }
    var autoScrolling by remember { mutableStateOf(false) }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<MessageDto?>(null) }
    var editText by remember { mutableStateOf("") }
    var saveToNoteContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.ensureActiveChat()
    }

    val speechIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val baseInput = speechUi.speechBaseInput
        speechUi = speechUi.copy(isListening = false)
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                val combined = if (baseInput.isBlank()) text else "$baseInput $text"
                viewModel.setInput(combined.trim())
            }
        }
    }

    fun launchIntentSpeechRecognition(baseInput: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_input))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_not_available))
            }
            return
        }
        speechUi = SpeechUiState(isListening = true, speechBaseInput = baseInput)
        try {
            speechIntentLauncher.launch(intent)
        } catch (_: Exception) {
            speechUi = speechUi.copy(isListening = false)
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_not_available))
            }
        }
    }

    fun startSpeechRecognition() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_permission_denied))
            }
            return
        }
        if (!speechController.canUseAnyRecognition()) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_not_available))
            }
            return
        }
        if (!speechController.isRecognizerAvailable()) {
            launchIntentSpeechRecognition(input)
            return
        }
        if (!showedOfflineHint && !isNetworkAvailable(context)) {
            showedOfflineHint = true
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_offline_hint))
            }
        }
        val baseInput = input
        speechUi = SpeechUiState(isListening = true, speechBaseInput = baseInput)
        try {
            speechController.start(
                onPartial = { partial ->
                    val combined = if (baseInput.isBlank()) partial else "$baseInput $partial"
                    viewModel.setInput(combined.trim())
                },
                onFinal = { final ->
                    if (final.isNotBlank()) {
                        val combined = if (baseInput.isBlank()) final else "$baseInput $final"
                        viewModel.setInput(combined.trim())
                    }
                    speechUi = speechUi.copy(isListening = false)
                },
                onListeningChange = { listening ->
                    speechUi = speechUi.copy(isListening = listening)
                },
                onError = { error ->
                    speechUi = speechUi.copy(isListening = false)
                    when (error) {
                        SpeechToTextController.ERROR_USE_INTENT_FALLBACK -> {
                            launchIntentSpeechRecognition(speechUi.speechBaseInput.ifBlank { input })
                        }
                        SpeechToTextController.ERROR_NOT_AVAILABLE -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.voice_not_available))
                            }
                        }
                        else -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(speechErrorMessage(context, error))
                            }
                        }
                    }
                },
            )
        } catch (_: Exception) {
            speechUi = speechUi.copy(isListening = false)
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_error_generic))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startSpeechRecognition()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.voice_permission_denied))
            }
        }
    }

    fun requestSpeechOrStart() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> startSpeechRecognition()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun toggleSpeech() {
        if (speechUi.isListening || speechController.isListening) {
            speechController.stop()
            speechUi = speechUi.copy(isListening = false)
        } else {
            requestSpeechOrStart()
        }
    }

    LaunchedEffect(streaming, messages, autoReadReplies) {
        if (wasStreaming && !streaming) {
            val last = messages.lastOrNull()
            if (autoReadReplies && last?.role == "assistant" && !last.content.isNullOrBlank()) {
                ttsController.speak(last.id, plainTextForSpeech(last.content))
            }
        }
        wasStreaming = streaming
    }

    LaunchedEffect(activeChatId, activeBranchId) {
        stickToBottom = true
        scrollToBottomNonce++
    }

    LaunchedEffect(initializing) {
        if (!initializing && timeline.isNotEmpty()) {
            stickToBottom = true
            scrollToBottomNonce++
        }
    }

    LifecycleResumeEffect(activeChatId, activeBranchId) {
        stickToBottom = true
        scrollToBottomNonce++
        onPauseOrDispose { }
    }

    LaunchedEffect(streaming) {
        if (streaming) stickToBottom = true
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.isAtBottom() }
            .collect { (scrolling, atBottom) ->
                if (atBottom) {
                    stickToBottom = true
                } else if (scrolling && !autoScrolling) {
                    stickToBottom = false
                }
            }
    }

    LaunchedEffect(
        scrollToBottomNonce,
        listState,
        streaming,
        streamStatusHint,
        reasoningStreaming,
        timeline.size,
        initializing,
    ) {
        if (initializing || !stickToBottom || timeline.isEmpty()) return@LaunchedEffect
        autoScrolling = true
        try {
            listState.awaitScrollToBottom(
                expectedItemCount = timeline.size,
                animated = !streaming,
            )
            snapshotFlow {
                val last = timeline.lastOrNull()
                val lastMsg = (last as? ChatTimelineItem.Message)?.message
                listOf(
                    timeline.size,
                    last?.key,
                    lastMsg?.streamingPayloadLength() ?: 0,
                    streamStatusHint,
                    reasoningStreaming,
                )
            }.collect {
                if (stickToBottom) {
                    listState.scrollToBottom(animated = !streaming)
                }
            }
        } finally {
            autoScrolling = false
        }
    }

    if (initializing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
            )
        }
        return
    }

    val chatTitle = activeChat?.title ?: "新对话"
    val currentBranch = activeChat?.let { branches.find { b -> b.id == it.branchId } }

    editingMessage?.let { message ->
        MindShelfAlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("修改消息", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editMessage(message, editText)
                        editingMessage = null
                    },
                    enabled = editText.isNotBlank(),
                ) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("取消") }
            },
        )
    }

    saveToNoteContent?.let { content ->
        SaveToNoteSheet(
            notes = notes,
            onDismiss = { saveToNoteContent = null },
            onCreateNew = {
                viewModel.saveAsNewNote(content)
                saveToNoteContent = null
            },
            onSelectNote = { note ->
                viewModel.appendToNote(note, content)
                saveToNoteContent = null
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            chatTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (branches.size > 1) {
                            Text(
                                currentBranch?.label ?: "主分支",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                activeChannelLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenList,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "对话列表",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                actions = {
                    if (activeChat != null && branches.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { branchMenuExpanded = true }) {
                                Text(
                                    currentBranch?.label ?: "分支",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            MindShelfDropdownMenu(
                                expanded = branchMenuExpanded,
                                onDismissRequest = { branchMenuExpanded = false },
                            ) {
                                branches.forEach { branch ->
                                    MindShelfDropdownMenuItem(
                                        text = branch.label,
                                        selected = branch.id == activeChat?.branchId,
                                        onClick = {
                                            viewModel.switchBranch(branch.id)
                                            branchMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.startNewConversation() },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "新对话",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (messages.isEmpty() && !initializing) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = "有什么可以帮你的？",
                        subtitle = "输入问题，AI 将基于你的知识库作答",
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(timeline, key = { it.key }) { item ->
                        when (item) {
                            is ChatTimelineItem.Message -> {
                                val msg = item.message
                                val isUser = msg.role == "user"
                                val siblings = viewModel.getSiblings(msg, messages, allMessages, branchIds)
                                val currentIndex = siblings.indexOfFirst { it.id == msg.id }.takeIf { it >= 0 } ?: 0
                                val isLastAssistant = !isUser &&
                                    msg.id == messages.lastOrNull { it.role == "assistant" }?.id &&
                                    streaming
                                val msgContent = msg.content.orEmpty()
                                val segmentContent = msg.segments
                                    ?.filter { it.type == "content" }
                                    ?.joinToString("") { it.text }
                                    .orEmpty()
                                    .ifBlank { msgContent }

                                if (isUser) {
                                    UserMessageBubble(
                                        message = msg,
                                        siblings = siblings,
                                        onEdit = {
                                            editingMessage = msg
                                            editText = msg.content
                                        },
                                        onSiblingPrev = {
                                            siblings.getOrNull(currentIndex - 1)?.let { viewModel.switchToSibling(it) }
                                        },
                                        onSiblingNext = {
                                            siblings.getOrNull(currentIndex + 1)?.let { viewModel.switchToSibling(it) }
                                        },
                                    )
                                } else {
                                    AssistantMessageBubble(
                                        message = msg,
                                        siblings = siblings,
                                        isStreaming = isLastAssistant,
                                        isStreamingReasoning = isLastAssistant && reasoningStreaming,
                                        isStreamingContent = isLastAssistant &&
                                            segmentContent.isNotBlank() &&
                                            !reasoningStreaming,
                                        statusHint = if (isLastAssistant) streamStatusHint else null,
                                        searchSources = messageSearchSources[msg.id].orEmpty(),
                                        inlineTools = inlineToolsForMessage(msg.id, branchToolActions),
                                        onApproveTool = { viewModel.confirmTool(it, true) },
                                        onRejectTool = { viewModel.confirmTool(it, false) },
                                        onRegenerate = { viewModel.regenerate(msg) },
                                        onSaveToNote = { saveToNoteContent = segmentContent.ifBlank { msgContent } },
                                        onSiblingPrev = {
                                            siblings.getOrNull(currentIndex - 1)?.let { viewModel.switchToSibling(it) }
                                        },
                                        onSiblingNext = {
                                            siblings.getOrNull(currentIndex + 1)?.let { viewModel.switchToSibling(it) }
                                        },
                                        ttsPlaying = ttsState.messageId == msg.id && ttsState.isSpeaking,
                                        ttsPaused = ttsState.messageId == msg.id && ttsState.isPaused,
                                        onPlayTts = {
                                            ttsController.speak(msg.id, plainTextForSpeech(msgContent))
                                        },
                                        onPauseTts = { ttsController.pause() },
                                        onResumeTts = { ttsController.resume() },
                                        onStopTts = { ttsController.stop() },
                                    )
                                }
                            }
                            is ChatTimelineItem.ToolAction -> {
                                ToolActionCard(
                                    item = item.action,
                                    onApprove = { viewModel.confirmTool(item.action.id, true) },
                                    onReject = { viewModel.confirmTool(item.action.id, false) },
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "回答由 AI 生成，仅供参考",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            ChatInputBar(
                value = input,
                onValueChange = viewModel::setInput,
                onSend = viewModel::send,
                enabled = !initializing,
                sending = streaming,
                isListening = speechUi.isListening,
                onMicTap = { toggleSpeech() },
                onMicPress = { if (!speechUi.isListening) requestSpeechOrStart() },
                onMicRelease = {
                    if (speechUi.isListening || speechController.isListening) {
                        speechController.stop()
                        speechUi = speechUi.copy(isListening = false)
                    }
                },
                showBuiltinOptions = isBuiltinChannel,
                webSearchEnabled = enableSearch,
                onWebSearchToggle = { viewModel.setWebSearchEnabled(!enableSearch) },
                builtinModel = builtinModel,
                onBuiltinModelChange = viewModel::setBuiltinModel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<ConversationDto?>(null) }

    LaunchedEffect(Unit) { viewModel.loadConversations() }

    deleteTarget?.let { conv ->
        ConfirmDeleteDialog(
            title = "删除对话",
            message = "删除后无法恢复，确定删除「${conv.title}」吗？",
            onConfirm = {
                viewModel.deleteConversation(conv.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("对话列表", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.startNewConversation()
                    onBack()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "新对话", modifier = Modifier.size(20.dp))
            }
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "暂无对话",
                    subtitle = "点击右下角按钮开始新对话",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val selected = conv.id == activeChat?.conversationId
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                deleteTarget = conv
                            }
                            false
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            SwipeDeleteBackground(dismissState)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.switchToConversation(conv)
                                onBack()
                            },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        conv.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatRelativeTime(conv.updatedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}

package com.example.mindshelf.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindshelf.ui.components.MarkdownEditor
import com.example.mindshelf.ui.components.MarkdownText
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import com.example.mindshelf.ui.components.ShareLinkDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    noteId: String?,
    onBack: () -> Unit,
    onOpenVersions: (String) -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val editing by viewModel.editingNote.collectAsStateWithLifecycle()
    val knowledgeBases by viewModel.knowledgeBases.collectAsStateWithLifecycle()
    val isNew = noteId == null

    var title by remember(noteId) { mutableStateOf("") }
    var content by remember(noteId) { mutableStateOf("") }
    var selectedKbIds by remember(noteId) { mutableStateOf(setOf<String>()) }
    var isEditing by remember(noteId) { mutableStateOf(isNew) }
    var kbMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var applyingHistory by remember { mutableStateOf(false) }
    var historyRevision by remember { mutableIntStateOf(0) }
    val textHistory = remember(noteId) { NoteTextHistory(NoteSnapshot("", "")) }
    val canUndo = remember(historyRevision) { textHistory.canUndo }
    val canRedo = remember(historyRevision) { textHistory.canRedo }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            viewModel.loadNote(noteId)
        } else {
            viewModel.clearEditing()
        }
    }

    LaunchedEffect(editing) {
        editing?.let {
            applyingHistory = true
            title = it.title
            content = it.content
            selectedKbIds = it.knowledgeBaseIds.toSet()
            textHistory.reset(NoteSnapshot(it.title, it.content))
            applyingHistory = false
            historyRevision++
        }
    }

    val latestTitle by rememberUpdatedState(title)
    val latestContent by rememberUpdatedState(content)
    LaunchedEffect(title, content, isEditing) {
        if (!isEditing || applyingHistory) return@LaunchedEffect
        delay(450)
        textHistory.record(NoteSnapshot(latestTitle, latestContent))
        historyRevision++
    }

    fun applySnapshot(snapshot: NoteSnapshot) {
        applyingHistory = true
        title = snapshot.title
        content = snapshot.content
        applyingHistory = false
        historyRevision++
    }

    fun saveNote(onDone: (() -> Unit)? = null) {
        if (saving) return
        saving = true
        val kbIds = selectedKbIds.toList()
        if (isNew) {
            viewModel.create(title, content, kbIds) {
                saving = false
                onDone?.invoke() ?: onBack()
            }
        } else {
            editing?.let { note ->
                viewModel.save(note.id, title, content, note.syncVersion, kbIds) {
                    saving = false
                    onDone?.invoke()
                }
            } ?: run { saving = false }
        }
    }

    if (showShare && editing != null) {
        ShareLinkDialog(
            onRequestLink = { viewModel.createShareLink(editing!!.id) },
            onDismiss = { showShare = false },
        )
    }

    Scaffold(
        topBar = {
            MindShelfTopAppBar(
                title = { Text(if (isNew) "新建笔记" else if (isEditing) "编辑" else "笔记") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (knowledgeBases.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { kbMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "知识库",
                                    tint = if (selectedKbIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            DropdownMenu(
                                expanded = kbMenuExpanded,
                                onDismissRequest = { kbMenuExpanded = false },
                            ) {
                                knowledgeBases.forEach { kb ->
                                    val selected = kb.id in selectedKbIds
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                kb.name,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                        },
                                        onClick = {
                                            selectedKbIds = if (selected) {
                                                selectedKbIds - kb.id
                                            } else {
                                                selectedKbIds + kb.id
                                            }
                                            if (!isNew && editing != null) {
                                                viewModel.save(
                                                    editing!!.id,
                                                    title,
                                                    content,
                                                    editing!!.syncVersion,
                                                    selectedKbIds.toList(),
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            if (selected) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (isEditing) {
                        IconButton(
                            onClick = { textHistory.undo()?.let { applySnapshot(it) } },
                            enabled = canUndo,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                        }
                        IconButton(
                            onClick = { textHistory.redo()?.let { applySnapshot(it) } },
                            enabled = canRedo,
                        ) {
                            Icon(Icons.Default.Redo, contentDescription = "重做")
                        }
                        IconButton(
                            onClick = { saveNote() },
                            enabled = !saving,
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(4.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "保存")
                            }
                        }
                        if (!isNew) {
                            IconButton(onClick = { isEditing = false }) {
                                Icon(Icons.Default.Visibility, contentDescription = "查看")
                            }
                        }
                    } else {
                        IconButton(onClick = { showShare = true }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                        IconButton(onClick = { onOpenVersions(editing!!.id) }) {
                            Icon(Icons.Default.History, contentDescription = "版本历史")
                        }
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!isNew && editing == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (isEditing) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (title.isEmpty()) {
                            Text(
                                "标题",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                MarkdownEditor(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 16.dp),
                    placeholder = "开始写笔记…",
                    showToolbar = true,
                    enablePreviewToggle = false,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (title.isBlank()) {
                        Text(
                            "标题",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (content.isBlank()) {
                        Text(
                            "开始写笔记…",
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MarkdownText(
                            text = content,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

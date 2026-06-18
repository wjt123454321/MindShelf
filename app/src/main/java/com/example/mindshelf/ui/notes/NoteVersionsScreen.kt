package com.example.mindshelf.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindshelf.data.remote.dto.NoteVersionDto
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import com.example.mindshelf.ui.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteVersionsScreen(
    noteId: String,
    onBack: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    var versions by remember { mutableStateOf<List<NoteVersionDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var restoreTarget by remember { mutableStateOf<NoteVersionDto?>(null) }
    val editing by viewModel.editingNote.collectAsStateWithLifecycle()

    LaunchedEffect(noteId) {
        loading = true
        error = null
        viewModel.loadNoteVersions(noteId)
            .onSuccess { versions = it; loading = false }
            .onFailure { e -> error = e.message; loading = false }
    }

    restoreTarget?.let { version ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("恢复此版本？") },
            text = { Text("当前内容将保存为新版本，笔记将回滚到所选快照。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreVersion(noteId, version.id) {
                        restoreTarget = null
                        onBack()
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("取消") }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MindShelfTopAppBar(
                title = { Text(editing?.title?.ifBlank { "版本历史" } ?: "版本历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "加载失败",
                    subtitle = error!!,
                    modifier = Modifier.padding(padding),
                )
            }
            versions.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "暂无历史版本",
                    subtitle = "编辑笔记后会自动保存版本（最多 10 条）",
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
                    items(versions, key = { it.id }) { version ->
                        VersionRow(version) { restoreTarget = version }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(version: NoteVersionDto, onRestore: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            version.title.ifBlank { "无标题" },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            formatRelativeTime(version.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            version.content.take(120).ifBlank { "（空）" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onRestore, modifier = Modifier.padding(top = 4.dp)) {
            Text("恢复此版本")
        }
    }
}

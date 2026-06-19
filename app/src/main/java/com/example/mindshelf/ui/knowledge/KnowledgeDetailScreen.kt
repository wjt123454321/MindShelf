package com.example.mindshelf.ui.knowledge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.MindShelfAlertDialog
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import com.example.mindshelf.ui.components.ShareLinkDialog
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeDetailScreen(
    kbId: String,
    kbName: String,
    onBack: () -> Unit,
    onOpenNote: (NoteDto) -> Unit,
    viewModel: KnowledgeViewModel = hiltViewModel(),
) {
    val notes by viewModel.kbNotes.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val kb = items.find { it.id == kbId }
    val displayName = kb?.name ?: kbName
    val displayDescription = kb?.description.orEmpty()

    var showEditDialog by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }

    LaunchedEffect(kbId) { viewModel.loadKbNotes(kbId) }

    if (showEditDialog) {
        MindShelfAlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("编辑知识库", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("描述") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.update(kbId, editName.trim(), editDescription.trim()) {
                            showEditDialog = false
                        }
                    },
                    enabled = editName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            },
        )
    }

    if (showShare) {
        ShareLinkDialog(
            onRequestLink = { viewModel.createShareLink(kbId) },
            onDismiss = { showShare = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MindShelfTopAppBar(
                title = {
                    Column {
                        Text(displayName, style = MaterialTheme.typography.titleMedium)
                        if (displayDescription.isNotBlank()) {
                            Text(
                                displayDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showShare = true }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                    IconButton(
                        onClick = {
                            editName = displayName
                            editDescription = displayDescription
                            showEditDialog = true
                        },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                },
            )
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.Note,
                    title = "暂无笔记",
                    subtitle = "可在编辑笔记时加入此知识库",
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(notes, key = { it.id }) { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { onOpenNote(note) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                note.title.ifBlank { "无标题" },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                note.content.take(80),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

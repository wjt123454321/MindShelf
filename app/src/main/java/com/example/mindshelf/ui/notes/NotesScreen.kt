package com.example.mindshelf.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.ui.components.ConfirmDeleteDialog
import com.example.mindshelf.ui.components.SwipeDeleteBackground
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.MindShelfTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    onEditNote: (NoteDto?) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<NoteDto?>(null) }
    var batchDeleteCount by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    deleteTarget?.let { note ->
        ConfirmDeleteDialog(
            title = "删除笔记",
            message = "删除后将移入回收站，确定删除「${note.title.ifBlank { "无标题" }}」吗？",
            onConfirm = {
                viewModel.delete(note.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (batchDeleteCount > 0) {
        ConfirmDeleteDialog(
            title = "批量删除笔记",
            message = "确定删除选中的 $batchDeleteCount 篇笔记吗？删除后将移入回收站。",
            onConfirm = {
                viewModel.deleteAll(selectedIds.toList())
                selectedIds = emptySet()
                selectionMode = false
                batchDeleteCount = 0
            },
            onDismiss = { batchDeleteCount = 0 },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MindShelfTopAppBar(
                title = { Text(if (selectionMode) "已选 ${selectedIds.size} 项" else "笔记") },
                actions = {
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == notes.size) {
                                    emptySet()
                                } else {
                                    notes.map { it.id }.toSet()
                                }
                            },
                        ) {
                            Text(if (selectedIds.size == notes.size) "取消全选" else "全选")
                        }
                        IconButton(
                            onClick = { batchDeleteCount = selectedIds.size },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) { Text("取消") }
                    } else if (notes.isNotEmpty()) {
                        TextButton(onClick = { selectionMode = true }) { Text("选择") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { onEditNote(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "新建")
                }
            }
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
                    subtitle = "点击右下角按钮创建第一篇笔记",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    val selected = selectedIds.contains(note.id)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (!selectionMode && value == SwipeToDismissBoxValue.EndToStart) {
                                deleteTarget = note
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
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                selectedIds = if (selected) {
                                                    selectedIds - note.id
                                                } else {
                                                    selectedIds + note.id
                                                }
                                            } else {
                                                onEditNote(note)
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedIds = setOf(note.id)
                                            }
                                        },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (selectionMode) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) {
                                                selectedIds + note.id
                                            } else {
                                                selectedIds - note.id
                                            }
                                        },
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .padding(end = 12.dp)
                                            .size(width = 4.dp, height = 40.dp)
                                            .background(
                                                MaterialTheme.colorScheme.tertiary,
                                                RoundedCornerShape(2.dp),
                                            ),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        note.title.ifBlank { "无标题" },
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        note.content.take(80),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
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

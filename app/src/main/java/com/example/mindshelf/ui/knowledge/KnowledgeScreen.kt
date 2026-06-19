package com.example.mindshelf.ui.knowledge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.mindshelf.data.remote.dto.KnowledgeBaseDto
import com.example.mindshelf.ui.components.ConfirmDeleteDialog
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.MindShelfAlertDialog
import com.example.mindshelf.ui.components.MindShelfDropdownMenu
import com.example.mindshelf.ui.components.MindShelfDropdownMenuDivider
import com.example.mindshelf.ui.components.MindShelfDropdownMenuItem
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import com.example.mindshelf.ui.components.SwipeDeleteBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KnowledgeScreen(
    onOpenKb: (KnowledgeBaseDto) -> Unit,
    viewModel: KnowledgeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<KnowledgeBaseDto?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<KnowledgeBaseDto?>(null) }
    var batchDeleteCount by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var menuKbId by remember { mutableStateOf<String?>(null) }

    deleteTarget?.let { kb ->
        ConfirmDeleteDialog(
            title = "删除知识库",
            message = "删除后将移入回收站，确定删除「${kb.name}」吗？",
            onConfirm = {
                viewModel.delete(kb.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (batchDeleteCount > 0) {
        ConfirmDeleteDialog(
            title = "批量删除知识库",
            message = "确定删除选中的 $batchDeleteCount 个知识库吗？删除后将移入回收站。",
            onConfirm = {
                viewModel.deleteAll(selectedIds.toList())
                selectedIds = emptySet()
                selectionMode = false
                batchDeleteCount = 0
            },
            onDismiss = { batchDeleteCount = 0 },
        )
    }

    editTarget?.let { kb ->
        MindShelfAlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("编辑知识库", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.update(kb.id, editName.trim(), editDescription.trim()) {
                            editTarget = null
                        }
                    },
                    enabled = editName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("取消") }
            },
        )
    }

    if (showCreateDialog) {
        MindShelfAlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建知识库", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.create(createName.trim())
                        createName = ""
                        showCreateDialog = false
                    },
                    enabled = createName.isNotBlank(),
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MindShelfTopAppBar(
                title = { Text(if (selectionMode) "已选 ${selectedIds.size} 项" else "知识库") },
                actions = {
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == items.size) {
                                    emptySet()
                                } else {
                                    items.map { it.id }.toSet()
                                }
                            },
                        ) {
                            Text(if (selectedIds.size == items.size) "取消全选" else "全选")
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
                    } else if (items.isNotEmpty()) {
                        TextButton(onClick = { selectionMode = true }) { Text("选择") }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建")
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.Folder,
                    title = "暂无知识库",
                    subtitle = "点击右下角按钮创建第一个知识库",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(items, key = { it.id }) { kb ->
                    val selected = selectedIds.contains(kb.id)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (!selectionMode && value == SwipeToDismissBoxValue.EndToStart) {
                                deleteTarget = kb
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
                                                    selectedIds - kb.id
                                                } else {
                                                    selectedIds + kb.id
                                                }
                                            } else {
                                                onOpenKb(kb)
                                            }
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selectedIds = setOf(kb.id)
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
                                                selectedIds + kb.id
                                            } else {
                                                selectedIds - kb.id
                                            }
                                        },
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .padding(end = 12.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        kb.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (kb.description.isNotBlank()) {
                                        Text(
                                            kb.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    Text(
                                        "${kb.noteCount} 篇笔记",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                if (!selectionMode) {
                                    Box {
                                        IconButton(onClick = { menuKbId = kb.id }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                                        }
                                        MindShelfDropdownMenu(
                                            expanded = menuKbId == kb.id,
                                            onDismissRequest = { menuKbId = null },
                                        ) {
                                            MindShelfDropdownMenuItem(
                                                text = "编辑",
                                                onClick = {
                                                    editTarget = kb
                                                    editName = kb.name
                                                    editDescription = kb.description
                                                    menuKbId = null
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Edit, contentDescription = null)
                                                },
                                            )
                                            MindShelfDropdownMenuDivider()
                                            MindShelfDropdownMenuItem(
                                                text = "删除",
                                                destructive = true,
                                                onClick = {
                                                    deleteTarget = kb
                                                    menuKbId = null
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                    )
                                                },
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
    }
}

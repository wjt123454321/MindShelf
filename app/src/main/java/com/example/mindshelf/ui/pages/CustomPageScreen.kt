package com.example.mindshelf.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.example.mindshelf.ui.components.ConfirmDeleteDialog
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.MindShelfAlertDialog
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import com.example.mindshelf.ui.components.ShareLinkDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPageScreen(
    pageId: String,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit = {},
    viewModel: CustomPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pageId) { viewModel.load(pageId) }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    if (uiState.showRenameDialog) {
        var renameText by remember(uiState.page?.name) {
            mutableStateOf(uiState.page?.name.orEmpty())
        }
        MindShelfAlertDialog(
            onDismissRequest = { viewModel.dismissRenameDialog() },
            title = { Text("重命名页面", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("页面名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renamePage(renameText) }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameDialog() }) {
                    Text("取消")
                }
            },
        )
    }

    if (uiState.showShareDialog) {
        ShareLinkDialog(
            onRequestLink = { viewModel.createShareLink() },
            onDismiss = { viewModel.dismissShare() },
        )
    }

    if (uiState.showDeleteDialog) {
        ConfirmDeleteDialog(
            title = "删除页面",
            message = "删除后将移入回收站，确定删除「${uiState.page?.name ?: "页面"}」吗？",
            onConfirm = {
                viewModel.deletePage()
                onBack()
            },
            onDismiss = { viewModel.dismissDeleteConfirm() },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MindShelfTopAppBar(
                title = { Text(uiState.page?.name ?: "自定义页面") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.page != null) {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(
                                if (uiState.editMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (uiState.editMode) "完成编辑" else "编辑",
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.requestShare() }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                    IconButton(onClick = { viewModel.showDeleteConfirm() }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.page == null -> {
                EmptyState(
                    icon = Icons.Default.PushPin,
                    title = "页面不存在",
                    subtitle = "该页面可能已被删除",
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            else -> {
                val page = uiState.page!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("固定到底栏", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = page.pinned, onCheckedChange = { viewModel.togglePinned() })
                    }
                    if (uiState.editMode) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "编辑模式",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.showRenameDialog() }) {
                                Text("重命名")
                            }
                        }
                    }
                    PageRenderer(
                        schema = page.schemaJson,
                        bindings = page.dataBindings,
                        noteLoader = { null },
                        editable = uiState.editMode,
                        onChecklistToggle = { key, id, done ->
                            viewModel.toggleChecklistItem(key, id, done)
                        },
                        onChecklistTextChange = { key, id, text ->
                            viewModel.updateChecklistItemText(key, id, text)
                        },
                        onChecklistAdd = { key -> viewModel.addChecklistItem(key) },
                        onChecklistRemove = { key, id -> viewModel.removeChecklistItem(key, id) },
                        onTextBindingChange = { key, text ->
                            viewModel.updateTextBinding(key, text)
                        },
                        onTableHeaderChange = { key, col, value ->
                            viewModel.updateTableHeader(key, col, value)
                        },
                        onTableCellChange = { key, row, col, value ->
                            viewModel.updateTableCell(key, row, col, value)
                        },
                        onTableAddRow = { key -> viewModel.addTableRow(key) },
                        onTableRemoveRow = { key, row -> viewModel.removeTableRow(key, row) },
                        onTableAddColumn = { key -> viewModel.addTableColumn(key) },
                        onTableRemoveColumn = { key, col -> viewModel.removeTableColumn(key, col) },
                        onOpenNote = onOpenNote,
                        loadedNotes = uiState.loadedNotes,
                    )
                }
            }
        }
    }
}

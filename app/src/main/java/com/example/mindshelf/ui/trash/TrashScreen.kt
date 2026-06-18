package com.example.mindshelf.ui.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindshelf.data.remote.dto.TrashItemDto
import com.example.mindshelf.ui.components.ConfirmDeleteDialog
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.ListSkeleton
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import com.example.mindshelf.ui.util.formatRelativeTime
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var purgeTarget = remember { androidx.compose.runtime.mutableStateOf<TrashItemDto?>(null) }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    if (purgeTarget.value != null) {
        ConfirmDeleteDialog(
            title = "永久删除",
            message = "此操作不可恢复，确定永久删除吗？",
            onConfirm = {
                purgeTarget.value?.let { viewModel.purge(it) }
                purgeTarget.value = null
            },
            onDismiss = { purgeTarget.value = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MindShelfTopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ListSkeleton(itemCount = 4, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                uiState.error != null -> {
                    EmptyState(
                        icon = Icons.Default.DeleteForever,
                        title = "加载失败",
                        subtitle = uiState.error!!,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                uiState.items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.DeleteForever,
                        title = "回收站为空",
                        subtitle = "删除的笔记与知识库会保留 30 天",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(uiState.items, key = { "${it.entityType}-${it.entity["id"]}" }) { item ->
                            TrashItemCard(
                                item = item,
                                onRestore = { viewModel.restore(item) },
                                onPurge = { purgeTarget.value = item },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashItemCard(
    item: TrashItemDto,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val isNote = item.entityType == "note"
    val title = when {
        isNote -> item.entity["title"]?.toString().orEmpty().ifBlank { "无标题笔记" }
        else -> item.entity["name"]?.toString().orEmpty().ifBlank { "知识库" }
    }
    val daysLeft = TimeUnit.MILLISECONDS.toDays(item.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                if (isNote) Icons.Default.Note else Icons.Default.Folder,
                contentDescription = null,
                tint = if (isNote) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "删除于 ${formatRelativeTime(item.deletedAt)} · 剩余约 ${daysLeft} 天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                TextButton(onClick = onRestore) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("恢复")
                }
                TextButton(onClick = onPurge) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

package com.example.mindshelf.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindshelf.data.remote.dto.CustomPageDto
import com.example.mindshelf.ui.components.ConfirmDeleteDialog
import com.example.mindshelf.ui.components.EmptyState
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import com.example.mindshelf.ui.components.SwipeDeleteBackground
import com.example.mindshelf.ui.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesListScreen(
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    viewModel: PagesListViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<CustomPageDto?>(null) }

    deleteTarget?.let { page ->
        ConfirmDeleteDialog(
            title = "删除页面",
            message = "删除后将移入回收站，确定删除「${page.name}」吗？",
            onConfirm = {
                viewModel.deletePage(page.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MindShelfTopAppBar(
                title = { Text("自定义页面") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createPage(onOpenPage) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建页面", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        if (pages.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Dashboard,
                title = "暂无自定义页面",
                subtitle = "可通过 AI 对话创建，或点击右下角新建",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                items(pages, key = { it.id }) { page ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                deleteTarget = page
                                false
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { SwipeDeleteBackground(dismissState) },
                        enableDismissFromStartToEnd = false,
                    ) {
                        PageListItem(
                            page = page,
                            onOpen = { onOpenPage(page.id) },
                            onTogglePin = { viewModel.togglePinned(page) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageListItem(
    page: CustomPageDto,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(page.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "更新于 ${formatRelativeTime(page.updatedAt)}" +
                        if (page.pinned) " · 已固定" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = if (page.pinned) "取消固定" else "固定到底栏",
                    tint = if (page.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

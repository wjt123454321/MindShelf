package com.example.mindshelf.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.mindshelf.ui.components.MindShelfTopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mindshelf.BuildConfig
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.example.mindshelf.ui.components.ListSkeleton
import com.example.mindshelf.ui.components.SyncConflictDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenPages: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    uiState.activeConflict?.let { conflict ->
        SyncConflictDialog(
            conflict = conflict,
            onResolveLocal = { viewModel.resolveConflict(conflict, "local") },
            onResolveRemote = { viewModel.resolveConflict(conflict, "remote") },
            onDismiss = { },
        )
    }

    androidx.compose.runtime.LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) onLogout()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MindShelfTopAppBar(title = { Text("我的") })
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                uiState.loading && uiState.user == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ListSkeleton(itemCount = 3, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
                uiState.error != null && uiState.user == null -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.loadUser() }) {
                            Text("重试")
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
                else -> {
                    val user = uiState.user
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            val displayName = user?.username?.takeIf { it.isNotBlank() }
                                ?: user?.email.orEmpty()
                            val initial = displayName.firstOrNull()?.uppercaseChar()?.toString()
                            if (initial.isNullOrBlank()) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    initial,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            user?.username ?: "未设置用户名",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (!user?.email.isNullOrBlank()) {
                            Text(
                                user!!.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }

                        Spacer(Modifier.height(28.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column {
                                ProfileListItem(
                                    icon = Icons.Filled.Email,
                                    label = "邮箱",
                                    value = user?.email ?: "—",
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ProfileListItem(
                                    icon = Icons.Filled.Person,
                                    label = "用户名",
                                    value = user?.username ?: "未设置",
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ProfileListItem(
                                    icon = Icons.Filled.Settings,
                                    label = "AI 服务",
                                    value = "内置 / 自定义 API",
                                    onClick = onOpenAiSettings,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ListItem(
                                    headlineContent = { Text("云同步", style = MaterialTheme.typography.labelMedium) },
                                    supportingContent = {
                                        Text(
                                            if (uiState.cloudSyncEnabled) "已开启，登录后双向同步" else "仅本地，不同步云端",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Filled.Cloud,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = uiState.cloudSyncEnabled,
                                            onCheckedChange = { viewModel.setCloudSyncEnabled(it) },
                                        )
                                    },
                                )
                                if (uiState.cloudSyncEnabled) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                    ProfileListItem(
                                        icon = Icons.Filled.Sync,
                                        label = "立即同步",
                                        value = if (uiState.syncing) "同步中…" else "拉取并推送本地变更",
                                        onClick = { if (!uiState.syncing) viewModel.syncNow() },
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ProfileListItem(
                                    icon = Icons.Filled.Dashboard,
                                    label = "自定义页面",
                                    value = "AI 创建或管理 Schema 页面",
                                    onClick = onOpenPages,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ProfileListItem(
                                    icon = Icons.Filled.Delete,
                                    label = "回收站",
                                    value = "已删除内容保留 30 天",
                                    onClick = onOpenTrash,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                ProfileListItem(
                                    icon = Icons.Filled.Info,
                                    label = "版本",
                                    value = "MindShelf ${BuildConfig.VERSION_NAME}",
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                        OutlinedButton(
                            onClick = { viewModel.logout(onLogout) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                ),
                            ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("退出登录", modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        headlineContent = { Text(label, style = MaterialTheme.typography.labelMedium) },
        supportingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

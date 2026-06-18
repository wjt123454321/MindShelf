package com.example.mindshelf.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.platform.LocalUriHandler
import com.example.mindshelf.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.ToolContentSnapshot
import com.example.mindshelf.data.remote.dto.ToolPreview
import com.example.mindshelf.ui.components.MarkdownText
import com.example.mindshelf.data.repository.SearchSource
import com.example.mindshelf.ui.theme.chatUserBubbleColor

private val UserBubbleShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)

@Composable
fun UserMessageBubble(
    message: MessageDto,
    siblings: List<MessageDto>,
    onEdit: () -> Unit,
    onSiblingPrev: () -> Unit,
    onSiblingNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = siblings.indexOfFirst { it.id == message.id }.takeIf { it >= 0 } ?: 0
    val hasSiblings = siblings.size > 1

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                ),
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "修改",
                    modifier = Modifier.size(15.dp),
                )
            }
            Surface(
                shape = UserBubbleShape,
                color = chatUserBubbleColor(),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    message.content.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (hasSiblings) {
            SiblingNavigator(
                currentIndex = currentIndex,
                total = siblings.size,
                onPrev = onSiblingPrev,
                onNext = onSiblingNext,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}

@Composable
fun AssistantMessageBubble(
    message: MessageDto,
    siblings: List<MessageDto>,
    isStreaming: Boolean,
    isStreamingReasoning: Boolean,
    isStreamingContent: Boolean,
    searchSources: List<SearchSource> = emptyList(),
    inlineTools: List<ToolActionItem> = emptyList(),
    onApproveTool: (String) -> Unit = {},
    onRejectTool: (String) -> Unit = {},
    statusHint: String? = null,
    onRegenerate: () -> Unit,
    onSaveToNote: () -> Unit,
    onSiblingPrev: () -> Unit,
    onSiblingNext: () -> Unit,
    ttsPlaying: Boolean = false,
    ttsPaused: Boolean = false,
    onPlayTts: () -> Unit = {},
    onPauseTts: () -> Unit = {},
    onResumeTts: () -> Unit = {},
    onStopTts: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentIndex = siblings.indexOfFirst { it.id == message.id }.takeIf { it >= 0 } ?: 0
    val hasSiblings = siblings.size > 1
    val segments = message.segments?.filter { it.text.isNotBlank() }.orEmpty()
    val reasoning = message.reasoning.orEmpty()
    val content = message.content.orEmpty()
    val hasReply = content.isNotBlank() ||
        segments.any { it.type == "content" && it.text.isNotBlank() }
    val hasReasoning = segments.any { it.type == "reasoning" } ||
        reasoning.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (searchSources.isNotEmpty()) {
            SearchSourcesRow(sources = searchSources)
        }
        if (segments.isNotEmpty()) {
            for (segmentIndex in 0..segments.size) {
                inlineTools.filter { (it.segmentIndex ?: segments.size) == segmentIndex }.forEach { tool ->
                    ToolActionCard(
                        item = tool,
                        onApprove = { onApproveTool(tool.id) },
                        onReject = { onRejectTool(tool.id) },
                    )
                }
                if (segmentIndex < segments.size) {
                    val segment = segments[segmentIndex]
                    when (segment.type) {
                        "reasoning" -> CollapsibleReasoningSection(
                            reasoning = segment.text,
                            roundLabel = null,
                            hasReply = hasReply,
                            isStreamingReasoning = isStreamingReasoning &&
                                segmentIndex == segments.lastIndex &&
                                segment.type == "reasoning",
                        )
                        "content" -> MarkdownText(
                            text = segment.text,
                            showCursor = isStreamingContent &&
                                segmentIndex == segments.lastIndex &&
                                segment.type == "content",
                        )
                    }
                }
            }
        } else {
            inlineTools.filter { it.segmentIndex == null || it.segmentIndex == 0 }.forEach { tool ->
                ToolActionCard(
                    item = tool,
                    onApprove = { onApproveTool(tool.id) },
                    onReject = { onRejectTool(tool.id) },
                )
            }
            if (isStreaming && isStreamingReasoning && !hasReasoning) {
                CollapsibleReasoningSection(
                    reasoning = "",
                    roundLabel = null,
                    hasReply = hasReply,
                    isStreamingReasoning = true,
                )
            }
            val reasoningRounds = reasoning.split("\n\n---\n\n").filter { it.isNotBlank() }
            reasoningRounds.forEachIndexed { index, round ->
                CollapsibleReasoningSection(
                    reasoning = round,
                    roundLabel = null,
                    hasReply = hasReply,
                    isStreamingReasoning = isStreamingReasoning &&
                        index == reasoningRounds.lastIndex,
                )
            }
            if (hasReply || isStreamingContent) {
                MarkdownText(
                    text = content,
                    showCursor = isStreamingContent,
                )
            }
            inlineTools.filter { it.segmentIndex != null && it.segmentIndex!! > 0 }.forEach { tool ->
                ToolActionCard(
                    item = tool,
                    onApprove = { onApproveTool(tool.id) },
                    onReject = { onRejectTool(tool.id) },
                )
            }
        }
        if (isStreaming && !statusHint.isNullOrBlank() && shouldShowStreamingStatusHint(statusHint)) {
            StreamingStatusHint(text = statusHint)
        } else if (!hasReply && !hasReasoning && !isStreamingContent && !isStreamingReasoning) {
            if (searchSources.isNotEmpty() && isStreaming) {
                StreamingStatusHint(text = "正在整理搜索结果…")
            }
        }
        if (hasSiblings) {
            SiblingNavigator(
                currentIndex = currentIndex,
                total = siblings.size,
                onPrev = onSiblingPrev,
                onNext = onSiblingNext,
            )
        }
        if (!isStreaming && message.content.orEmpty().isNotBlank()) {
            AssistantActionBar(
                onRegenerate = onRegenerate,
                onSaveToNote = onSaveToNote,
                ttsPlaying = ttsPlaying,
                ttsPaused = ttsPaused,
                onPlayTts = onPlayTts,
                onPauseTts = onPauseTts,
                onResumeTts = onResumeTts,
                onStopTts = onStopTts,
            )
        }
    }
}

private fun shouldShowStreamingStatusHint(hint: String): Boolean {
    if (hint.contains("思考")) return false
    return hint.contains("搜索") ||
        hint.contains("联网") ||
        hint.contains("整理") ||
        hint.contains("工具") ||
        hint.contains("笔记") ||
        hint.contains("知识库")
}

@Composable
private fun StreamingStatusHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    val isSearch = text.contains("搜索") || text.contains("联网")
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSearch) {
            Icon(
                Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchSourcesRow(
    sources: List<SearchSource>,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var sectionExpanded by remember(sources) { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { sectionExpanded = !sectionExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "联网搜索 · ${sources.size} 个来源",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (sectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (sectionExpanded) "收起" else "展开",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sectionExpanded) {
            sources.forEachIndexed { index, source ->
                var itemExpanded by remember(source.url) { mutableStateOf(false) }
                val preview = source.content.takeIf { it.isNotBlank() }
                    ?: source.snippet.takeIf { it.isNotBlank() }
                val canExpand = preview != null && preview.length > 80

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (canExpand) {
                                    Modifier.clickable { itemExpanded = !itemExpanded }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${index + 1}. ${source.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (itemExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (canExpand) {
                            Icon(
                                if (itemExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (itemExpanded) "收起" else "展开",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        source.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { uriHandler.openUri(source.url) },
                    )
                    if (source.snippet.isNotBlank()) {
                        Text(
                            source.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (itemExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (source.content.isNotBlank() && (itemExpanded || source.snippet.isBlank())) {
                        Text(
                            source.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            maxLines = if (itemExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleReasoningSection(
    reasoning: String,
    roundLabel: String? = null,
    hasReply: Boolean,
    isStreamingReasoning: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(reasoning, roundLabel) { mutableStateOf(!hasReply) }

    LaunchedEffect(hasReply, isStreamingReasoning) {
        if (hasReply && !isStreamingReasoning) expanded = false
    }

    val showExpanded = if (!hasReply && isStreamingReasoning) true else expanded
    val sectionTitle = roundLabel ?: "思考过程"

    Column(modifier = modifier.fillMaxWidth()) {
        if (hasReply) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    if (showExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showExpanded) "收起思考" else "展开思考",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    sectionTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                if (roundLabel != null) "$roundLabel…" else "思考中…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        AnimatedVisibility(
            visible = showExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MarkdownText(
                    text = reasoning,
                    modifier = Modifier.padding(10.dp),
                    showCursor = isStreamingReasoning,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SiblingNavigator(
    currentIndex: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(
            onClick = onPrev,
            enabled = currentIndex > 0,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一分支",
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            "${currentIndex + 1}/$total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = onNext,
            enabled = currentIndex < total - 1,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一分支",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AssistantActionBar(
    onRegenerate: () -> Unit,
    onSaveToNote: () -> Unit,
    ttsPlaying: Boolean,
    ttsPaused: Boolean,
    onPlayTts: () -> Unit,
    onPauseTts: () -> Unit,
    onResumeTts: () -> Unit,
    onStopTts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (ttsPlaying && !ttsPaused) {
            IconButton(
                onClick = onPauseTts,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.voice_pause),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onStopTts,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.voice_stop),
                    modifier = Modifier.size(16.dp),
                )
            }
        } else if (ttsPaused) {
            IconButton(
                onClick = onResumeTts,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.voice_resume),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onStopTts,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.voice_stop),
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            IconButton(
                onClick = onPlayTts,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                ),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.voice_play),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        IconButton(
            onClick = onRegenerate,
            modifier = Modifier.size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            ),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "重新生成",
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(
            onClick = onSaveToNote,
            modifier = Modifier.size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            ),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "保存为笔记",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun ToolActionCard(
    item: ToolActionItem,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = toolActionTitle(item.tool, item.preview)
    val borderColor = when (item.status) {
        ToolActionStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        ToolActionStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
        ToolActionStatus.REJECTED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ToolPreviewContent(item.preview)
            when (item.status) {
                ToolActionStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onApprove,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("批准")
                        }
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("拒绝")
                        }
                    }
                }
                ToolActionStatus.EXECUTING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "正在执行…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ToolActionStatus.SUCCESS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            item.resultMessage ?: "操作已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        item.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                ToolActionStatus.REJECTED -> {
                    Text(
                        item.resultMessage ?: "已取消",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToolActionStatus.FAILED -> {
                    Text(
                        item.errorMessage ?: "操作失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageToolPreview(preview: ToolPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            when (preview.action) {
                "create" -> "将创建自定义页面"
                "update" -> "页面变更预览"
                "delete" -> "将删除自定义页面"
                else -> "页面操作预览"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (preview.action == "delete") {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        preview.name?.takeIf { it.isNotBlank() }?.let {
            PreviewField(label = "名称", value = it, highlight = preview.action != "delete")
        }
        preview.schemaSummary?.takeIf { it.isNotBlank() }?.let {
            PreviewField(label = "组件", value = it, highlight = preview.action == "create")
        }
        preview.pinned?.takeIf { it }?.let {
            Text(
                "将固定到底栏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ToolPreviewContent(preview: ToolPreview) {
    if (preview.pageId != null || preview.schemaSummary != null) {
        PageToolPreview(preview)
        return
    }
    when (preview.action) {
        "create" -> preview.after?.let {
            CreatePreview(snapshot = it, isNote = it.title != null || preview.noteId != null)
        }
        "update" -> {
            preview.before?.let { before ->
                preview.after?.let { after ->
                    UpdatePreview(
                        before = before,
                        after = after,
                        isNote = before.title != null || after.title != null,
                    )
                }
            }
        }
        "delete" -> preview.before?.let {
            DeletePreview(snapshot = it, isNote = it.title != null)
        }
    }
}

@Composable
private fun CreatePreview(snapshot: ToolContentSnapshot, isNote: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (isNote) "将创建笔记" else "将创建知识库",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isNote) {
            snapshot.title?.takeIf { it.isNotBlank() }?.let {
                PreviewField(label = "标题", value = it, highlight = true)
            }
            snapshot.content?.takeIf { it.isNotBlank() }?.let {
                PreviewField(label = "正文", value = it, highlight = true)
            }
        } else {
            snapshot.name?.takeIf { it.isNotBlank() }?.let {
                PreviewField(label = "名称", value = it, highlight = true)
            }
            snapshot.description?.takeIf { it.isNotBlank() }?.let {
                PreviewField(label = "描述", value = it, highlight = true)
            }
        }
    }
}

@Composable
private fun UpdatePreview(
    before: ToolContentSnapshot,
    after: ToolContentSnapshot,
    isNote: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (isNote) "笔记变更预览" else "知识库变更预览",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isNote) {
            FieldDiff(label = "标题", before = before.title.orEmpty(), after = after.title.orEmpty())
            FieldDiff(label = "正文", before = before.content.orEmpty(), after = after.content.orEmpty())
        } else {
            FieldDiff(label = "名称", before = before.name.orEmpty(), after = after.name.orEmpty())
            FieldDiff(
                label = "描述",
                before = before.description.orEmpty(),
                after = after.description.orEmpty(),
            )
        }
    }
}

@Composable
private fun DeletePreview(snapshot: ToolContentSnapshot, isNote: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (isNote) "将删除笔记" else "将删除知识库",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (isNote) {
            snapshot.title?.takeIf { it.isNotBlank() }?.let {
                PreviewField(label = "标题", value = it, highlight = false, removed = true)
            }
        } else {
            snapshot.name?.takeIf { it.isNotBlank() }?.let {
                PreviewField(label = "名称", value = it, highlight = false, removed = true)
            }
        }
    }
}

@Composable
private fun FieldDiff(
    label: String,
    before: String,
    after: String,
) {
    if (before == after) return
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (before.isNotBlank()) {
        DiffLine(text = before, removed = true)
    }
    if (after.isNotBlank()) {
        DiffLine(text = after, removed = false)
    }
}

@Composable
private fun PreviewField(
    label: String,
    value: String,
    highlight: Boolean,
    removed: Boolean = false,
) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CollapsiblePreviewText(
        text = value,
        highlight = highlight,
        removed = removed,
    )
}

@Composable
private fun DiffLine(
    text: String,
    removed: Boolean,
) {
    CollapsiblePreviewText(
        text = text,
        highlight = !removed,
        removed = removed,
        prefix = if (removed) "− " else "+ ",
    )
}

@Composable
private fun CollapsiblePreviewText(
    text: String,
    highlight: Boolean,
    removed: Boolean = false,
    prefix: String = "",
    collapsedMaxLines: Int = 3,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val lineCount = text.lineSequence().count()
    val canExpand = lineCount > collapsedMaxLines || text.length > 180
    val background = when {
        removed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        highlight -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = background,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canExpand) {
                        Modifier.clickable { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                prefix + text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded || !canExpand) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canExpand) {
            Text(
                if (expanded) "收起预览" else "展开完整预览",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

private fun toolActionTitle(tool: String, preview: ToolPreview): String {
    val target = when (tool) {
        "mutate_note" -> "笔记"
        "mutate_knowledge_base" -> "知识库"
        "mutate_custom_page" -> "自定义页面"
        else -> "内容"
    }
    return when (preview.action) {
        "create" -> "AI 请求创建$target"
        "update" -> "AI 请求修改$target"
        "delete" -> "AI 请求删除$target"
        else -> "AI 请求操作$target"
    }
}

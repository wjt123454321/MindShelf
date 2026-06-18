package com.example.mindshelf.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import com.example.mindshelf.data.remote.dto.MessageDto
import kotlinx.coroutines.delay

private const val BOTTOM_SCROLL_THRESHOLD = 80
private const val MAX_BOTTOM_SCROLL_OFFSET = 10_000

/** 流式输出时用于触发滚动的文本规模（content / reasoning / segments 均计入）。 */
fun MessageDto.streamingPayloadLength(): Int {
    val segmentChars = segments?.sumOf { it.text.length } ?: 0
    return maxOf(
        content.orEmpty().length,
        reasoning.orEmpty().length,
        segmentChars,
    )
}

fun LazyListState.isAtBottom(threshold: Int = BOTTOM_SCROLL_THRESHOLD): Boolean {
    val layoutInfo = layoutInfo
    val total = layoutInfo.totalItemsCount
    if (total == 0) return true
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return false
    if (lastVisible.index < total - 1) return false
    val itemEnd = lastVisible.offset + lastVisible.size
    val viewportEnd = layoutInfo.viewportEndOffset
    return itemEnd <= viewportEnd + threshold
}

suspend fun LazyListState.scrollToBottom(animated: Boolean = true) {
    val index = layoutInfo.totalItemsCount - 1
    if (index < 0) return
    try {
        if (animated) {
            animateScrollToItem(index, scrollOffset = MAX_BOTTOM_SCROLL_OFFSET)
        } else {
            scrollToItem(index, scrollOffset = MAX_BOTTOM_SCROLL_OFFSET)
        }
    } catch (_: Exception) {
        try {
            scrollToItem(index)
        } catch (_: Exception) {
        }
    }
}

/** 等待 LazyColumn 完成布局后再滚到底部，避免返回页面时仍停在顶部。 */
suspend fun LazyListState.awaitScrollToBottom(
    expectedItemCount: Int,
    animated: Boolean = false,
    maxAttempts: Int = 15,
) {
    if (expectedItemCount <= 0) return
    repeat(maxAttempts) { attempt ->
        val total = layoutInfo.totalItemsCount
        if (total > 0) {
            scrollToBottom(animated = animated && attempt == maxAttempts - 1)
            if (isAtBottom()) return
        }
        delay(32)
    }
    scrollToBottom(animated = false)
}

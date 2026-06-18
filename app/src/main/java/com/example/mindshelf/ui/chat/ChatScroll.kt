package com.example.mindshelf.ui.chat

import androidx.compose.foundation.lazy.LazyListState

private const val BOTTOM_SCROLL_THRESHOLD = 80

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
            animateScrollToItem(index, scrollOffset = 10_000)
        } else {
            scrollToItem(index, scrollOffset = 10_000)
        }
    } catch (_: Exception) {
    }
}

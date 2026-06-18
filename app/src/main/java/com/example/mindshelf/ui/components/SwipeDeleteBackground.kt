package com.example.mindshelf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun SwipeDeleteBackground(
    dismissState: SwipeToDismissBoxState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 2.dp.toPx() }
    val swipeDistance = runCatching { dismissState.requireOffset() }
        .getOrDefault(0f)
        .let { (-it).coerceAtLeast(0f) }
    if (swipeDistance < thresholdPx) return

    val revealProgress = (swipeDistance / with(density) { 120.dp.toPx() })
        .coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.error.copy(alpha = revealProgress)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "删除",
            tint = Color.White.copy(alpha = 0.85f + revealProgress * 0.15f),
            modifier = Modifier
                .padding(end = 24.dp)
                .size(24.dp),
        )
    }
}

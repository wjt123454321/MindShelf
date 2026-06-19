package com.example.mindshelf.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MindShelfAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        confirmText = "删除",
        onConfirm = onConfirm,
        dismissText = "取消",
        confirmDestructive = true,
    )
}

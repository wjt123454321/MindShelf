package com.example.mindshelf.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindshelf.data.remote.dto.SyncConflict

@Composable
fun SyncConflictDialog(
    conflict: SyncConflict,
    onResolveLocal: () -> Unit,
    onResolveRemote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (conflict.entity) {
        "note" -> "笔记同步冲突"
        "knowledge_base" -> "知识库同步冲突"
        else -> "同步冲突"
    }
    val localPreview = conflict.local["title"]?.toString()
        ?: conflict.local["name"]?.toString()
        ?: "本地版本"
    val remotePreview = conflict.remote["title"]?.toString()
        ?: conflict.remote["name"]?.toString()
        ?: "云端版本"

    MindShelfAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "本地与云端同时修改了同一内容，请选择保留哪一版：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "本地：$localPreview",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "云端：$remotePreview",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResolveLocal) { Text("保留本地") }
        },
        dismissButton = {
            TextButton(onClick = onResolveRemote) { Text("保留云端") }
        },
    )
}

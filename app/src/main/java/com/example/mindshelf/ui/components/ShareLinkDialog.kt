package com.example.mindshelf.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mindshelf.data.remote.dto.ShareLinkDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShareLinkDialog(
    onRequestLink: suspend () -> ShareLinkDto,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var link by remember { mutableStateOf<ShareLinkDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) { onRequestLink() }
        }.onSuccess { link = it; loading = false }
            .onFailure { e -> error = e.message; loading = false }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享链接") },
        text = {
            Column {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    link != null -> {
                        Text(
                            "持有链接者可只读访问，你可随时撤销。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            link!!.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    link?.url?.let { copyToClipboard(context, it) }
                },
                enabled = link != null,
            ) { Text("复制链接") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("share_link", text))
}

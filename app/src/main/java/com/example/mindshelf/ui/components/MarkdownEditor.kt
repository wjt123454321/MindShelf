package com.example.mindshelf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

enum class MarkdownFormat {
    Bold,
    Italic,
    Heading,
    Bullet,
    Code,
    Link,
}

@Composable
fun MarkdownEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "正文（支持 Markdown）",
    showToolbar: Boolean = true,
    enablePreviewToggle: Boolean = true,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var previewMode by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    fun applyFormat(format: MarkdownFormat) {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(0, text.length)
        val selected = text.substring(start, end)
        val (newText, newSelection) = when (format) {
            MarkdownFormat.Bold -> wrapSelection(text, start, end, selected, "**", "**")
            MarkdownFormat.Italic -> wrapSelection(text, start, end, selected, "*", "*")
            MarkdownFormat.Heading -> {
                val lineStart = text.lastIndexOf('\n', startIndex = (start - 1).coerceAtLeast(0)).let {
                    if (it == -1) 0 else it + 1
                }
                val prefix = "## "
                val updated = text.substring(0, lineStart) + prefix + text.substring(lineStart)
                val cursor = (start + prefix.length).coerceAtMost(updated.length)
                updated to TextRange(cursor)
            }
            MarkdownFormat.Bullet -> {
                val lineStart = text.lastIndexOf('\n', startIndex = (start - 1).coerceAtLeast(0)).let {
                    if (it == -1) 0 else it + 1
                }
                val prefix = "- "
                val updated = text.substring(0, lineStart) + prefix + text.substring(lineStart)
                val cursor = (start + prefix.length).coerceAtMost(updated.length)
                updated to TextRange(cursor)
            }
            MarkdownFormat.Code -> wrapSelection(text, start, end, selected, "`", "`")
            MarkdownFormat.Link -> {
                val label = selected.ifBlank { "链接文字" }
                wrapSelection(text, start, end, selected, "[$label](", ")")
            }
        }
        textFieldValue = TextFieldValue(newText, newSelection)
        onValueChange(newText)
    }

    Column(modifier = modifier) {
        if (showToolbar) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextFormatButton("B", fontWeight = FontWeight.Bold, description = "粗体") {
                    applyFormat(MarkdownFormat.Bold)
                }
                TextFormatButton("I", fontStyle = FontStyle.Italic, description = "斜体") {
                    applyFormat(MarkdownFormat.Italic)
                }
                TextFormatButton("H", fontWeight = FontWeight.SemiBold, description = "标题") {
                    applyFormat(MarkdownFormat.Heading)
                }
                FormatButton(Icons.AutoMirrored.Filled.List, "列表") {
                    applyFormat(MarkdownFormat.Bullet)
                }
                TextFormatButton("`", description = "代码") { applyFormat(MarkdownFormat.Code) }
                TextFormatButton("链", fontWeight = FontWeight.Medium, description = "链接") {
                    applyFormat(MarkdownFormat.Link)
                }
                if (enablePreviewToggle) {
                    IconButton(onClick = { previewMode = !previewMode }) {
                        Icon(
                            if (previewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (previewMode) "编辑" else "预览",
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        if (previewMode && enablePreviewToggle) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    if (value.isBlank()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MarkdownText(text = value)
                    }
                }
            }
        } else {
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onValueChange(it.text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                decorationBox = { inner ->
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun TextFormatButton(
    label: String,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.padding(0.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = fontWeight,
                fontStyle = fontStyle,
            ),
        )
    }
}

@Composable
private fun FormatButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.padding(0.dp)) {
        Icon(icon, contentDescription = description)
    }
}

private fun wrapSelection(
    text: String,
    start: Int,
    end: Int,
    selected: String,
    prefix: String,
    suffix: String,
): Pair<String, TextRange> {
    val content = selected.ifBlank { "文字" }
    val wrapped = prefix + content + suffix
    val updated = text.substring(0, start) + wrapped + text.substring(end)
    val newStart = start + prefix.length
    val newEnd = newStart + content.length
    return updated to TextRange(newStart, newEnd)
}

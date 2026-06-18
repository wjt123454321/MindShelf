package com.example.mindshelf.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mindshelf.ui.components.MarkdownText

@Composable
fun TextBlockContent(
    text: String,
    editable: Boolean = false,
    onTextChange: (String) -> Unit = {},
) {
    if (editable) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("输入文本…") },
            minLines = 2,
        )
    } else {
        Text(
            text = text.ifBlank { " " },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun TodoListBlock(
    items: List<Map<String, Any?>>,
    onToggle: (String, Boolean) -> Unit,
    editable: Boolean = false,
    onTextChange: (String, String) -> Unit = { _, _ -> },
    onAdd: () -> Unit = {},
    onRemove: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            val id = item["id"]?.toString().orEmpty()
            val label = item["text"]?.toString().orEmpty()
            val done = item["done"] as? Boolean ?: false
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = done, onCheckedChange = { onToggle(id, it) })
                if (editable) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { onTextChange(id, it) },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        placeholder = { Text("待办内容") },
                    )
                    IconButton(onClick = { onRemove(id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                } else {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        if (editable) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("添加待办", modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
fun SimpleTableBlock(
    headers: List<String>,
    rows: List<List<String>>,
    editable: Boolean = false,
    onHeaderChange: (Int, String) -> Unit = { _, _ -> },
    onCellChange: (Int, Int, String) -> Unit = { _, _, _ -> },
    onAddRow: () -> Unit = {},
    onRemoveRow: (Int) -> Unit = {},
    onAddColumn: () -> Unit = {},
    onRemoveColumn: (Int) -> Unit = {},
) {
    val columnCount = headers.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val displayHeaders = headers.padEnd(columnCount)
    val displayRows = rows.map { it.padEnd(columnCount) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                displayHeaders.forEachIndexed { colIndex, header ->
                    if (editable) {
                        OutlinedTextField(
                            value = header,
                            onValueChange = { onHeaderChange(colIndex, it) },
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            textStyle = MaterialTheme.typography.labelMedium,
                            singleLine = true,
                            placeholder = { Text("列${colIndex + 1}") },
                        )
                    } else {
                        Text(
                            header,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (editable && columnCount > 1) {
                    IconButton(onClick = { onRemoveColumn(columnCount - 1) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除列")
                    }
                }
            }
            if (displayHeaders.isNotEmpty() || displayRows.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            displayRows.forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    row.forEachIndexed { colIndex, cell ->
                        if (editable) {
                            OutlinedTextField(
                                value = cell,
                                onValueChange = { onCellChange(rowIndex, colIndex, it) },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                        } else {
                            Text(
                                cell,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (editable) {
                        IconButton(onClick = { onRemoveRow(rowIndex) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除行")
                        }
                    }
                }
            }
            if (editable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAddRow) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("添加行", modifier = Modifier.padding(start = 4.dp))
                    }
                    TextButton(onClick = onAddColumn) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("添加列", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NoteEmbedBlock(
    title: String,
    content: String,
    onOpenNote: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onOpenNote != null) Modifier.clickable(onClick = onOpenNote) else Modifier,
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.ifBlank { "无标题" }, style = MaterialTheme.typography.titleMedium)
            if (onOpenNote != null) {
                Text(
                    "点击在笔记中编辑",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            MarkdownText(content)
        }
    }
}

private fun List<String>.padEnd(size: Int): List<String> =
    if (this.size >= size) this.take(size) else this + List(size - this.size) { "" }

@Suppress("UNCHECKED_CAST")
fun parseChecklistItems(binding: Map<String, Any?>?): List<Map<String, Any?>> {
    val items = binding?.get("items") as? List<*> ?: return emptyList()
    return items.mapNotNull { it as? Map<String, Any?> }
}

@Suppress("UNCHECKED_CAST")
fun parseTable(binding: Map<String, Any?>?): Pair<List<String>, List<List<String>>> {
    val headers = (binding?.get("headers") as? List<*>)?.map { it.toString() }.orEmpty()
    val rows = (binding?.get("rows") as? List<*>)?.mapNotNull { row ->
        (row as? List<*>)?.map { it.toString() }
    }.orEmpty()
    return headers to rows
}

fun normalizeTableSize(headers: List<String>, rows: List<List<String>>): Pair<List<String>, List<List<String>>> {
    val columnCount = headers.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val normalizedHeaders = headers.padEnd(columnCount)
    val normalizedRows = rows.map { row -> row.padEnd(columnCount) }
    return normalizedHeaders to normalizedRows
}

package com.example.mindshelf.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PageRenderer(
    schema: Map<String, Any?>,
    bindings: Map<String, Any?>,
    noteLoader: suspend (String) -> Pair<String, String>?,
    onChecklistToggle: (bindingKey: String, itemId: String, done: Boolean) -> Unit,
    loadedNotes: Map<String, Pair<String, String>>,
    editable: Boolean = false,
    onChecklistTextChange: (bindingKey: String, itemId: String, text: String) -> Unit = { _, _, _ -> },
    onChecklistAdd: (bindingKey: String) -> Unit = {},
    onChecklistRemove: (bindingKey: String, itemId: String) -> Unit = { _, _ -> },
    onTextBindingChange: (bindingKey: String, text: String) -> Unit = { _, _ -> },
    onTableHeaderChange: (bindingKey: String, columnIndex: Int, value: String) -> Unit = { _, _, _ -> },
    onTableCellChange: (bindingKey: String, rowIndex: Int, columnIndex: Int, value: String) -> Unit = { _, _, _, _ -> },
    onTableAddRow: (bindingKey: String) -> Unit = {},
    onTableRemoveRow: (bindingKey: String, rowIndex: Int) -> Unit = { _, _ -> },
    onTableAddColumn: (bindingKey: String) -> Unit = {},
    onTableRemoveColumn: (bindingKey: String, columnIndex: Int) -> Unit = { _, _ -> },
    onOpenNote: (noteId: String) -> Unit = {},
) {
    val root = schema["root"] as? Map<*, *>
    if (root != null) {
        RenderNode(
            node = root,
            bindings = bindings,
            noteLoader = noteLoader,
            onChecklistToggle = onChecklistToggle,
            loadedNotes = loadedNotes,
            editable = editable,
            onChecklistTextChange = onChecklistTextChange,
            onChecklistAdd = onChecklistAdd,
            onChecklistRemove = onChecklistRemove,
            onTextBindingChange = onTextBindingChange,
            onTableHeaderChange = onTableHeaderChange,
            onTableCellChange = onTableCellChange,
            onTableAddRow = onTableAddRow,
            onTableRemoveRow = onTableRemoveRow,
            onTableAddColumn = onTableAddColumn,
            onTableRemoveColumn = onTableRemoveColumn,
            onOpenNote = onOpenNote,
        )
    }
}

@Composable
private fun RenderNode(
    node: Map<*, *>,
    bindings: Map<String, Any?>,
    noteLoader: suspend (String) -> Pair<String, String>?,
    onChecklistToggle: (bindingKey: String, itemId: String, done: Boolean) -> Unit,
    loadedNotes: Map<String, Pair<String, String>>,
    editable: Boolean,
    onChecklistTextChange: (bindingKey: String, itemId: String, text: String) -> Unit,
    onChecklistAdd: (bindingKey: String) -> Unit,
    onChecklistRemove: (bindingKey: String, itemId: String) -> Unit,
    onTextBindingChange: (bindingKey: String, text: String) -> Unit,
    onTableHeaderChange: (bindingKey: String, columnIndex: Int, value: String) -> Unit,
    onTableCellChange: (bindingKey: String, rowIndex: Int, columnIndex: Int, value: String) -> Unit,
    onTableAddRow: (bindingKey: String) -> Unit,
    onTableRemoveRow: (bindingKey: String, rowIndex: Int) -> Unit,
    onTableAddColumn: (bindingKey: String) -> Unit,
    onTableRemoveColumn: (bindingKey: String, columnIndex: Int) -> Unit,
    onOpenNote: (noteId: String) -> Unit,
) {
    when (node["type"]?.toString()) {
        "Column" -> {
            val children = node["children"] as? List<*> ?: emptyList<Any>()
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                children.forEach { child ->
                    val childMap = child as? Map<*, *> ?: return@forEach
                    RenderNode(
                        node = childMap,
                        bindings = bindings,
                        noteLoader = noteLoader,
                        onChecklistToggle = onChecklistToggle,
                        loadedNotes = loadedNotes,
                        editable = editable,
                        onChecklistTextChange = onChecklistTextChange,
                        onChecklistAdd = onChecklistAdd,
                        onChecklistRemove = onChecklistRemove,
                        onTextBindingChange = onTextBindingChange,
                        onTableHeaderChange = onTableHeaderChange,
                        onTableCellChange = onTableCellChange,
                        onTableAddRow = onTableAddRow,
                        onTableRemoveRow = onTableRemoveRow,
                        onTableAddColumn = onTableAddColumn,
                        onTableRemoveColumn = onTableRemoveColumn,
                        onOpenNote = onOpenNote,
                    )
                }
            }
        }
        "TextBlock" -> {
            val props = node["props"] as? Map<*, *>
            val bindingKey = props?.get("binding")?.toString()
            val bound = bindingKey?.let { bindingMap(bindings, it) }
            val text = bound?.get("text")?.toString()
                ?: bound?.get("value")?.toString()
                ?: props?.get("text")?.toString()
                ?: ""
            val canEdit = editable && !bindingKey.isNullOrBlank()
            TextBlockContent(
                text = text,
                editable = canEdit,
                onTextChange = { newText ->
                    bindingKey?.let { onTextBindingChange(it, newText) }
                },
            )
        }
        "TodoList", "Checklist" -> {
            val bindingKey = (node["props"] as? Map<*, *>)?.get("binding")?.toString().orEmpty()
            val items = parseChecklistItems(bindingMap(bindings, bindingKey))
            TodoListBlock(
                items = items,
                onToggle = { itemId, done -> onChecklistToggle(bindingKey, itemId, done) },
                editable = editable,
                onTextChange = { itemId, text -> onChecklistTextChange(bindingKey, itemId, text) },
                onAdd = { onChecklistAdd(bindingKey) },
                onRemove = { itemId -> onChecklistRemove(bindingKey, itemId) },
            )
        }
        "SimpleTable" -> {
            val bindingKey = (node["props"] as? Map<*, *>)?.get("binding")?.toString().orEmpty()
            val (headers, rows) = parseTable(bindingMap(bindings, bindingKey))
            SimpleTableBlock(
                headers = headers,
                rows = rows,
                editable = editable,
                onHeaderChange = { col, value -> onTableHeaderChange(bindingKey, col, value) },
                onCellChange = { row, col, value -> onTableCellChange(bindingKey, row, col, value) },
                onAddRow = { onTableAddRow(bindingKey) },
                onRemoveRow = { row -> onTableRemoveRow(bindingKey, row) },
                onAddColumn = { onTableAddColumn(bindingKey) },
                onRemoveColumn = { col -> onTableRemoveColumn(bindingKey, col) },
            )
        }
        "NoteEmbed" -> {
            val bindingKey = (node["props"] as? Map<*, *>)?.get("binding")?.toString().orEmpty()
            val noteId = bindingMap(bindings, bindingKey)?.get("note_id")?.toString().orEmpty()
            val note = loadedNotes[noteId]
            if (note != null) {
                NoteEmbedBlock(
                    title = note.first,
                    content = note.second,
                    onOpenNote = if (noteId.isNotBlank()) ({ onOpenNote(noteId) }) else null,
                )
            } else {
                TextBlockContent("笔记加载中…")
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun bindingMap(bindings: Map<String, Any?>, key: String): Map<String, Any?>? =
    bindings[key] as? Map<String, Any?>

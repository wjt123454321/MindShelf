package com.example.mindshelf.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.CustomPageDto
import com.example.mindshelf.data.repository.NoteRepository
import com.example.mindshelf.data.repository.PageRepository
import com.example.mindshelf.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class CustomPageUiState(
    val loading: Boolean = true,
    val page: CustomPageDto? = null,
    val loadedNotes: Map<String, Pair<String, String>> = emptyMap(),
    val editMode: Boolean = false,
    val actionMessage: String? = null,
    val showShareDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
)

@HiltViewModel
class CustomPageViewModel @Inject constructor(
    private val pageRepository: PageRepository,
    private val noteRepository: NoteRepository,
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomPageUiState())
    val uiState: StateFlow<CustomPageUiState> = _uiState.asStateFlow()

    private var pageId: String? = null
    private val saveJobs = ConcurrentHashMap<String, Job>()

    fun load(pageId: String) {
        if (this.pageId == pageId && _uiState.value.page != null) return
        this.pageId = pageId
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, editMode = false) }
            val raw = pageRepository.get(pageId)
            val page = raw?.let { pageRepository.migrateContentBindings(it) }
            _uiState.update { it.copy(loading = false, page = page) }
            page?.let { loadEmbeddedNotes(it) }
        }
    }

    private suspend fun loadEmbeddedNotes(page: CustomPageDto) {
        val notes = mutableMapOf<String, Pair<String, String>>()
        page.dataBindings.values.forEach { binding ->
            @Suppress("UNCHECKED_CAST")
            val map = binding as? Map<String, Any?> ?: return@forEach
            if (map["kind"]?.toString() == "note") {
                val noteId = map["note_id"]?.toString() ?: return@forEach
                noteRepository.get(noteId)?.let { note ->
                    notes[noteId] = note.title to note.content
                }
            }
        }
        _uiState.update { it.copy(loadedNotes = notes) }
    }

    fun toggleEditMode() {
        if (_uiState.value.editMode) {
            saveJobs.values.forEach { it.cancel() }
            viewModelScope.launch {
                val page = _uiState.value.page ?: return@launch
                val updated = pageRepository.update(id = page.id, dataBindings = page.dataBindings)
                _uiState.update { it.copy(editMode = false, page = updated) }
            }
        } else {
            _uiState.update { it.copy(editMode = true) }
        }
    }

    private fun debouncedPersist(key: String, delayMs: Long = 500, persist: suspend () -> Unit) {
        saveJobs[key]?.cancel()
        saveJobs[key] = viewModelScope.launch {
            delay(delayMs)
            persist()
            saveJobs.remove(key)
        }
    }

    private fun updateBindingsOptimistic(transform: (Map<String, Any?>) -> Map<String, Any?>) {
        val page = _uiState.value.page ?: return
        val bindings = transform(page.dataBindings)
        _uiState.update { it.copy(page = page.copy(dataBindings = bindings)) }
    }

    private fun persistBindings(key: String, bindings: Map<String, Any?>) {
        val page = _uiState.value.page ?: return
        debouncedPersist(key) {
            val updated = pageRepository.update(id = page.id, dataBindings = bindings)
            _uiState.update { state ->
                if (state.page?.id == page.id) state.copy(page = updated) else state
            }
        }
    }

    private fun updateChecklistBinding(
        bindingKey: String,
        immediate: Boolean = false,
        transform: (List<Map<String, Any?>>) -> List<Map<String, Any?>>,
    ) {
        val page = _uiState.value.page ?: return
        @Suppress("UNCHECKED_CAST")
        val bindings = page.dataBindings.toMutableMap()
        val binding = (bindings[bindingKey] as? Map<String, Any?>)?.toMutableMap() ?: return
        @Suppress("UNCHECKED_CAST")
        val items = (binding["items"] as? List<Map<String, Any?>>)?.toMutableList() ?: mutableListOf()
        binding["items"] = transform(items)
        binding["kind"] = "checklist"
        bindings[bindingKey] = binding
        updateBindingsOptimistic { bindings }
        if (immediate) {
            viewModelScope.launch {
                val updated = pageRepository.update(id = page.id, dataBindings = bindings)
                _uiState.update { it.copy(page = updated) }
            }
        } else {
            persistBindings("checklist:$bindingKey", bindings)
        }
    }

    fun toggleChecklistItem(bindingKey: String, itemId: String, done: Boolean) {
        updateChecklistBinding(bindingKey, immediate = true) { items ->
            items.map { item ->
                if (item["id"]?.toString() == itemId) {
                    item.toMutableMap().apply { put("done", done) }
                } else {
                    item
                }
            }
        }
    }

    fun updateChecklistItemText(bindingKey: String, itemId: String, text: String) {
        updateChecklistBinding(bindingKey) { items ->
            items.map { item ->
                if (item["id"]?.toString() == itemId) {
                    item.toMutableMap().apply { put("text", text) }
                } else {
                    item
                }
            }
        }
    }

    fun addChecklistItem(bindingKey: String) {
        updateChecklistBinding(bindingKey, immediate = true) { items ->
            items + mapOf(
                "id" to UUID.randomUUID().toString(),
                "text" to "",
                "done" to false,
            )
        }
    }

    fun removeChecklistItem(bindingKey: String, itemId: String) {
        updateChecklistBinding(bindingKey, immediate = true) { items ->
            items.filterNot { it["id"]?.toString() == itemId }
        }
    }

    fun updateTextBinding(bindingKey: String, text: String) {
        val page = _uiState.value.page ?: return
        val bindings = page.dataBindings.toMutableMap()
        bindings[bindingKey] = mapOf("kind" to "text", "text" to text)
        updateBindingsOptimistic { bindings }
        persistBindings("text:$bindingKey", bindings)
    }

    private fun updateTableBindingDirect(
        bindingKey: String,
        headers: List<String>,
        rows: List<List<String>>,
        immediate: Boolean = false,
    ) {
        val page = _uiState.value.page ?: return
        val bindings = page.dataBindings.toMutableMap()
        val (normalizedHeaders, normalizedRows) = normalizeTableSize(headers, rows)
        bindings[bindingKey] = mapOf(
            "kind" to "table",
            "headers" to normalizedHeaders,
            "rows" to normalizedRows,
        )
        updateBindingsOptimistic { bindings }
        if (immediate) {
            viewModelScope.launch {
                val updated = pageRepository.update(id = page.id, dataBindings = bindings)
                _uiState.update { it.copy(page = updated) }
            }
        } else {
            persistBindings("table:$bindingKey", bindings)
        }
    }

    fun updateTableHeader(bindingKey: String, columnIndex: Int, value: String) {
        val page = _uiState.value.page ?: return
        val (headers, rows) = parseTable(page.dataBindings[bindingKey] as? Map<String, Any?>)
        val newHeaders = headers.toMutableList().apply {
            while (size <= columnIndex) add("")
            this[columnIndex] = value
        }
        updateTableBindingDirect(bindingKey, newHeaders, rows)
    }

    fun updateTableCell(bindingKey: String, rowIndex: Int, columnIndex: Int, value: String) {
        val page = _uiState.value.page ?: return
        val (headers, rows) = parseTable(page.dataBindings[bindingKey] as? Map<String, Any?>)
        val newRows = rows.toMutableList()
        while (newRows.size <= rowIndex) newRows.add(emptyList())
        val row = newRows[rowIndex].toMutableList()
        while (row.size <= columnIndex) row.add("")
        row[columnIndex] = value
        newRows[rowIndex] = row
        updateTableBindingDirect(bindingKey, headers, newRows)
    }

    fun addTableRow(bindingKey: String) {
        val page = _uiState.value.page ?: return
        val (headers, rows) = parseTable(page.dataBindings[bindingKey] as? Map<String, Any?>)
        val columnCount = headers.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
        val newRow = List(columnCount) { "" }
        updateTableBindingDirect(bindingKey, headers, rows + listOf(newRow), immediate = true)
    }

    fun removeTableRow(bindingKey: String, rowIndex: Int) {
        val page = _uiState.value.page ?: return
        val (headers, rows) = parseTable(page.dataBindings[bindingKey] as? Map<String, Any?>)
        updateTableBindingDirect(
            bindingKey,
            headers,
            rows.filterIndexed { index, _ -> index != rowIndex },
            immediate = true,
        )
    }

    fun addTableColumn(bindingKey: String) {
        val page = _uiState.value.page ?: return
        val (headers, rows) = parseTable(page.dataBindings[bindingKey] as? Map<String, Any?>)
        updateTableBindingDirect(
            bindingKey,
            headers + "",
            rows.map { it + "" },
            immediate = true,
        )
    }

    fun removeTableColumn(bindingKey: String, columnIndex: Int) {
        val page = _uiState.value.page ?: return
        val (headers, rows) = parseTable(page.dataBindings[bindingKey] as? Map<String, Any?>)
        if (headers.size <= 1 && rows.all { it.size <= 1 }) return
        updateTableBindingDirect(
            bindingKey,
            headers.filterIndexed { index, _ -> index != columnIndex },
            rows.map { row -> row.filterIndexed { index, _ -> index != columnIndex } },
            immediate = true,
        )
    }

    fun showRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = true) }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false) }
    }

    fun renamePage(name: String) {
        val page = _uiState.value.page ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed == page.name) {
            dismissRenameDialog()
            return
        }
        viewModelScope.launch {
            val updated = pageRepository.update(id = page.id, name = trimmed)
            _uiState.update {
                it.copy(page = updated, showRenameDialog = false, actionMessage = "已重命名")
            }
        }
    }

    fun togglePinned() {
        val page = _uiState.value.page ?: return
        viewModelScope.launch {
            val updated = pageRepository.setPinned(page.id, !page.pinned)
            _uiState.update {
                it.copy(
                    page = updated,
                    actionMessage = if (updated.pinned) "已固定到底栏" else "已取消固定",
                )
            }
        }
    }

    fun deletePage() {
        val id = _uiState.value.page?.id ?: return
        viewModelScope.launch {
            pageRepository.delete(id)
            _uiState.update { it.copy(showDeleteDialog = false, actionMessage = "已移入回收站") }
        }
    }

    fun requestShare() {
        _uiState.update { it.copy(showShareDialog = true) }
    }

    fun dismissShare() {
        _uiState.update { it.copy(showShareDialog = false) }
    }

    suspend fun createShareLink() =
        shareRepository.createLink("page", _uiState.value.page?.id ?: error("无页面"))

    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}

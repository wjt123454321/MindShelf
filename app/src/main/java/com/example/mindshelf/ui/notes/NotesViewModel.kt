package com.example.mindshelf.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.KnowledgeBaseDto
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.data.repository.KnowledgeRepository
import com.example.mindshelf.data.repository.NoteRepository
import com.example.mindshelf.data.repository.VersionRepository
import com.example.mindshelf.data.repository.ShareRepository
import com.example.mindshelf.data.sync.NetworkContentSyncObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val versionRepository: VersionRepository,
    private val shareRepository: ShareRepository,
    private val networkContentSyncObserver: NetworkContentSyncObserver,
) : ViewModel() {

    val notes: StateFlow<List<NoteDto>> = noteRepository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knowledgeBases: StateFlow<List<KnowledgeBaseDto>> = knowledgeRepository.observeKnowledgeBases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingNote = MutableStateFlow<NoteDto?>(null)
    val editingNote: StateFlow<NoteDto?> = _editingNote.asStateFlow()

    fun loadNote(id: String) {
        viewModelScope.launch {
            _editingNote.value = noteRepository.get(id)
        }
    }

    fun clearEditing() {
        _editingNote.value = null
    }

    fun save(
        noteId: String?,
        title: String,
        content: String,
        syncVersion: Int,
        kbIds: List<String>,
        onDone: (() -> Unit)? = null,
    ) {
        viewModelScope.launch {
            if (noteId == null) {
                noteRepository.create(title, content, kbIds)
            } else {
                noteRepository.update(noteId, title, content, syncVersion, kbIds)
            }
            networkContentSyncObserver.scheduleSync()
            if (noteId != null) {
                _editingNote.value = noteRepository.get(noteId)
            }
            onDone?.invoke()
        }
    }

    fun create(title: String, content: String, kbIds: List<String>, onDone: () -> Unit) {
        save(null, title, content, 1, kbIds, onDone)
    }

    fun update(
        id: String,
        title: String,
        content: String,
        syncVersion: Int,
        kbIds: List<String>,
        onDone: () -> Unit,
    ) {
        save(id, title, content, syncVersion, kbIds, onDone)
    }

    fun delete(id: String) {
        viewModelScope.launch {
            noteRepository.delete(id)
            networkContentSyncObserver.scheduleSync()
        }
    }

    fun deleteAll(ids: List<String>) {
        viewModelScope.launch {
            noteRepository.deleteAll(ids)
        }
    }

    suspend fun loadNoteVersions(noteId: String): Result<List<com.example.mindshelf.data.remote.dto.NoteVersionDto>> =
        runCatching { versionRepository.listVersions(noteId) }

    fun restoreVersion(noteId: String, versionId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            versionRepository.restoreVersion(noteId, versionId)
            _editingNote.value = noteRepository.get(noteId)
            onDone()
        }
    }

    suspend fun createShareLink(noteId: String) = shareRepository.createLink("note", noteId)
}

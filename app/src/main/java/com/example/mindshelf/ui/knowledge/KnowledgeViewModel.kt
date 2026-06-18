package com.example.mindshelf.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.KnowledgeBaseDto
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.data.repository.KnowledgeRepository
import com.example.mindshelf.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {

    val items: StateFlow<List<KnowledgeBaseDto>> = repository.observeKnowledgeBases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _kbNotes = MutableStateFlow<List<NoteDto>>(emptyList())
    val kbNotes: StateFlow<List<NoteDto>> = _kbNotes.asStateFlow()

    fun create(name: String) {
        viewModelScope.launch {
            repository.create(name)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun deleteAll(ids: List<String>) {
        viewModelScope.launch {
            repository.deleteAll(ids)
        }
    }

    fun update(id: String, name: String, description: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.update(id, name, description)
            onDone()
        }
    }

    fun loadKbNotes(kbId: String) {
        viewModelScope.launch {
            _kbNotes.value = noteRepository.getNotesForKb(kbId)
        }
    }
}

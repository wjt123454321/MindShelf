package com.example.mindshelf.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.CustomPageDto
import com.example.mindshelf.data.repository.PageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PagesListViewModel @Inject constructor(
    private val pageRepository: PageRepository,
) : ViewModel() {

    val pages: StateFlow<List<CustomPageDto>> =
        pageRepository.observePages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPage(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val page = pageRepository.create(name = "新页面")
            onCreated(page.id)
        }
    }

    fun deletePage(id: String) {
        viewModelScope.launch { pageRepository.delete(id) }
    }

    fun togglePinned(page: CustomPageDto) {
        viewModelScope.launch {
            pageRepository.setPinned(page.id, !page.pinned)
        }
    }
}

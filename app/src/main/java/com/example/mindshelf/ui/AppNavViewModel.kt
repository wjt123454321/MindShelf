package com.example.mindshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.CustomPageDto
import com.example.mindshelf.data.repository.PageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(
    pageRepository: PageRepository,
) : ViewModel() {
    val pinnedPage: StateFlow<CustomPageDto?> =
        pageRepository.observePinnedPage()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

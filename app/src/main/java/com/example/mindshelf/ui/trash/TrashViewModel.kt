package com.example.mindshelf.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindshelf.data.remote.dto.TrashItemDto
import com.example.mindshelf.data.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val loading: Boolean = true,
    val items: List<TrashItemDto> = emptyList(),
    val error: String? = null,
    val actionMessage: String? = null,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        load()
    }

    fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _refreshing.value = true
            else _uiState.update { it.copy(loading = true, error = null) }
            runCatching { trashRepository.listTrash() }
                .onSuccess { items ->
                    _uiState.update { it.copy(loading = false, items = items, error = null) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, error = e.message ?: "加载失败")
                    }
                }
            _refreshing.value = false
        }
    }

    fun refresh() = load(isRefresh = true)

    fun restore(item: TrashItemDto) {
        viewModelScope.launch {
            runCatching {
                trashRepository.restore(item.entityType, item.entity["id"].toString())
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        items = state.items.filterNot {
                            it.entityType == item.entityType &&
                                it.entity["id"] == item.entity["id"]
                        },
                        actionMessage = "已恢复",
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(actionMessage = e.message ?: "恢复失败") }
            }
        }
    }

    fun purge(item: TrashItemDto) {
        viewModelScope.launch {
            runCatching {
                trashRepository.purge(item.entityType, item.entity["id"].toString())
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        items = state.items.filterNot {
                            it.entityType == item.entityType &&
                                it.entity["id"] == item.entity["id"]
                        },
                        actionMessage = "已永久删除",
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(actionMessage = e.message ?: "删除失败") }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}

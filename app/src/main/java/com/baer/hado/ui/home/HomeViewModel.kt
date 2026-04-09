package com.baer.hado.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baer.hado.data.model.HaState
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.repository.AuthRepository
import com.baer.hado.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val todoLists: List<HaState> = emptyList(),
    val selectedListId: String? = null,
    val items: List<TodoItem> = emptyList(),
    val isLoadingLists: Boolean = false,
    val isLoadingItems: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTodoLists()
    }

    fun loadTodoLists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLists = true, error = null)
            todoRepository.getTodoLists().fold(
                onSuccess = { lists ->
                    val selectedId = _uiState.value.selectedListId ?: lists.firstOrNull()?.entityId
                    _uiState.value = _uiState.value.copy(
                        todoLists = lists,
                        selectedListId = selectedId,
                        isLoadingLists = false
                    )
                    selectedId?.let { loadItems(it) }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingLists = false,
                        error = e.message ?: "Failed to load lists"
                    )
                }
            )
        }
    }

    fun selectList(entityId: String) {
        _uiState.value = _uiState.value.copy(selectedListId = entityId)
        loadItems(entityId)
    }

    fun loadItems(entityId: String? = _uiState.value.selectedListId) {
        val id = entityId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingItems = true, error = null)
            todoRepository.getTodoItems(id).fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        items = items,
                        isLoadingItems = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingItems = false,
                        error = e.message ?: "Failed to load items"
                    )
                }
            )
        }
    }

    fun addItem(summary: String) {
        val entityId = _uiState.value.selectedListId ?: return
        viewModelScope.launch {
            todoRepository.addItem(entityId, summary).fold(
                onSuccess = { loadItems(entityId) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun toggleItem(item: TodoItem) {
        val entityId = _uiState.value.selectedListId ?: return
        viewModelScope.launch {
            todoRepository.updateItemStatus(entityId, item.uid, !item.isCompleted).fold(
                onSuccess = { loadItems(entityId) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun removeItem(item: TodoItem) {
        val entityId = _uiState.value.selectedListId ?: return
        viewModelScope.launch {
            todoRepository.removeItem(entityId, item.uid).fold(
                onSuccess = { loadItems(entityId) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(isLoggedOut = true)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

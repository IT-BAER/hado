package com.baer.hado.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.HaState
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.repository.AuthRepository
import com.baer.hado.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val todoLists: List<HaState> = emptyList(),
    val selectedListId: String? = null,
    val itemsByList: Map<String, List<TodoItem>> = emptyMap(),
    val loadingListIds: Set<String> = emptySet(),
    val lastLoadedAt: Map<String, Long> = emptyMap(),
    val isLoadingLists: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false,
    val isLocalMode: Boolean = false
) {
    fun itemsFor(entityId: String?): List<TodoItem> {
        return entityId?.let { itemsByList[it] }.orEmpty()
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val SELECTED_LIST_CACHE_TTL_MS = 5_000L
        private const val BACKGROUND_LIST_CACHE_TTL_MS = 15_000L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadItemsJob: Job? = null
    private var loadingEntityId: String? = null
    private var activeLoadToken = 0L

    init {
        _uiState.value = _uiState.value.copy(isLocalMode = tokenManager.isDemoMode)
        loadTodoLists()
    }

    fun loadTodoLists() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLists = true, error = null)
            todoRepository.getTodoLists().fold(
                onSuccess = { lists ->
                    val selectedId = _uiState.value.selectedListId
                        ?.takeIf { currentId -> lists.any { it.entityId == currentId } }
                        ?: lists.firstOrNull()?.entityId

                    _uiState.value = _uiState.value.copy(
                        todoLists = lists,
                        selectedListId = selectedId,
                        isLoadingLists = false
                    )

                    selectedId?.let { loadItems(it) }
                },
                onFailure = { e ->
                    if (e.message?.contains("401") == true) {
                        Log.w("HAdo", "401 on loadTodoLists — token refresh failed, redirecting to login")
                        handleUnauthorized(isLoadingLists = false)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingLists = false,
                            error = e.message ?: "Failed to load lists"
                        )
                    }
                }
            )
        }
    }

    fun selectList(entityId: String) {
        if (_uiState.value.selectedListId == entityId) return
        _uiState.value = _uiState.value.copy(selectedListId = entityId, error = null)
        loadItems(entityId)
    }

    fun loadItems(entityId: String? = _uiState.value.selectedListId, force: Boolean = false) {
        val id = entityId ?: return
        if (!shouldLoadItems(id, force)) return

        val previousLoadingId = loadingEntityId
        if (previousLoadingId != null) {
            loadItemsJob?.cancel()
            if (previousLoadingId != id) {
                setLoading(previousLoadingId, false)
            }
        }

        val loadToken = ++activeLoadToken
        loadingEntityId = id
        setLoading(id, true)
        _uiState.update { it.copy(error = null) }

        loadItemsJob = viewModelScope.launch {
            try {
                todoRepository.getTodoItems(id).fold(
                    onSuccess = { items ->
                        if (loadToken != activeLoadToken) return@fold
                        _uiState.update { state ->
                            state.copy(
                                itemsByList = state.itemsByList + (id to items),
                                lastLoadedAt = state.lastLoadedAt + (id to System.currentTimeMillis())
                            )
                        }
                    },
                    onFailure = { e ->
                        if (loadToken != activeLoadToken) return@fold
                        if (e.message?.contains("401") == true) {
                            Log.w("HAdo", "401 on loadItems — token refresh failed, redirecting to login")
                            handleUnauthorized()
                        } else {
                            _uiState.update { state ->
                                state.copy(error = e.message ?: "Failed to load items")
                            }
                        }
                    }
                )
            } finally {
                if (loadToken == activeLoadToken) {
                    loadingEntityId = null
                    setLoading(id, false)
                }
            }
        }
    }

    fun updateCachedItems(entityId: String, items: List<TodoItem>) {
        _uiState.update { state ->
            if (state.itemsByList[entityId] == items) {
                state
            } else {
                state.copy(itemsByList = state.itemsByList + (entityId to items))
            }
        }
    }

    private fun shouldLoadItems(entityId: String, force: Boolean): Boolean {
        val state = _uiState.value
        if (force) return true
        if (entityId in state.loadingListIds) return false

        val lastLoadedAt = state.lastLoadedAt[entityId] ?: return true
        val ttlMs = if (entityId == state.selectedListId) {
            SELECTED_LIST_CACHE_TTL_MS
        } else {
            BACKGROUND_LIST_CACHE_TTL_MS
        }
        return System.currentTimeMillis() - lastLoadedAt >= ttlMs
    }

    private fun setLoading(entityId: String, isLoading: Boolean) {
        _uiState.update { state ->
            state.copy(
                loadingListIds = if (isLoading) {
                    state.loadingListIds + entityId
                } else {
                    state.loadingListIds - entityId
                }
            )
        }
    }

    fun addItem(summary: String) {
        val entityId = _uiState.value.selectedListId ?: return
        viewModelScope.launch {
            todoRepository.addItem(entityId, summary).fold(
                onSuccess = { loadItems(entityId, force = true) },
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
                onSuccess = { loadItems(entityId, force = true) },
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
                onSuccess = { loadItems(entityId, force = true) },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            )
        }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            val newList = todoRepository.createList(name)
            loadTodoLists()
            selectList(newList.entityId)
        }
    }

    fun deleteList(entityId: String) {
        viewModelScope.launch {
            todoRepository.deleteList(entityId)
            _uiState.value = _uiState.value.copy(selectedListId = null)
            loadTodoLists()
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(isLoggedOut = true)
    }

    private fun handleUnauthorized(isLoadingLists: Boolean = _uiState.value.isLoadingLists) {
        authRepository.logout()
        _uiState.value = _uiState.value.copy(
            isLoadingLists = isLoadingLists,
            todoLists = emptyList(),
            selectedListId = null,
            itemsByList = emptyMap(),
            loadingListIds = emptySet(),
            lastLoadedAt = emptyMap(),
            error = null,
            isLoggedOut = true,
            isLocalMode = false
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

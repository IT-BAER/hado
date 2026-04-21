@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.baer.hado.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.baer.hado.R
import com.baer.hado.data.model.TodoItem
import com.baer.hado.ui.theme.AppSpacing
import com.baer.hado.widget.ListIconManager
import com.baer.hado.widget.TodoListEditor
import com.baer.hado.widget.WidgetHttpClient
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    onLoggedOut: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val httpClient = remember(context) { WidgetHttpClient(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewListDialog by remember { mutableStateOf(false) }
    var showDeleteListDialog by remember { mutableStateOf(false) }
    val selectedList = uiState.todoLists.find { it.entityId == uiState.selectedListId }
    val selectedListName = selectedList?.attributes?.friendlyName ?: uiState.selectedListId
    val editorItemsByList = remember { mutableStateMapOf<String, List<TodoItem>>() }
    var isAddInputFocused by remember { mutableStateOf(false) }
    val selectedItems = selectedList?.let { editorItemsByList[it.entityId] ?: uiState.items } ?: emptyList()

    LaunchedEffect(uiState.selectedListId, uiState.items) {
        uiState.selectedListId?.let { selectedId ->
            editorItemsByList[selectedId] = uiState.items
        }
    }

    LaunchedEffect(selectedList?.entityId) {
        isAddInputFocused = false
    }

    val activeCount = selectedItems.count { !it.isCompleted }
    val completedCount = selectedItems.count { it.isCompleted }
    val overdueCount = selectedItems.count { it.isOverdue && !it.isCompleted }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (uiState.isLocalMode) {
                        IconButton(onClick = { showNewListDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_list))
                        }
                    }
                    IconButton(onClick = { viewModel.loadItems() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap)
        ) {
            AnimatedVisibility(
                visible = !isAddInputFocused,
                enter = fadeIn(animationSpec = tween(180)) +
                    expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top) +
                    slideInVertically(animationSpec = tween(220), initialOffsetY = { -it / 3 }),
                exit = fadeOut(animationSpec = tween(120)) +
                    shrinkVertically(animationSpec = tween(180), shrinkTowards = Alignment.Top) +
                    slideOutVertically(animationSpec = tween(180), targetOffsetY = { -it / 2 })
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap)) {
                    HomeOverviewCard(
                        listEntityId = selectedList?.entityId,
                        listName = selectedListName,
                        isLocalMode = uiState.isLocalMode,
                        activeCount = activeCount,
                        completedCount = completedCount,
                        overdueCount = overdueCount,
                        compact = false,
                        onDeleteCurrentList = if (uiState.isLocalMode && uiState.selectedListId != null) {
                            { showDeleteListDialog = true }
                        } else {
                            null
                        }
                    )

                    if (uiState.todoLists.size > 1) {
                        ListSelector(
                            lists = uiState.todoLists.map {
                                it.entityId to (it.attributes.friendlyName ?: it.entityId)
                            },
                            selectedId = uiState.selectedListId,
                            onSelect = viewModel::selectList,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }

            when {
                uiState.isLoadingLists -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.todoLists.isEmpty() && !uiState.isLoadingLists -> {
                    EmptyStateCard(
                        text = if (uiState.isLocalMode) stringResource(R.string.home_no_lists_local)
                        else stringResource(R.string.home_no_lists_ha),
                        modifier = Modifier.padding(horizontal = AppSpacing.screenHorizontal)
                    )
                }
                selectedList == null -> {
                    EmptyStateCard(
                        text = stringResource(R.string.home_choose_list),
                        modifier = Modifier.padding(horizontal = AppSpacing.screenHorizontal)
                    )
                }
                else -> {
                    val selectedIndex = uiState.todoLists.indexOfFirst { it.entityId == selectedList.entityId }
                        .let { index -> if (index >= 0) index else 0 }
                    val pagerState = rememberPagerState(
                        initialPage = selectedIndex,
                        pageCount = { uiState.todoLists.size }
                    )

                    LaunchedEffect(selectedIndex, uiState.todoLists.size) {
                        if (uiState.todoLists.isNotEmpty() && pagerState.currentPage != selectedIndex) {
                            pagerState.animateScrollToPage(selectedIndex)
                        }
                    }

                    LaunchedEffect(pagerState, uiState.todoLists, uiState.selectedListId) {
                        snapshotFlow { pagerState.currentPage }
                            .collectLatest { page ->
                                val pageList = uiState.todoLists.getOrNull(page) ?: return@collectLatest
                                if (pageList.entityId != uiState.selectedListId) {
                                    viewModel.selectList(pageList.entityId)
                                }
                            }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        beyondBoundsPageCount = 1
                    ) { page ->
                        val pageList = uiState.todoLists[page]
                        TodoListEditor(
                            context = context,
                            entityId = pageList.entityId,
                            listName = pageList.attributes.friendlyName ?: pageList.entityId,
                            supportedFeatures = pageList.attributes.supportedFeatures ?: 0,
                            httpClient = httpClient,
                            isLocalMode = uiState.isLocalMode,
                            showOpenAppAction = false,
                            onBack = {},
                            onChanged = { viewModel.loadItems(pageList.entityId) },
                            modifier = Modifier.fillMaxSize(),
                            showTopBar = false,
                            showListHeader = false,
                            contentBottomPadding = 24.dp,
                            initialItems = editorItemsByList[pageList.entityId].orEmpty(),
                            onItemsChanged = { items ->
                                editorItemsByList[pageList.entityId] = items
                            },
                            onAddInputFocusChanged = { isFocused ->
                                if (pageList.entityId == uiState.selectedListId) {
                                    isAddInputFocused = isFocused
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showNewListDialog) {
        NewListDialog(
            onDismiss = { showNewListDialog = false },
            onCreate = { name ->
                viewModel.createList(name)
                showNewListDialog = false
            }
        )
    }

    if (showDeleteListDialog && uiState.selectedListId != null) {
        val listName = uiState.todoLists.find { it.entityId == uiState.selectedListId }
            ?.attributes?.friendlyName ?: uiState.selectedListId
        AlertDialog(
            onDismissRequest = { showDeleteListDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_list_title)) },
            text = { Text(stringResource(R.string.dialog_delete_list_text, listName!!)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteList(uiState.selectedListId!!)
                    showDeleteListDialog = false
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteListDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun ListSelector(
    lists: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = AppSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compactGap)
    ) {
        items(lists, key = { it.first }) { (id, name) ->
            FilterChip(
                selected = id == selectedId,
                onClick = { onSelect(id) },
                label = {
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun HomeOverviewCard(
    listEntityId: String?,
    listName: String?,
    isLocalMode: Boolean,
    activeCount: Int,
    completedCount: Int,
    overdueCount: Int,
    compact: Boolean,
    onDeleteCurrentList: (() -> Unit)?
) {
    val context = LocalContext.current
    val resolvedIcon = remember(listEntityId) {
        listEntityId?.let { entityId -> ListIconManager.resolveIcon(context, entityId) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.screenHorizontal)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeListIcon(
                        resolvedIcon = resolvedIcon,
                        compact = compact
                    )
                    Text(
                        text = listName ?: stringResource(R.string.home_choose_list),
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onDeleteCurrentList != null) {
                    IconButton(onClick = onDeleteCurrentList) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete_list),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!compact) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isLocalMode) stringResource(R.string.home_local_mode_support)
                        else stringResource(R.string.home_remote_mode_support),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.compactGap)
            ) {
                StatPill(
                    text = stringResource(R.string.home_open_count, activeCount),
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    text = stringResource(R.string.completed_count, completedCount),
                    modifier = Modifier.weight(1f)
                )
                if (overdueCount > 0) {
                    StatPill(
                        text = stringResource(R.string.home_overdue_count, overdueCount),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeListIcon(
    resolvedIcon: ListIconManager.ResolvedIcon?,
    compact: Boolean
) {
    val iconSize = if (compact) 24.dp else 30.dp

    when {
        resolvedIcon == null -> {
            Text(
                text = "📋",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
            )
        }
        resolvedIcon.type == ListIconManager.IconType.EMOJI -> {
            Text(
                text = resolvedIcon.value,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
            )
        }
        else -> {
            val bitmap = remember(resolvedIcon.value) { ListIconManager.loadBitmap(resolvedIcon.value) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(iconSize)
                        .height(iconSize)
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = "📋",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyStateCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun NewListDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_list_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.label_list_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onCreate(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

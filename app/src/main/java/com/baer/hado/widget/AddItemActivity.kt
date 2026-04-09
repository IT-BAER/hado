package com.baer.hado.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.model.TodoItemStatus
import com.baer.hado.data.model.TodoListFeature
import com.baer.hado.ui.theme.HadoTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddItemActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra("entity_id")
        val listName = intent.getStringExtra("list_name") ?: "To-Do"
        val supportedFeatures = intent.getIntExtra("supported_features", 0)
        val detailItemUid = intent.getStringExtra("detail_item_uid")

        if (entityId.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            HadoTheme {
                ListEditorScreen(
                    context = this,
                    entityId = entityId,
                    listName = listName,
                    supportedFeatures = supportedFeatures,
                    httpClient = WidgetHttpClient(this),
                    onBack = { finish() },
                    onChanged = { TodoWidgetWorker.enqueueOneTime(this) },
                    autoDetailItemUid = detailItemUid
                )
            }
        }
    }
}

private object ItemsCache {
    private const val PREFS_NAME = "hado_items_cache"

    fun load(context: Context, entityId: String): List<TodoItem>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(entityId, null) ?: return null
        return try {
            val type = object : TypeToken<List<TodoItem>>() {}.type
            Gson().fromJson(json, type)
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, entityId: String, items: List<TodoItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(entityId, Gson().toJson(items)).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ListEditorScreen(
    context: Context,
    entityId: String,
    listName: String,
    supportedFeatures: Int,
    httpClient: WidgetHttpClient,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    autoDetailItemUid: String? = null
) {
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }
    val supportsDescription = TodoListFeature.hasFeature(supportedFeatures, TodoListFeature.SET_DESCRIPTION_ON_ITEM)
    val supportsDueDate = TodoListFeature.hasFeature(supportedFeatures, TodoListFeature.SET_DUE_DATE_ON_ITEM)
    val supportsDueDatetime = TodoListFeature.hasFeature(supportedFeatures, TodoListFeature.SET_DUE_DATETIME_ON_ITEM)
    // Load cached items instantly, then refresh from HA
    val cachedItems = remember {
        val cached = ItemsCache.load(context, entityId)
        Log.d("HAdo", "Cache for $entityId: ${cached?.size ?: "null"} items")
        cached
    }
    var items by remember { mutableStateOf(cachedItems ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedItems == null) }
    var newItemText by remember { mutableStateOf("") }
    val addFocusRequester = remember { FocusRequester() }
    var refocusTrigger by remember { mutableIntStateOf(0) }
    var initialLoadComplete by remember { mutableStateOf(false) }
    var completedExpanded by remember { mutableStateOf(true) }
    var pendingDeletes by remember { mutableStateOf(emptySet<String>()) }
    var draggedItemUid by remember { mutableStateOf<String?>(null) }
    var detailItem by remember { mutableStateOf<TodoItem?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 48.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    // Always-current items ref for gesture callbacks (avoids stale closures)
    val currentItemsState by rememberUpdatedState(items)

    // Re-focus input field after adding an item (deferred to next frame)
    LaunchedEffect(refocusTrigger) {
        if (refocusTrigger > 0) {
            // Scroll to add-item row: title(1) + uncompleted items
            val addRowIndex = 1 + items.count { !it.isCompleted }
            listState.animateScrollToItem(addRowIndex)
            addFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Mark initial load complete after first items render
    LaunchedEffect(isLoading) {
        if (!isLoading) initialLoadComplete = true
    }

    // Fetch items on launch (background refresh)
    LaunchedEffect(entityId) {
        withContext(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf("entity_id" to entityId))
                val response = httpClient.post(
                    "api/services/todo/get_items?return_response", payload
                )
                response?.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val freshItems = parseItemsFromResponse(gson, body, entityId)
                            items = freshItems
                            ItemsCache.save(context, entityId, freshItems)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HAdo", "Failed to fetch items", e)
            }
            isLoading = false
        }
    }

    // Auto-open detail dialog when launched from widget item tap
    var autoDetailHandled by remember { mutableStateOf(false) }
    LaunchedEffect(items, autoDetailHandled) {
        if (autoDetailItemUid != null && !autoDetailHandled && items.isNotEmpty()) {
            val target = items.find { it.uid == autoDetailItemUid }
            if (target != null) {
                detailItem = target
                autoDetailHandled = true
            }
        }
    }

    fun addItem(text: String) {
        Log.d("HAdo", "addItem called with text='$text'")
        if (text.isBlank()) return
        val trimmed = text.trim()
        newItemText = ""

        // Trigger re-focus on next frame for rapid multi-add
        refocusTrigger++

        // Optimistic UI update — add item immediately to local list
        val tempItem = TodoItem(
            uid = "temp_${System.currentTimeMillis()}",
            summary = trimmed,
            status = TodoItemStatus.NEEDS_ACTION
        )
        items = items + tempItem

        scope.launch(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf("entity_id" to entityId, "item" to trimmed))
                val response = httpClient.post("api/services/todo/add_item", payload)
                response?.use { resp ->
                    if (resp.isSuccessful) {
                        Log.d("HAdo", "addItem succeeded: ${resp.code}")
                    } else {
                        Log.e("HAdo", "addItem failed: ${resp.code} ${resp.body?.string()}")
                    }
                }
                onChanged()
            } catch (e: Exception) {
                Log.e("HAdo", "addItem failed", e)
            }
        }
    }

    fun toggleItem(item: TodoItem) {
        val newStatus = if (item.isCompleted) "needs_action" else "completed"
        // Optimistic update
        items = items.map {
            if (it.uid == item.uid) it.copy(
                status = if (item.isCompleted) TodoItemStatus.NEEDS_ACTION
                else TodoItemStatus.COMPLETED
            ) else it
        }
        scope.launch(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf(
                    "entity_id" to entityId,
                    "item" to item.uid,
                    "status" to newStatus
                ))
                val response = httpClient.post("api/services/todo/update_item", payload)
                response?.use { resp ->
                    if (resp.isSuccessful) {
                        Log.d("HAdo", "toggleItem succeeded: ${resp.code}")
                    } else {
                        Log.e("HAdo", "toggleItem failed: ${resp.code} ${resp.body?.string()}")
                    }
                }
                onChanged()
            } catch (e: Exception) {
                Log.e("HAdo", "toggleItem failed", e)
            }
        }
    }

    fun deleteItem(item: TodoItem) {
        pendingDeletes = pendingDeletes + item.uid
        scope.launch {
            delay(300) // match exit animation duration
            items = items.filter { it.uid != item.uid }
            pendingDeletes = pendingDeletes - item.uid
        }
        scope.launch(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf(
                    "entity_id" to entityId,
                    "item" to item.uid
                ))
                val response = httpClient.post("api/services/todo/remove_item", payload)
                response?.use { resp ->
                    if (resp.isSuccessful) {
                        Log.d("HAdo", "deleteItem succeeded: ${resp.code}")
                    } else {
                        Log.e("HAdo", "deleteItem failed: ${resp.code} ${resp.body?.string()}")
                    }
                }
                onChanged()
            } catch (e: Exception) {
                Log.e("HAdo", "deleteItem failed", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(context.packageName)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Open app",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // List title with icon (Keep: 24sp, paddingTop 12dp)
                item {
                    val resolvedIcon = remember {
                        ListIconManager.resolveIcon(context, entityId)
                    }
                    Row(
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = 16.dp
                        ),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        if (resolvedIcon != null) {
                            when (resolvedIcon.type) {
                                ListIconManager.IconType.EMOJI -> {
                                    Text(
                                        text = resolvedIcon.value,
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                ListIconManager.IconType.IMAGE -> {
                                    val bitmap = remember { ListIconManager.loadBitmap(resolvedIcon.value) }
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .padding(end = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = listName,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Uncompleted items with drag handles
                val uncompleted = items.filter { !it.isCompleted }
                itemsIndexed(uncompleted, key = { _, item -> item.uid }) { _, item ->
                    val shouldAnimate = initialLoadComplete
                    var visible by remember { mutableStateOf(!shouldAnimate) }
                    LaunchedEffect(Unit) { visible = true }
                    val isDragging = draggedItemUid == item.uid

                    AnimatedVisibility(
                        visible = visible && item.uid !in pendingDeletes,
                        enter = fadeIn(tween(300)) + expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = tween(300)
                        ),
                        exit = fadeOut(tween(300)) + shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = tween(300)
                        ),
                        modifier = if (isDragging) Modifier else Modifier.animateItemPlacement()
                    ) {
                        TodoItemRow(
                            item = item,
                            onToggle = { toggleItem(item) },
                            onDelete = { deleteItem(item) },
                            onLongPress = { detailItem = item },
                            showDragHandle = true,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragOffsetY else 0f,
                            onDragStart = {
                                draggedItemUid = item.uid
                                dragOffsetY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { deltaY ->
                                dragOffsetY += deltaY
                                val uid = draggedItemUid ?: return@TodoItemRow
                                // Dynamically find current position (avoids stale closure)
                                val liveUncompleted = currentItemsState.filter { !it.isCompleted }
                                val currentIndex = liveUncompleted.indexOfFirst { it.uid == uid }
                                if (currentIndex < 0) return@TodoItemRow

                                val halfItem = itemHeightPx / 2
                                val targetIndex = when {
                                    dragOffsetY > halfItem && currentIndex < liveUncompleted.lastIndex -> currentIndex + 1
                                    dragOffsetY < -halfItem && currentIndex > 0 -> currentIndex - 1
                                    else -> return@TodoItemRow
                                }

                                val mutableItems = currentItemsState.toMutableList()
                                val draggedItem = liveUncompleted[currentIndex]
                                val targetItem = liveUncompleted[targetIndex]
                                val fromGlobal = mutableItems.indexOf(draggedItem)
                                val toGlobal = mutableItems.indexOf(targetItem)
                                if (fromGlobal >= 0 && toGlobal >= 0) {
                                    mutableItems.removeAt(fromGlobal)
                                    mutableItems.add(toGlobal, draggedItem)
                                    items = mutableItems
                                    // Adjust offset so item stays under finger
                                    dragOffsetY -= (targetIndex - currentIndex) * itemHeightPx
                                }
                            },
                            onDragEnd = {
                                val finishedUid = draggedItemUid
                                draggedItemUid = null
                                dragOffsetY = 0f
                                // Sync to HA via WebSocket
                                if (finishedUid != null) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val finalUncompleted = currentItemsState.filter { !it.isCompleted }
                                            val finalIndex = finalUncompleted.indexOfFirst { it.uid == finishedUid }
                                            if (finalIndex < 0) return@launch
                                            val prevUid = if (finalIndex > 0) finalUncompleted[finalIndex - 1].uid else null
                                            val ok = httpClient.moveTodoItem(entityId, finishedUid, prevUid)
                                            if (ok) {
                                                Log.d("HAdo", "moveTodoItem succeeded: $finishedUid after $prevUid")
                                                onChanged()
                                            } else {
                                                Log.w("HAdo", "moveTodoItem not supported or failed for this entity")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("HAdo", "moveTodoItem error", e)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Add item row (Keep: below uncompleted items)
                item(key = "add-item-row") {
                    AddItemRow(
                        text = newItemText,
                        onTextChange = { newItemText = it },
                        onAdd = { addItem(newItemText) },
                        focusRequester = addFocusRequester
                    )
                }

                // Completed items section with collapsible header
                val completed = items.filter { it.isCompleted }
                if (completed.isNotEmpty()) {
                    item {
                        CompletedSectionHeader(
                            count = completed.size,
                            expanded = completedExpanded,
                            onToggle = { completedExpanded = !completedExpanded }
                        )
                    }
                    if (completedExpanded) {
                        items(completed, key = { it.uid }) { item ->
                            val shouldAnimate = initialLoadComplete
                            var visible by remember { mutableStateOf(!shouldAnimate) }
                            LaunchedEffect(Unit) { visible = true }

                            AnimatedVisibility(
                                visible = visible && item.uid !in pendingDeletes,
                                enter = fadeIn(tween(300)) + expandVertically(
                                    expandFrom = Alignment.Top,
                                    animationSpec = tween(300)
                                ),
                                exit = fadeOut(tween(300)) + shrinkVertically(
                                    shrinkTowards = Alignment.Top,
                                    animationSpec = tween(300)
                                ),
                                modifier = Modifier.animateItemPlacement()
                            ) {
                                TodoItemRow(
                                    item = item,
                                    onToggle = { toggleItem(item) },
                                    onDelete = { deleteItem(item) },
                                    onLongPress = { detailItem = item },
                                    showDragHandle = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Item detail dialog (long-press)
        detailItem?.let { item ->
        ItemDetailDialog(
            context = context,
            item = item,
            supportsDescription = supportsDescription,
            supportsDueDate = supportsDueDate,
            supportsDueDatetime = supportsDueDatetime,
            onDismiss = { detailItem = null },
            onSave = { updatedItem ->
                // Optimistic UI update
                items = items.map { if (it.uid == item.uid) updatedItem else it }
                detailItem = null
                scope.launch(Dispatchers.IO) {
                    try {
                        val body = mutableMapOf(
                            "entity_id" to entityId,
                            "item" to item.uid
                        )
                        if (updatedItem.summary != item.summary) {
                            body["rename"] = updatedItem.summary
                        }
                        if (updatedItem.description != item.description) {
                            body["description"] = updatedItem.description ?: ""
                        }
                        val newDue = updatedItem.due
                        if (newDue != item.due) {
                            if (newDue == null) {
                                body["due_date"] = ""
                            } else if (newDue.contains("T") || (newDue.contains(" ") && newDue.length > 10)) {
                                body["due_datetime"] = newDue.replace("T", " ")
                            } else {
                                body["due_date"] = newDue
                            }
                        }
                        val payload = gson.toJson(body)
                        val response = httpClient.post("api/services/todo/update_item", payload)
                        response?.use { resp ->
                            if (resp.isSuccessful) {
                                Log.d("HAdo", "updateItemDetails succeeded: ${resp.code}")
                            } else {
                                Log.e("HAdo", "updateItemDetails failed: ${resp.code} ${resp.body?.string()}")
                            }
                        }
                        onChanged()
                    } catch (e: Exception) {
                        Log.e("HAdo", "updateItemDetails failed", e)
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoItemRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit = {},
    showDragHandle: Boolean,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val overdueColor = if (item.isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
    else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(overdueColor)
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = { onLongPress() }
            )
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = 6.dp, end = 6.dp)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragOffsetY
                    shadowElevation = 8f
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle (Keep: 48dp touch target, only on uncompleted)
        if (showDragHandle) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp)
                    .pointerInput(item.uid) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, offset ->
                                change.consume()
                                onDrag(offset.y)
                            }
                        )
                    }
            )
        }

        // Checkbox
        Checkbox(
            checked = item.isCompleted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(48.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.surface
            )
        )

        // Item text with optional due date + description preview
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            )
            // Due date indicator
            if (item.due != null) {
                val dueText = formatDueDisplay(item)
                Text(
                    text = dueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            // Description preview (first line only)
            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description.lines().first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Delete button — X icon
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CompletedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 6.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp
            else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp).padding(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count completed",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddItemRow(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    "List item",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    Log.d("HAdo", "Keyboard send, text='$text'")
                    onAdd()
                }
            )
        )
        if (text.isNotBlank()) {
            IconButton(onClick = {
                Log.d("HAdo", "Send button clicked, text='$text'")
                onAdd()
            }) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Add item",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun parseItemsFromResponse(
    gson: Gson, json: String, entityId: String
): List<TodoItem> {
    return try {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val root: Map<String, Any> = gson.fromJson(json, type)
        val serviceResponse = root["service_response"] as? Map<*, *> ?: root
        val entityData = serviceResponse[entityId] as? Map<*, *> ?: return emptyList()
        val itemsList = entityData["items"] ?: return emptyList()
        val itemsJson = gson.toJson(itemsList)
        Log.d("HAdo", "parseItemsFromResponse itemsJson=$itemsJson")
        val itemsType = object : TypeToken<List<TodoItem>>() {}.type
        val parsed: List<TodoItem> = gson.fromJson(itemsJson, itemsType)
        parsed.forEach { item ->
            Log.d("HAdo", "  item uid=${item.uid} summary=${item.summary} due=${item.due} desc=${item.description?.take(30)}")
        }
        parsed
    } catch (_: Exception) {
        emptyList()
    }
}

private fun formatDueDisplay(item: TodoItem): String {
    val dt = item.dueDateTime
    if (dt != null) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
        return (if (item.isOverdue) "⚠ " else "📅 ") + dt.format(formatter)
    }
    val d = item.dueDate
    if (d != null) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
        return (if (item.isOverdue) "⚠ " else "📅 ") + d.format(formatter)
    }
    return ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailDialog(
    context: Context,
    item: TodoItem,
    supportsDescription: Boolean,
    supportsDueDate: Boolean,
    supportsDueDatetime: Boolean,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit
) {
    var summary by remember { mutableStateOf(item.summary) }
    var description by remember { mutableStateOf(item.description ?: "") }
    var dueString by remember { mutableStateOf(item.due) }
    var isEditingDescription by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Markwon instance for markdown rendering
    val markwon = remember {
        io.noties.markwon.Markwon.builder(context)
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(context))
            .build()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Description section
                if (supportsDescription) {
                    Text(
                        "Description",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (isEditingDescription) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp),
                            placeholder = { Text("Add description (markdown supported)") },
                            maxLines = 10
                        )
                        TextButton(onClick = { isEditingDescription = false }) {
                            Text("Preview")
                        }
                    } else {
                        if (description.isNotBlank()) {
                            // Render markdown via AndroidView
                            val textColor = MaterialTheme.colorScheme.onSurface
                            val textColorArgb = android.graphics.Color.argb(
                                (textColor.alpha * 255).toInt(),
                                (textColor.red * 255).toInt(),
                                (textColor.green * 255).toInt(),
                                (textColor.blue * 255).toInt()
                            )
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.TextView(ctx).apply {
                                        setTextColor(textColorArgb)
                                        textSize = 14f
                                    }
                                },
                                update = { tv ->
                                    tv.setTextColor(textColorArgb)
                                    markwon.setMarkdown(tv, description)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isEditingDescription = true }
                                    .padding(8.dp)
                            )
                        } else {
                            TextButton(
                                onClick = { isEditingDescription = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add description...")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Due date section
                if (supportsDueDate || supportsDueDatetime) {
                    Text(
                        "Due date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dateDisplay = if (dueString != null) {
                            val parsed = item.copy(due = dueString)
                            formatDueDisplay(parsed).removePrefix("⚠ ").removePrefix("📅 ")
                        } else "No due date"

                        TextButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(dateDisplay)
                        }
                        if (dueString != null) {
                            IconButton(onClick = { dueString = null }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear due date",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    item.copy(
                        summary = summary.trim(),
                        description = description.ifBlank { null },
                        due = dueString
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Material3 DatePicker dialog
    if (showDatePicker) {
        val initialMillis = item.dueDate?.let {
            it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        dueString = date.toString() // YYYY-MM-DD
                        showDatePicker = false
                        if (supportsDueDatetime) {
                            showTimePicker = true
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material3 TimePicker dialog
    if (showTimePicker && supportsDueDatetime) {
        val existingTime = item.dueDateTime
        val timePickerState = rememberTimePickerState(
            initialHour = existingTime?.hour ?: 12,
            initialMinute = existingTime?.minute ?: 0
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    // Combine selected date with time
                    val datePrefix = dueString?.substringBefore("T") ?: dueString ?: return@TextButton
                    dueString = "${datePrefix}T${String.format("%02d:%02d:00", timePickerState.hour, timePickerState.minute)}"
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Keep date-only (no time)
                    dueString = dueString?.substringBefore("T")
                    showTimePicker = false
                }) { Text("Skip time") }
            }
        )
    }
}

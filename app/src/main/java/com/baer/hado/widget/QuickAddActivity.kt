package com.baer.hado.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baer.hado.R
import com.baer.hado.data.local.AddItemPosition
import com.baer.hado.data.local.AppPreferencesManager
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.model.TodoItemStatus
import com.baer.hado.data.model.TodoListFeature
import com.baer.hado.ui.theme.HadoTheme
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Small add-item card floating above the keyboard over the home screen (widget + / mic tap).
 * Stays open after each add so several items can be entered in a row.
 */
class QuickAddActivity : ComponentActivity() {

    // Outlives the activity so writes complete after finish(); the mutex keeps add order.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val entityId = intent.getStringExtra("entity_id")
        val listName = intent.getStringExtra("list_name") ?: ""
        val supportedFeatures = intent.getIntExtra("supported_features", 0)
        val startVoice = intent.getBooleanExtra("start_voice", false)
        val targetAppWidgetId = intent.getIntExtra("app_widget_id", AppWidgetManager.INVALID_APPWIDGET_ID)
            .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

        val tokenManager = TokenManager(this)
        val isLocalMode = tokenManager.isDemoMode
        val accountId = targetAppWidgetId?.let { WidgetSettingsManager.accountIdFor(this, it, tokenManager) }

        // A widget whose server was removed must not fall back to another server.
        if (entityId.isNullOrBlank() || (targetAppWidgetId != null && !isLocalMode && accountId == null)) {
            finish()
            return
        }

        val supportsDueDate = TodoListFeature.hasFeature(supportedFeatures, TodoListFeature.SET_DUE_DATE_ON_ITEM)

        setContent {
            HadoTheme {
                QuickAddCard(
                    listName = listName,
                    supportsDueDate = supportsDueDate,
                    startVoice = startVoice,
                    onAdd = { text, due ->
                        persist(entityId, text, due?.toString(), isLocalMode, accountId, targetAppWidgetId)
                    },
                    onOpenList = {
                        startActivity(Intent(this, AddItemActivity::class.java).apply {
                            putExtra("entity_id", entityId)
                            putExtra("list_name", listName)
                            putExtra("supported_features", supportedFeatures)
                            putExtra("app_widget_id", targetAppWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun persist(
        entityId: String,
        text: String,
        due: String?,
        isLocalMode: Boolean,
        accountId: String?,
        appWidgetId: Int?
    ) {
        val appContext = applicationContext
        val position = AppPreferencesManager.loadAddItemPosition(appContext)
        saveScope.launch {
            saveMutex.withLock {
                if (isLocalMode) {
                    val item = LocalTodoStore(appContext).addItem(entityId, text, due = due, position = position)
                    WidgetStateMutator.addItem(appContext, accountId, entityId, item, position, appWidgetId)
                } else {
                    addToHomeAssistant(entityId, text, due, accountId, appWidgetId, position)
                }
                if (appWidgetId != null) {
                    TodoWidgetWorker.enqueueOneTime(appContext, appWidgetId)
                } else {
                    TodoWidgetWorker.enqueueOneTime(appContext)
                }
            }
        }
    }

    private suspend fun addToHomeAssistant(
        entityId: String,
        text: String,
        due: String?,
        accountId: String?,
        appWidgetId: Int?,
        position: AddItemPosition
    ) {
        val appContext = applicationContext
        val gson = Gson()
        val httpClient = WidgetHttpClient(appContext, accountId)

        // Optimistic: show the item on the widget right away, revert on failure.
        val tempItem = TodoItem(
            uid = "temp_${System.nanoTime()}",
            summary = text,
            status = TodoItemStatus.NEEDS_ACTION,
            due = due
        )
        WidgetStateMutator.addItem(appContext, accountId, entityId, tempItem, position, appWidgetId)

        val added = try {
            val knownUids = fetchListItems(httpClient, gson, entityId)?.map { it.uid }?.toSet() ?: emptySet()
            val body = buildMap {
                put("entity_id", entityId)
                put("item", text)
                if (due != null) put("due_date", due)
            }
            val ok = httpClient.post("api/services/todo/add_item", gson.toJson(body))?.use { resp ->
                if (!resp.isSuccessful) Log.e("HAdo", "Quick add failed: ${resp.code}")
                resp.isSuccessful
            } == true
            if (ok) resolveAddedItem(httpClient, gson, entityId, knownUids, text) ?: AddedItem(tempItem, true) else null
        } catch (e: Exception) {
            Log.e("HAdo", "Quick add failed", e)
            null
        }

        if (added == null) {
            WidgetStateMutator.removeItem(appContext, accountId, entityId, tempItem.uid, appWidgetId)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, R.string.error_add_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (added.item.uid != tempItem.uid) {
            if (position == AddItemPosition.TOP && !added.alreadyFirstActive) {
                httpClient.moveTodoItem(entityId, added.item.uid, null)
            }
            WidgetStateMutator.replaceItem(appContext, accountId, entityId, tempItem.uid, added.item, appWidgetId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddCard(
    listName: String,
    supportsDueDate: Boolean,
    startVoice: Boolean,
    onAdd: (String, LocalDate?) -> Unit,
    onOpenList: () -> Unit,
    onDismiss: () -> Unit
) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val noRipple = remember { MutableInteractionSource() }

    fun setText(text: String) {
        field = TextFieldValue(text, selection = TextRange(text.length))
    }

    val voice = rememberVoiceInput(
        prompt = listName,
        onPartial = { setText(it) },
        onResult = {
            // Prefilled only: the user confirms with send.
            setText(it)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    )

    fun submit() {
        val text = field.text.trim()
        if (text.isEmpty()) return
        onAdd(text, due)
        setText("")
        due = null
    }

    LaunchedEffect(Unit) {
        // Wait for the window to take input focus before showing the keyboard.
        delay(150)
        if (startVoice && voice.available) {
            voice.start()
        } else {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp)
                .clickable(interactionSource = noRipple, indication = null, onClick = {}),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenList) {
                        Text(stringResource(R.string.quick_add_open_list))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                stringResource(
                                    if (voice.listening) R.string.quick_add_listening
                                    else R.string.placeholder_list_item
                                )
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        trailingIcon = if (voice.available) {
                            {
                                IconButton(onClick = { if (voice.listening) voice.stop() else voice.start() }) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = stringResource(R.string.cd_voice_input),
                                        tint = if (voice.listening) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else null
                    )
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(onClick = { submit() }, enabled = field.text.isNotBlank()) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.cd_add_item)
                        )
                    }
                }
                if (supportsDueDate) {
                    val today = LocalDate.now()
                    val tomorrow = today.plusDays(1)
                    val customDue = due?.takeIf { it != today && it != tomorrow }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        FilterChip(
                            selected = due == today,
                            onClick = { due = if (due == today) null else today },
                            label = { Text(stringResource(R.string.due_today)) }
                        )
                        FilterChip(
                            selected = due == tomorrow,
                            onClick = { due = if (due == tomorrow) null else tomorrow },
                            label = { Text(stringResource(R.string.due_tomorrow)) }
                        )
                        FilterChip(
                            selected = customDue != null,
                            onClick = { showDatePicker = true },
                            label = {
                                Text(
                                    customDue?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                                        ?: stringResource(R.string.quick_add_pick_date)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (due ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

package com.baer.hado.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baer.hado.R
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.SimpleState
import com.baer.hado.ui.theme.HadoTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result = CANCELED (user backs out without saving)
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            HadoTheme {
                WidgetSettingsScreen(
                    appWidgetId = appWidgetId,
                    onSave = { settings ->
                        WidgetSettingsManager.save(this, appWidgetId, settings)
                        TodoWidgetWorker.enqueuePeriodic(this, appWidgetId)
                        TodoWidgetWorker.enqueueOneTime(this, appWidgetId)

                        val resultIntent = Intent().putExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
                        )
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsScreen(
    appWidgetId: Int,
    onSave: (WidgetSettings) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Load existing settings
    val existingSettings = remember { WidgetSettingsManager.load(context, appWidgetId) }

    var selectedListIds by remember { mutableStateOf(existingSettings.selectedListIds) }
    var showCompleted by remember { mutableStateOf(existingSettings.showCompleted) }
    var refreshInterval by remember { mutableStateOf(existingSettings.refreshInterval) }
    var fontSize by remember { mutableStateOf(existingSettings.fontSize) }
    var itemHeight by remember { mutableStateOf(existingSettings.itemHeight) }
    var backgroundOpacity by remember { mutableFloatStateOf(existingSettings.backgroundOpacity) }
    var compactMode by remember { mutableStateOf(existingSettings.compactMode) }
    var checkboxOnly by remember { mutableStateOf(existingSettings.checkboxOnly) }
    var showTitle by remember { mutableStateOf(existingSettings.showTitle) }

    fun currentSettings() = WidgetSettings(
        selectedListIds = selectedListIds,
        showCompleted = showCompleted,
        refreshInterval = refreshInterval,
        fontSize = fontSize,
        itemHeight = itemHeight,
        backgroundOpacity = backgroundOpacity,
        compactMode = compactMode,
        checkboxOnly = checkboxOnly,
        showTitle = showTitle
    )

    // Back gesture/button saves settings instead of discarding
    BackHandler { onSave(currentSettings()) }

    // Available lists — fetched from HA (entityId, friendlyName, haIcon)
    var availableLists by remember { mutableStateOf<List<Triple<String, String, String?>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            availableLists = fetchAvailableLists(context)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_widget_settings)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(currentSettings()) }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Lists selection ---
            SettingsSection(title = stringResource(R.string.section_lists_to_show)) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (availableLists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_lists),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    // "All lists" toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedListIds = emptySet() }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedListIds.isEmpty(),
                            onClick = { selectedListIds = emptySet() }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_all_lists),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selectedListIds.isEmpty()) FontWeight.Medium else FontWeight.Normal
                        )
                    }

                    // Individual list checkboxes
                    availableLists.forEach { (entityId, name, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedListIds = if (selectedListIds.contains(entityId)) {
                                        selectedListIds - entityId
                                    } else {
                                        selectedListIds + entityId
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedListIds.isEmpty() || selectedListIds.contains(entityId),
                                onCheckedChange = { checked ->
                                    selectedListIds = if (checked) {
                                        selectedListIds + entityId
                                    } else {
                                        (selectedListIds.ifEmpty { availableLists.map { it.first }.toSet() }) - entityId
                                    }
                                },
                                enabled = selectedListIds.isNotEmpty()
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // --- List icons ---
            if (availableLists.isNotEmpty()) {
                ListIconsSection(
                    availableLists = availableLists,
                    selectedListIds = selectedListIds
                )
            }

            // --- Show completed ---
            SettingsSection(title = stringResource(R.string.section_completed_items)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showCompleted = !showCompleted }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_show_completed),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showCompleted,
                        onCheckedChange = { showCompleted = it }
                    )
                }
            }

            // --- Checkbox only toggle ---
            SettingsSection(title = stringResource(R.string.section_interaction)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { checkboxOnly = !checkboxOnly }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_checkbox_only),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_checkbox_only_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = checkboxOnly,
                        onCheckedChange = { checkboxOnly = it }
                    )
                }
            }

            // --- Show title ---
            SettingsSection(title = stringResource(R.string.section_appearance)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showTitle = !showTitle }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_show_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_show_title_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showTitle,
                        onCheckedChange = { showTitle = it }
                    )
                }
            }

            // --- Font size ---
            SettingsSection(title = stringResource(R.string.section_font_size)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WidgetSettings.FontSize.entries.forEachIndexed { index, size ->
                        SegmentedButton(
                            selected = fontSize == size,
                            onClick = { fontSize = size },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = WidgetSettings.FontSize.entries.size
                            )
                        ) {
                            Text(stringResource(size.labelResId))
                        }
                    }
                }
            }

            // --- Item height ---
            SettingsSection(title = stringResource(R.string.section_item_height)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WidgetSettings.ItemHeight.entries.forEachIndexed { index, height ->
                        SegmentedButton(
                            selected = itemHeight == height,
                            onClick = { itemHeight = height },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = WidgetSettings.ItemHeight.entries.size
                            )
                        ) {
                            Text(stringResource(height.labelResId))
                        }
                    }
                }
            }

            // --- Background opacity ---
            SettingsSection(title = stringResource(R.string.section_bg_opacity)) {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = { backgroundOpacity = it },
                        valueRange = 0.0f..1.0f,
                        steps = 9
                    )
                    Text(
                        text = "${(backgroundOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Compact mode ---
            SettingsSection(title = stringResource(R.string.section_layout)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { compactMode = !compactMode }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_compact_mode),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_compact_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = compactMode,
                        onCheckedChange = { compactMode = it }
                    )
                }
            }

            // --- Refresh interval (global) ---
            SettingsSection(title = stringResource(R.string.section_refresh_interval)) {
                Column {
                    WidgetSettings.RefreshInterval.entries.forEachIndexed { index, interval ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    refreshInterval = interval
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = refreshInterval == interval,
                                onClick = {
                                    refreshInterval = interval
                                }
                            )
                            Text(
                                text = stringResource(interval.labelResId),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        if (index < WidgetSettings.RefreshInterval.entries.size - 1) {
                            HorizontalDivider()
                        }
                    }
                    Text(
                        text = stringResource(R.string.refresh_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ListIconsSection(
    availableLists: List<Triple<String, String, String?>>,
    selectedListIds: Set<String>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var iconDialogEntityId by remember { mutableStateOf<String?>(null) }
    var iconDialogListName by remember { mutableStateOf("") }
    var iconDialogHaIcon by remember { mutableStateOf<String?>(null) }
    // Force recomposition after icon changes
    var iconVersion by remember { mutableIntStateOf(0) }

    // Image picker launcher
    var pendingImageEntityId by remember { mutableStateOf<String?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val entityId = pendingImageEntityId
        if (uri != null && entityId != null) {
            ListIconManager.setImage(context, entityId, uri)
            iconVersion++
            iconDialogEntityId = null
        }
        pendingImageEntityId = null
    }

    // Show only selected lists (or all if none selected)
    val listsToShow = if (selectedListIds.isEmpty()) {
        availableLists
    } else {
        availableLists.filter { it.first in selectedListIds }
    }

    SettingsSection(title = "List Icons") {
        listsToShow.forEach { (entityId, name, haIcon) ->
            val resolved = remember(entityId, iconVersion) {
                ListIconManager.resolveIcon(context, entityId, haIcon)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        iconDialogEntityId = entityId
                        iconDialogListName = name
                        iconDialogHaIcon = haIcon
                    }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current icon preview
                when (resolved?.type) {
                    ListIconManager.IconType.EMOJI -> {
                        Text(
                            text = resolved.value,
                            fontSize = 24.sp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    ListIconManager.IconType.IMAGE -> {
                        val bitmap = remember(resolved.value, iconVersion) {
                            ListIconManager.loadBitmap(resolved.value)
                        }
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            NoIconPlaceholder()
                        }
                    }
                    null -> NoIconPlaceholder()
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.label_tap_to_change),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Icon picker dialog
    if (iconDialogEntityId != null) {
        IconPickerDialog(
            entityId = iconDialogEntityId!!,
            listName = iconDialogListName,
            haIcon = iconDialogHaIcon,
            onEmojiPicked = { emoji ->
                ListIconManager.setEmoji(context, iconDialogEntityId!!, emoji)
                iconVersion++
                iconDialogEntityId = null
            },
            onImagePick = {
                pendingImageEntityId = iconDialogEntityId
                imagePickerLauncher.launch("image/*")
            },
            onClear = {
                ListIconManager.clearIcon(context, iconDialogEntityId!!)
                iconVersion++
                iconDialogEntityId = null
            },
            onDisable = {
                ListIconManager.disableIcon(context, iconDialogEntityId!!)
                iconVersion++
                iconDialogEntityId = null
            },
            onDismiss = { iconDialogEntityId = null }
        )
    }
}

@Composable
private fun NoIconPlaceholder() {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "—",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun IconPickerDialog(
    entityId: String,
    listName: String,
    haIcon: String?,
    onEmojiPicked: (String) -> Unit,
    onImagePick: () -> Unit,
    onClear: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    var emojiInput by remember { mutableStateOf("") }
    val isValidEmoji = emojiInput.isNotBlank() && !emojiInput.any { it in 'a'..'z' || it in 'A'..'Z' }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_icon_title, listName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (haIcon != null) {
                    Text(
                        text = stringResource(R.string.label_ha_icon, haIcon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Emoji input
                OutlinedTextField(
                    value = emojiInput,
                    onValueChange = { input ->
                        // Filter out ASCII letters on input
                        emojiInput = input.filter { it !in 'a'..'z' && it !in 'A'..'Z' }
                    },
                    label = { Text(stringResource(R.string.label_emoji)) },
                    placeholder = { Text(stringResource(R.string.emoji_placeholder)) },
                    singleLine = true,
                    isError = emojiInput.isNotBlank() && !isValidEmoji,
                    supportingText = if (emojiInput.isNotBlank() && !isValidEmoji) {
                        { Text(stringResource(R.string.emoji_error)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isValidEmoji) onEmojiPicked(emojiInput.trim())
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isValidEmoji) {
                            IconButton(onClick = { onEmojiPicked(emojiInput.trim()) }) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_apply))
                            }
                        }
                    }
                )

                // Image picker button
                OutlinedButton(
                    onClick = onImagePick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📷")
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_choose_image))
                }

                // Clear button — reset to default (📋)
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_reset_default))
                }

                // Disable icon button
                TextButton(
                    onClick = onDisable,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_no_icon))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content
            )
        }
    }
}

private suspend fun fetchAvailableLists(context: android.content.Context): List<Triple<String, String, String?>> {
    return withContext(Dispatchers.IO) {
        try {
            val tokenManager = TokenManager(context)
            if (tokenManager.isDemoMode) {
                val localStore = LocalTodoStore(context)
                return@withContext localStore.getLists().map { list ->
                    Triple(
                        list.entityId,
                        list.attributes.friendlyName ?: list.entityId,
                        null as String?
                    )
                }
            }

            val httpClient = WidgetHttpClient(context)
            if (!httpClient.isLoggedIn) return@withContext emptyList()

            val response = httpClient.get("api/states") ?: return@withContext emptyList()
            val json = response.use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }

            val type = object : TypeToken<List<SimpleState>>() {}.type
            val states: List<SimpleState> = Gson().fromJson(json, type)

            states
                .filter { it.entity_id.startsWith("todo.") }
                .map { state ->
                    val name = state.attributes?.get("friendly_name") as? String
                        ?: state.entity_id
                    val icon = state.attributes?.get("icon") as? String
                    Triple(state.entity_id, name, icon)
                }
                .sortedBy { it.second }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

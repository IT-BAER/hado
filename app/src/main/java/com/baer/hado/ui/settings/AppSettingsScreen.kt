package com.baer.hado.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baer.hado.BuildConfig
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.widget.ListIconManager
import com.baer.hado.widget.TodoWidgetWorker
import com.baer.hado.widget.WidgetHttpClient
import com.baer.hado.widget.WidgetSettings
import com.baer.hado.widget.WidgetSettingsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var availableLists by remember { mutableStateOf<List<Triple<String, String, String?>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            availableLists = fetchLists(context)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // --- Demo mode banner ---
            if (TokenManager(context).isDemoMode) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Local Mode — your lists are stored on this device. Log out to connect to Home Assistant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // --- Refresh interval ---
            RefreshIntervalSection()

            // --- List icons ---
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (availableLists.isNotEmpty()) {
                AppListIconsSection(availableLists = availableLists)
            }

            // --- Logout ---
            SettingsSection(title = "Account") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                        TokenManager(context).clearAll()
                        onLogout()
                    }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Logout",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // --- About ---
            AboutSection()

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    SettingsSection(title = "About") {
        Column {
            // Version
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Developer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Developer",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "IT-BAER",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Privacy Policy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/IT-BAER/hado/blob/master/PRIVACY.md")
                        )
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Source Code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/IT-BAER/hado")
                        )
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source Code",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RefreshIntervalSection() {
    val context = LocalContext.current
    var refreshInterval by remember {
        mutableStateOf(WidgetSettingsManager.loadRefreshInterval(context))
    }

    SettingsSection(title = "Refresh Interval") {
        Column {
            WidgetSettings.RefreshInterval.entries.forEachIndexed { index, interval ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            refreshInterval = interval
                            WidgetSettingsManager.saveRefreshInterval(context, interval)
                            TodoWidgetWorker.enqueuePeriodic(context)
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = refreshInterval == interval,
                        onClick = {
                            refreshInterval = interval
                            WidgetSettingsManager.saveRefreshInterval(context, interval)
                            TodoWidgetWorker.enqueuePeriodic(context)
                        }
                    )
                    Text(
                        text = interval.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (index < WidgetSettings.RefreshInterval.entries.size - 1) {
                    HorizontalDivider()
                }
            }
            Text(
                text = "How often the widget fetches new data from Home Assistant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AppListIconsSection(
    availableLists: List<Triple<String, String, String?>>
) {
    val context = LocalContext.current
    var iconDialogEntityId by remember { mutableStateOf<String?>(null) }
    var iconDialogListName by remember { mutableStateOf("") }
    var iconDialogHaIcon by remember { mutableStateOf<String?>(null) }
    var iconVersion by remember { mutableIntStateOf(0) }

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

    SettingsSection(title = "List Icons") {
        availableLists.forEach { (entityId, name, haIcon) ->
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
                    text = "Tap to change",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (iconDialogEntityId != null) {
        IconPickerDialog(
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
        title = { Text("Icon for $listName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (haIcon != null) {
                    Text(
                        text = "HA icon: $haIcon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = emojiInput,
                    onValueChange = { input ->
                        emojiInput = input.filter { it !in 'a'..'z' && it !in 'A'..'Z' }
                    },
                    label = { Text("Emoji") },
                    placeholder = { Text("e.g. 🛒 📋 ⭐") },
                    singleLine = true,
                    isError = emojiInput.isNotBlank() && !isValidEmoji,
                    supportingText = if (emojiInput.isNotBlank() && !isValidEmoji) {
                        { Text("Only emoji allowed") }
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
                                Icon(Icons.Default.Check, contentDescription = "Apply")
                            }
                        }
                    }
                )

                OutlinedButton(
                    onClick = onImagePick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📷")
                    Spacer(Modifier.width(8.dp))
                    Text("Choose image")
                }

                // Clear button — reset to default (📋)
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to default")
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
                    Text("No icon")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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

private suspend fun fetchLists(context: android.content.Context): List<Triple<String, String, String?>> {
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

            data class SimpleState(
                val entity_id: String,
                val attributes: Map<String, Any>?
            )

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

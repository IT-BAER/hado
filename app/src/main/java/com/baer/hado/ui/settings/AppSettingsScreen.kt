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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baer.hado.BuildConfig
import com.baer.hado.R
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.SimpleState
import com.baer.hado.ui.theme.AppSpacing
import com.baer.hado.widget.ListIconManager
import com.baer.hado.widget.WidgetHttpClient
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
                title = {
                    Text(
                        text = stringResource(R.string.title_settings),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                .padding(horizontal = AppSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
        ) {
            Spacer(Modifier.height(4.dp))

            if (TokenManager(context).isDemoMode) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.local_mode_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            if (isLoading) {
                SettingsSection(title = stringResource(R.string.section_list_icons)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (availableLists.isNotEmpty()) {
                AppListIconsSection(availableLists = availableLists)
            }

            SettingsSection(title = stringResource(R.string.section_account)) {
                SettingsItem(
                    headline = stringResource(R.string.action_logout),
                    headlineColor = MaterialTheme.colorScheme.error,
                    supporting = stringResource(R.string.settings_logout_supporting),
                    onClick = {
                        TokenManager(context).clearAll()
                        onLogout()
                    },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }

            AboutSection()

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    SettingsSection(title = stringResource(R.string.section_about)) {
        SettingsItem(
            headline = stringResource(R.string.label_version),
            trailingText = BuildConfig.VERSION_NAME
        )

        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

        SettingsItem(
            headline = stringResource(R.string.label_developer),
            trailingText = "IT-BAER"
        )

        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

        SettingsItem(
            headline = stringResource(R.string.label_privacy_policy),
            trailingText = stringResource(R.string.settings_open_link),
            onClick = {
                openExternalUrl(
                    context = context,
                    url = "https://github.com/IT-BAER/hado/blob/master/PRIVACY.md"
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

        SettingsItem(
            headline = stringResource(R.string.label_source_code),
            trailingText = stringResource(R.string.settings_open_link),
            onClick = {
                openExternalUrl(
                    context = context,
                    url = "https://github.com/IT-BAER/hado"
                )
            }
        )
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

    SettingsSection(title = stringResource(R.string.section_list_icons)) {
        availableLists.forEach { (entityId, name, haIcon) ->
            val resolved = remember(entityId, iconVersion) {
                ListIconManager.resolveIcon(context, entityId, haIcon)
            }

            SettingsItem(
                headline = name,
                supporting = stringResource(R.string.label_tap_to_change),
                onClick = {
                        iconDialogEntityId = entityId
                        iconDialogListName = name
                        iconDialogHaIcon = haIcon
                    },
                leadingContent = {
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
                },
                trailingText = stringResource(R.string.label_tap_to_change)
            )

            if (entityId != availableLists.last().first) {
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
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

                OutlinedTextField(
                    value = emojiInput,
                    onValueChange = { input ->
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
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailingText: String? = null,
    headlineColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .let {
                if (onClick != null) {
                    it.clickable(onClick = onClick)
                } else {
                    it
                }
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            leadingContent?.invoke()
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                color = headlineColor
            )
            if (supporting != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
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

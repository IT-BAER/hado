package com.baer.hado.ui.settings

import android.Manifest
import android.app.LocaleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.core.content.ContextCompat
import com.baer.hado.BuildConfig
import com.baer.hado.R
import com.baer.hado.data.local.AddItemPosition
import com.baer.hado.data.local.AppPreferencesManager
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.SimpleState
import com.baer.hado.notifications.OverdueNotificationSettings
import com.baer.hado.notifications.OverdueNotificationSettingsManager
import com.baer.hado.notifications.OverdueNotificationScheduler
import com.baer.hado.ui.theme.AppSpacing
import com.baer.hado.widget.ListIconHaSyncManager
import com.baer.hado.widget.ListIconManager
import com.baer.hado.widget.ListIconPickerDialog
import com.baer.hado.widget.ListIconPreview
import com.baer.hado.widget.WidgetHttpClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class AppLanguageOption(
    val tag: String?,
    val locale: Locale?
) {
    fun label(context: android.content.Context): String {
        if (tag.isNullOrBlank() || locale == null) {
            return context.getString(R.string.settings_language_system)
        }

        val displayName = locale.getDisplayName(locale)
        return displayName.replaceFirstChar { firstChar ->
            if (firstChar.isLowerCase()) {
                firstChar.titlecase(locale)
            } else {
                firstChar.toString()
            }
        }
    }
}

private val appLanguageOptions = listOf(
    AppLanguageOption(tag = null, locale = null),
    AppLanguageOption(tag = "en", locale = Locale.ENGLISH),
    AppLanguageOption(tag = "de", locale = Locale.GERMAN),
    AppLanguageOption(tag = "es", locale = Locale("es")),
    AppLanguageOption(tag = "fr", locale = Locale.FRENCH),
    AppLanguageOption(tag = "nl", locale = Locale("nl")),
    AppLanguageOption(tag = "pt-BR", locale = Locale("pt", "BR")),
    AppLanguageOption(tag = "ru", locale = Locale("ru")),
    AppLanguageOption(tag = "ja", locale = Locale.JAPANESE),
    AppLanguageOption(tag = "ko", locale = Locale.KOREAN),
    AppLanguageOption(tag = "zh-CN", locale = Locale.SIMPLIFIED_CHINESE)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedLanguageTag by remember { mutableStateOf(currentAppLanguageTag(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var notificationSettings by remember {
        mutableStateOf(OverdueNotificationSettingsManager.load(context))
    }
    var notificationPermissionDenied by remember { mutableStateOf(false) }
    var showTimingDialog by remember { mutableStateOf(false) }
    var showCadenceDialog by remember { mutableStateOf(false) }
    var showNotificationListsDialog by remember { mutableStateOf(false) }

    var availableLists by remember { mutableStateOf<List<Triple<String, String, String?>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationPermissionDenied = false
            notificationSettings = notificationSettings.copy(enabled = true)
            OverdueNotificationSettingsManager.save(context, notificationSettings)
            OverdueNotificationScheduler.reschedule(context)
        } else {
            notificationPermissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            availableLists = fetchLists(context)
            isLoading = false
        }
    }

    LaunchedEffect(isLoading, availableLists) {
        if (isLoading) return@LaunchedEffect
        if (notificationSettings.selectedListIds.isEmpty()) return@LaunchedEffect

        val availableIds = availableLists.mapTo(mutableSetOf()) { it.first }
        val validSelectedIds = notificationSettings.selectedListIds.intersect(availableIds)
        if (validSelectedIds == notificationSettings.selectedListIds) return@LaunchedEffect

        notificationSettings = notificationSettings.copy(selectedListIds = validSelectedIds)
        OverdueNotificationSettingsManager.save(context, notificationSettings)
        OverdueNotificationScheduler.reschedule(context)
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

            SettingsSection(title = stringResource(R.string.section_behavior)) {
                val addPosition = remember { mutableStateOf(AppPreferencesManager.loadAddItemPosition(context)) }
                SettingsItem(
                    headline = stringResource(R.string.settings_add_item_position),
                    supporting = stringResource(R.string.settings_add_item_position_desc),
                    trailingText = when (addPosition.value) {
                        AddItemPosition.TOP -> stringResource(R.string.add_position_top)
                        AddItemPosition.BOTTOM -> stringResource(R.string.add_position_bottom)
                    },
                    onClick = {
                        val newPosition = when (addPosition.value) {
                            AddItemPosition.TOP -> AddItemPosition.BOTTOM
                            AddItemPosition.BOTTOM -> AddItemPosition.TOP
                        }
                        addPosition.value = newPosition
                        AppPreferencesManager.saveAddItemPosition(context, newPosition)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                var checkboxOnly by remember { mutableStateOf(AppPreferencesManager.loadCheckboxOnly(context)) }
                SettingsToggleItem(
                    headline = stringResource(R.string.settings_app_checkbox_only),
                    supporting = stringResource(R.string.settings_app_checkbox_only_desc),
                    checked = checkboxOnly,
                    onCheckedChange = { enabled ->
                        checkboxOnly = enabled
                        AppPreferencesManager.saveCheckboxOnly(context, enabled)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.CheckBox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                var hideCompleted by remember { mutableStateOf(AppPreferencesManager.loadHideCompleted(context)) }
                SettingsToggleItem(
                    headline = stringResource(R.string.settings_hide_completed),
                    supporting = stringResource(R.string.settings_hide_completed_desc),
                    checked = hideCompleted,
                    onCheckedChange = { enabled ->
                        hideCompleted = enabled
                        AppPreferencesManager.saveHideCompleted(context, enabled)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            SettingsSection(title = stringResource(R.string.section_language)) {
                SettingsItem(
                    headline = stringResource(R.string.label_app_language),
                    supporting = stringResource(R.string.settings_language_supporting),
                    trailingText = languageLabel(context, selectedLanguageTag),
                    onClick = { showLanguageDialog = true },
                    leadingContent = {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            SettingsSection(title = stringResource(R.string.section_notifications)) {
                SettingsToggleItem(
                    headline = stringResource(R.string.settings_overdue_notifications),
                    supporting = if (notificationPermissionDenied) {
                        stringResource(R.string.settings_notifications_permission_required)
                    } else {
                        stringResource(R.string.settings_notifications_supporting)
                    },
                    checked = notificationSettings.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (hasNotificationPermission(context)) {
                                notificationPermissionDenied = false
                                notificationSettings = notificationSettings.copy(enabled = true)
                                OverdueNotificationSettingsManager.save(context, notificationSettings)
                                OverdueNotificationScheduler.reschedule(context)
                            } else {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            notificationPermissionDenied = false
                            notificationSettings = notificationSettings.copy(enabled = false)
                            OverdueNotificationSettingsManager.save(context, notificationSettings)
                            OverdueNotificationScheduler.reschedule(context)
                        }
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                SettingsItem(
                    headline = stringResource(R.string.settings_notification_timing),
                    supporting = stringResource(R.string.settings_overdue_notifications_off),
                    trailingText = stringResource(notificationSettings.timing.labelResId),
                    onClick = { showTimingDialog = true },
                    leadingContent = {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                SettingsItem(
                    headline = stringResource(R.string.settings_notification_repeat),
                    supporting = stringResource(R.string.settings_notifications_supporting),
                    trailingText = stringResource(notificationSettings.cadence.labelResId),
                    onClick = { showCadenceDialog = true },
                    leadingContent = {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (!isLoading && availableLists.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

                    SettingsItem(
                        headline = stringResource(R.string.settings_notification_lists),
                        supporting = stringResource(R.string.settings_notification_lists_supporting),
                        trailingText = notificationListSummary(
                            context = context,
                            settings = notificationSettings,
                            availableLists = availableLists
                        ),
                        onClick = { showNotificationListsDialog = true },
                        leadingContent = {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.section_account)) {
                SettingsItem(
                    headline = stringResource(R.string.action_logout),
                    headlineColor = MaterialTheme.colorScheme.error,
                    supporting = stringResource(R.string.settings_logout_supporting),
                    onClick = {
                        TokenManager(context).clearAll()
                        OverdueNotificationScheduler.cancelAll(context)
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

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.label_app_language)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    appLanguageOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedLanguageTag = option.tag
                                    applyAppLanguage(context, option.tag)
                                    showLanguageDialog = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguageTag == option.tag,
                                onClick = {
                                    selectedLanguageTag = option.tag
                                    applyAppLanguage(context, option.tag)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option.label(context),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showTimingDialog) {
        SingleChoiceSettingsDialog(
            title = stringResource(R.string.settings_notification_timing),
            selectedValue = notificationSettings.timing,
            options = OverdueNotificationSettings.NotificationTiming.entries,
            labelFor = { stringResource(it.labelResId) },
            onDismiss = { showTimingDialog = false },
            onSelected = { timing ->
                notificationSettings = notificationSettings.copy(timing = timing)
                OverdueNotificationSettingsManager.save(context, notificationSettings)
                OverdueNotificationScheduler.reschedule(context)
                showTimingDialog = false
            }
        )
    }

    if (showCadenceDialog) {
        SingleChoiceSettingsDialog(
            title = stringResource(R.string.settings_notification_repeat),
            selectedValue = notificationSettings.cadence,
            options = OverdueNotificationSettings.ReminderCadence.entries,
            labelFor = { stringResource(it.labelResId) },
            onDismiss = { showCadenceDialog = false },
            onSelected = { cadence ->
                notificationSettings = notificationSettings.copy(cadence = cadence)
                OverdueNotificationSettingsManager.save(context, notificationSettings)
                OverdueNotificationScheduler.reschedule(context)
                showCadenceDialog = false
            }
        )
    }

    if (showNotificationListsDialog) {
        NotificationListsDialog(
            availableLists = availableLists,
            selectedListIds = notificationSettings.selectedListIds,
            onDismiss = { showNotificationListsDialog = false },
            onSave = { selectedIds ->
                notificationSettings = notificationSettings.copy(selectedListIds = selectedIds)
                OverdueNotificationSettingsManager.save(context, notificationSettings)
                OverdueNotificationScheduler.reschedule(context)
                showNotificationListsDialog = false
            }
        )
    }
}

private fun notificationListSummary(
    context: android.content.Context,
    settings: OverdueNotificationSettings,
    availableLists: List<Triple<String, String, String?>>
): String {
    if (settings.selectedListIds.isEmpty()) {
        return context.getString(R.string.settings_all_lists)
    }

    val selectedNames = availableLists
        .filter { it.first in settings.selectedListIds }
        .map { it.second }

    return when (selectedNames.size) {
        0 -> context.getString(R.string.settings_all_lists)
        1 -> selectedNames.first()
        else -> context.getString(R.string.settings_selected_lists_count, selectedNames.size)
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun currentAppLanguageTag(context: android.content.Context): String? {
    val localeManager = context.getSystemService(LocaleManager::class.java) ?: return null
    val languageTags = localeManager.applicationLocales.toLanguageTags()
    return languageTags.ifBlank { null }
}

private fun applyAppLanguage(context: android.content.Context, languageTag: String?) {
    val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
    localeManager.applicationLocales = if (languageTag.isNullOrBlank()) {
        LocaleList.getEmptyLocaleList()
    } else {
        LocaleList.forLanguageTags(languageTag)
    }
}

private fun languageLabel(context: android.content.Context, languageTag: String?): String {
    val exactMatch = appLanguageOptions.firstOrNull { it.tag == languageTag }
    if (exactMatch != null) {
        return exactMatch.label(context)
    }

    if (languageTag.isNullOrBlank()) {
        return context.getString(R.string.settings_language_system)
    }

    val locale = Locale.forLanguageTag(languageTag)
    val displayName = locale.getDisplayName(locale)
    return displayName.replaceFirstChar { firstChar ->
        if (firstChar.isLowerCase()) {
            firstChar.titlecase(locale)
        } else {
            firstChar.toString()
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    SettingsSection(title = stringResource(R.string.section_about)) {
        SettingsItem(
            headline = stringResource(R.string.label_version),
            trailingText = BuildConfig.VERSION_NAME,
            leadingContent = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

        SettingsItem(
            headline = stringResource(R.string.label_developer),
            trailingText = "IT-BAER",
            leadingContent = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            },
            leadingContent = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            },
            leadingContent = {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    val scope = rememberCoroutineScope()
    var syncAvailability by remember {
        mutableStateOf(ListIconHaSyncManager.SyncAvailability.UNAVAILABLE)
    }
    var iconDialogEntityId by remember { mutableStateOf<String?>(null) }
    var iconDialogListName by remember { mutableStateOf("") }
    var iconDialogHaIcon by remember { mutableStateOf<String?>(null) }
    var iconDialogResolvedIcon by remember { mutableStateOf<ListIconManager.ResolvedIcon?>(null) }
    var iconVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(context) {
        syncAvailability = withContext(Dispatchers.IO) {
            ListIconHaSyncManager.getSyncAvailability(context)
        }
    }

    var pendingImageEntityId by remember { mutableStateOf<String?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val entityId = pendingImageEntityId
        if (uri != null && entityId != null) {
            val saved = ListIconManager.setImage(context, entityId, uri)
            if (saved) {
                iconVersion++
                scope.launch(Dispatchers.IO) {
                    ListIconHaSyncManager.restoreOriginalIconIfNeeded(context, entityId)
                }
            }
            iconDialogEntityId = null
        }
        pendingImageEntityId = null
    }

    SettingsSection(title = stringResource(R.string.section_list_icons)) {
        if (syncAvailability == ListIconHaSyncManager.SyncAvailability.REQUIRES_ADMIN) {
            Text(
                text = stringResource(R.string.list_icon_sync_requires_admin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

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
                    iconDialogResolvedIcon = resolved
                    },
                leadingContent = {
                    resolved?.let {
                        ListIconPreview(
                            resolvedIcon = it,
                            size = 32.dp,
                            emojiColor = MaterialTheme.colorScheme.onSurface,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } ?: NoIconPlaceholder()
                },
                trailingText = stringResource(R.string.label_tap_to_change)
            )

            if (entityId != availableLists.last().first) {
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }
        }
    }

    if (iconDialogEntityId != null) {
        ListIconPickerDialog(
            listName = iconDialogListName,
            haIcon = iconDialogHaIcon,
            currentIcon = iconDialogResolvedIcon,
            onMdiPicked = { mdiIcon ->
                val entityId = iconDialogEntityId!!
                val currentHaIcon = iconDialogHaIcon
                ListIconManager.setMdi(context, entityId, mdiIcon)
                iconVersion++
                scope.launch(Dispatchers.IO) {
                    ListIconHaSyncManager.syncMdiOverride(
                        context = context,
                        entityId = entityId,
                        mdiIcon = mdiIcon,
                        currentHaIcon = currentHaIcon
                    )
                }
                iconDialogEntityId = null
            },
            onEmojiPicked = { emoji ->
                val entityId = iconDialogEntityId!!
                val currentHaIcon = iconDialogHaIcon
                ListIconManager.setEmoji(context, entityId, emoji)
                iconVersion++
                scope.launch(Dispatchers.IO) {
                    ListIconHaSyncManager.syncEmojiOverride(
                        context = context,
                        entityId = entityId,
                        emoji = emoji,
                        currentHaIcon = currentHaIcon
                    )
                }
                iconDialogEntityId = null
                iconDialogResolvedIcon = null
            },
            onImagePick = {
                pendingImageEntityId = iconDialogEntityId
                imagePickerLauncher.launch("image/*")
            },
            onClear = {
                val entityId = iconDialogEntityId!!
                ListIconManager.clearIcon(context, entityId)
                iconVersion++
                scope.launch(Dispatchers.IO) {
                    ListIconHaSyncManager.restoreOriginalIconIfNeeded(context, entityId)
                }
                iconDialogEntityId = null
                iconDialogResolvedIcon = null
            },
            onDismiss = {
                iconDialogEntityId = null
                iconDialogResolvedIcon = null
            }
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
private fun SettingsToggleItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
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
                color = MaterialTheme.colorScheme.onSurface
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

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun <T> SingleChoiceSettingsDialog(
    title: String,
    selectedValue: T,
    options: Iterable<T>,
    labelFor: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelected(option) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedValue,
                            onClick = { onSelected(option) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = labelFor(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun NotificationListsDialog(
    availableLists: List<Triple<String, String, String?>>,
    selectedListIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var tempSelected by remember(selectedListIds) { mutableStateOf(selectedListIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_notification_lists)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { tempSelected = emptySet() }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = tempSelected.isEmpty(),
                        onCheckedChange = { checked ->
                            if (checked == true) tempSelected = emptySet()
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_all_lists),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                availableLists.forEach { (entityId, name, _) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                tempSelected = if (entityId in tempSelected) {
                                    tempSelected - entityId
                                } else {
                                    tempSelected + entityId
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = entityId in tempSelected,
                            onCheckedChange = { checked ->
                                tempSelected = if (checked == true) {
                                    tempSelected + entityId
                                } else {
                                    tempSelected - entityId
                                }
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempSelected) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
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

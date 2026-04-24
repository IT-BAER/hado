package com.baer.hado.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.baer.hado.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListIconPickerDialog(
    listName: String,
    haIcon: String?,
    currentIcon: ListIconManager.ResolvedIcon?,
    onMdiPicked: (String) -> Unit,
    onEmojiPicked: (String) -> Unit,
    onImagePick: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mdiInput by remember(currentIcon) {
        mutableStateOf(
            if (currentIcon?.type == ListIconManager.IconType.MDI) currentIcon.value else ""
        )
    }
    var emojiInput by remember(currentIcon) {
        mutableStateOf(
            if (currentIcon?.type == ListIconManager.IconType.EMOJI) currentIcon.value else ""
        )
    }
    var mdiExpanded by remember { mutableStateOf(false) }

    val normalizedMdiInput = remember(mdiInput) { ListIconManager.normalizeMdiIcon(mdiInput) }
    val mdiSuggestions = remember(mdiInput) {
        ListIconManager.searchSupportedMdiIcons(mdiInput, limit = 8)
    }
    val exactMdiIcon = remember(normalizedMdiInput) {
        normalizedMdiInput?.takeIf { ListIconManager.canRenderMdiIcon(context, it) }
    }
    val isValidEmoji = emojiInput.isNotBlank() && !emojiInput.any { it in 'a'..'z' || it in 'A'..'Z' }
    val currentHaResolvedIcon = remember(haIcon) {
        ListIconManager.normalizeMdiIcon(haIcon)?.let { mdiIcon ->
            ListIconManager.ResolvedIcon(
                type = ListIconManager.IconType.MDI,
                value = mdiIcon,
                fallbackEmoji = ListIconManager.mapMdiToEmoji(mdiIcon)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.dialog_icon_title, listName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (haIcon != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentHaResolvedIcon != null) {
                            ListIconPreview(
                                resolvedIcon = currentHaResolvedIcon,
                                size = 20.dp,
                                emojiColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(R.string.label_ha_icon, haIcon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = mdiInput,
                    onValueChange = { input ->
                        mdiInput = input.trim()
                        mdiExpanded = true
                    },
                    label = { Text(stringResource(R.string.label_mdi_icon)) },
                    placeholder = { Text(stringResource(R.string.mdi_icon_placeholder)) },
                    supportingText = {
                        Text(
                            text = if (mdiInput.isNotBlank() && mdiSuggestions.isEmpty() && exactMdiIcon == null) {
                                stringResource(R.string.mdi_icon_no_match)
                            } else {
                                stringResource(R.string.mdi_icon_supporting)
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            exactMdiIcon?.let(onMdiPicked)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        exactMdiIcon?.let { mdiIcon ->
                            ListIconPreview(
                                resolvedIcon = ListIconManager.ResolvedIcon(
                                    type = ListIconManager.IconType.MDI,
                                    value = mdiIcon,
                                    fallbackEmoji = ListIconManager.mapMdiToEmoji(mdiIcon)
                                ),
                                size = 20.dp,
                                emojiColor = MaterialTheme.colorScheme.onSurface,
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    },
                    trailingIcon = {
                        if (exactMdiIcon != null) {
                            IconButton(onClick = { onMdiPicked(exactMdiIcon) }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.cd_apply)
                                )
                            }
                        }
                    }
                )

                if (mdiExpanded && mdiInput.isNotBlank() && mdiSuggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            mdiSuggestions.forEachIndexed { index, mdiIcon ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .clickable {
                                            mdiInput = mdiIcon
                                            mdiExpanded = false
                                            onMdiPicked(mdiIcon)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ListIconPreview(
                                        resolvedIcon = ListIconManager.ResolvedIcon(
                                            type = ListIconManager.IconType.MDI,
                                            value = mdiIcon,
                                            fallbackEmoji = ListIconManager.mapMdiToEmoji(mdiIcon)
                                        ),
                                        size = 20.dp,
                                        emojiColor = MaterialTheme.colorScheme.onSurface,
                                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        text = mdiIcon,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (index < mdiSuggestions.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
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

                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_reset_default))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
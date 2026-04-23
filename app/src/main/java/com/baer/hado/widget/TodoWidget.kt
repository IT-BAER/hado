package com.baer.hado.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.Image
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.baer.hado.R
import com.baer.hado.data.model.TodoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class WidgetListData(
    val entityId: String,
    val name: String,
    val items: List<TodoItem>,
    val iconType: String? = null,  // "emoji" or "image"
    val iconValue: String? = null,  // emoji chars or absolute file path
    val supportedFeatures: Int? = null
)

class TodoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val listsJson = prefs[TodoWidgetKeys.ALL_LISTS_KEY] ?: "[]"
        val pendingToggleJson = prefs[TodoWidgetKeys.PENDING_TOGGLE_IDS_KEY] ?: "[]"
        val settingsJson = prefs[TodoWidgetKeys.SETTINGS_JSON_KEY]
        val appWidgetId = prefs[TodoWidgetKeys.APP_WIDGET_ID_KEY] ?: ""

        val allLists = try {
            val type = object : TypeToken<List<WidgetListData>>() {}.type
            Gson().fromJson<List<WidgetListData>>(listsJson, type)
        } catch (_: Exception) {
            emptyList()
        }

        val settings = try {
            if (settingsJson != null) Gson().fromJson(settingsJson, WidgetSettings::class.java)
            else WidgetSettings()
        } catch (_: Exception) {
            WidgetSettings()
        }

        val pendingToggleIds = try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>>(pendingToggleJson, type).toSet()
        } catch (_: Exception) {
            emptySet()
        }
        val context = LocalContext.current

        // Apply display filters
        val lists = allLists.let { raw ->
            val selectedLists = if (settings.selectedListIds.isNotEmpty()) {
                raw.filter { it.entityId in settings.selectedListIds }
            } else {
                raw
            }
            if (!settings.showCompleted) {
                selectedLists.map { list -> list.copy(items = list.items.filter { !it.isCompleted }) }
            } else {
                selectedLists
            }
        }

        val outerPadding = if (settings.compactMode) 6.dp else 12.dp
        val headerBottomPad = if (settings.compactMode) 4.dp else 8.dp
        val isDarkTheme = (LocalContext.current.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgModifier = if (settings.backgroundOpacity < 1.0f) {
            val baseColor = if (isDarkTheme) Color.Black else Color.White
            GlanceModifier.background(color = baseColor.copy(alpha = settings.backgroundOpacity))
        } else {
            GlanceModifier.background(colorProvider = GlanceTheme.colors.widgetBackground)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .then(bgModifier)
                .cornerRadius(16.dp)
                .padding(vertical = outerPadding)
        ) {
            // Top bar
            if (settings.showTitle) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = headerBottomPad, start = outerPadding, end = outerPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(actionRunCallback<OpenAppAction>()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.mipmap.ic_launcher_monochrome),
                            contentDescription = null,
                            modifier = GlanceModifier.size(48.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = LocalContext.current.getString(R.string.app_name),
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = settings.fontSize.titleSp.sp,
                                color = GlanceTheme.colors.onSurface
                            )
                        )
                    }
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_refresh),
                        contentDescription = LocalContext.current.getString(R.string.cd_refresh),
                        modifier = GlanceModifier
                            .size(22.dp)
                            .clickable(actionRunCallback<RefreshAction>()),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }

            if (lists.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = LocalContext.current.getString(R.string.widget_no_lists),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = settings.fontSize.itemSp.sp
                        )
                    )
                }
            } else {
                val rows = mutableListOf<WidgetRow>()
                val anyListHasIcon = settings.showListIcons && lists.any { list ->
                    (list.iconType != null && list.iconValue != null) ||
                        ListIconManager.resolveIcon(context, list.entityId) != null
                }
                lists.forEachIndexed { index, list ->
                    rows.add(WidgetRow.Header(list.entityId, list.name, list.iconType, list.iconValue, list.supportedFeatures, isFirstHeader = index == 0))
                    val activeItems = list.items.filter { !it.isCompleted }
                    val completedItems = list.items.filter { it.isCompleted }
                    if (activeItems.isEmpty() && completedItems.isEmpty()) {
                        rows.add(WidgetRow.EmptyHint(list.entityId))
                    }
                    for (item in activeItems) {
                        rows.add(WidgetRow.Item(list.entityId, item, list.name, list.supportedFeatures))
                    }
                    for (item in completedItems) {
                        rows.add(WidgetRow.Item(list.entityId, item, list.name, list.supportedFeatures))
                    }
                }

                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(rows, itemId = { it.stableId }) { row ->
                        when (row) {
                            is WidgetRow.Header -> ListHeader(row.entityId, row.name, row.iconType, row.iconValue, row.supportedFeatures, row.isFirstHeader, settings, anyListHasIcon)
                            is WidgetRow.Item -> TodoItemRow(row.item, row.entityId, row.listName, row.supportedFeatures, settings, pendingToggleIds.contains(pendingToggleKey(row.entityId, row.item.uid)))
                            is WidgetRow.EmptyHint -> EmptyHintRow(settings)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ListHeader(entityId: String, name: String, iconType: String?, iconValue: String?, supportedFeatures: Int?, isFirstHeader: Boolean, settings: WidgetSettings, anyListHasIcon: Boolean) {
        val topPad = when {
            settings.compactMode && isFirstHeader -> 10.dp
            settings.compactMode -> 6.dp
            isFirstHeader -> 16.dp
            else -> 10.dp
        }
        val bottomPad = if (settings.compactMode) 6.dp else 8.dp
        val iconSize = (settings.fontSize.titleSp * 1.4f).dp
        val context = LocalContext.current
        val fallbackIcon = if (settings.showListIcons && (iconType == null || iconValue == null)) {
            ListIconManager.resolveIcon(context, entityId)
        } else {
            null
        }
        val effectiveIconType = fallbackIcon?.type?.name?.lowercase() ?: iconType
        val effectiveIconValue = fallbackIcon?.value ?: iconValue

        val hzPad = if (settings.compactMode) 6.dp else 12.dp

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(
                    actionRunCallback<AddItemAction>(
                        actionParametersOf(
                            AddItemAction.PARAM_ENTITY_ID to entityId,
                            AddItemAction.PARAM_LIST_NAME to name,
                            AddItemAction.PARAM_SUPPORTED_FEATURES to (supportedFeatures ?: 0).toString(),
                            AddItemAction.PARAM_AUTO_FOCUS to settings.autoFocusOnOpen.toString()
                        )
                    )
                )
                .padding(top = topPad, bottom = bottomPad, start = hzPad, end = hzPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon: emoji as text, image as bitmap, or placeholder for alignment
            if (settings.showListIcons) {
                if (effectiveIconType == "emoji" && effectiveIconValue != null) {
                    Text(
                        text = effectiveIconValue,
                        style = TextStyle(fontSize = settings.fontSize.titleSp.sp),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(6.dp))
                } else if (effectiveIconType == "image" && effectiveIconValue != null) {
                    val bitmap = BitmapFactory.decodeFile(effectiveIconValue)
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = null,
                            modifier = GlanceModifier
                                .size(iconSize)
                                .cornerRadius(iconSize / 2)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                    } else {
                        val defaultIcon = ListIconManager.resolveIcon(context, entityId)
                        if (defaultIcon?.type == ListIconManager.IconType.EMOJI) {
                            Text(
                                text = defaultIcon.value,
                                style = TextStyle(fontSize = settings.fontSize.titleSp.sp),
                                maxLines = 1
                            )
                            Spacer(GlanceModifier.width(6.dp))
                        } else if (anyListHasIcon) {
                            Spacer(GlanceModifier.width(iconSize + 6.dp))
                        }
                    }
                } else if (anyListHasIcon) {
                    // Placeholder spacer to align with lists that have icons
                    Spacer(GlanceModifier.width(iconSize + 6.dp))
                }
            }
            Text(
                text = name,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = settings.fontSize.titleSp.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
        }
    }

    @Composable
    private fun TodoItemRow(item: TodoItem, entityId: String, listName: String, supportedFeatures: Int?, settings: WidgetSettings, isPending: Boolean) {
        val vertPad = settings.itemHeight.rowVerticalPaddingDp.dp
        val hzPad = if (settings.compactMode) 6.dp else 12.dp
        val overdueModifier = if (item.isOverdue) {
            GlanceModifier.background(color = Color(0x18D32F2F))
        } else GlanceModifier

        val toggleAction = actionRunCallback<ToggleItemAction>(
            actionParametersOf(
                ToggleItemAction.PARAM_ENTITY_ID to entityId,
                ToggleItemAction.PARAM_ITEM_UID to item.uid,
                ToggleItemAction.PARAM_COMPLETED to item.isCompleted.toString()
            )
        )

        val viewAction = actionRunCallback<ViewItemAction>(
            actionParametersOf(
                ViewItemAction.PARAM_ENTITY_ID to entityId,
                ViewItemAction.PARAM_LIST_NAME to listName,
                ViewItemAction.PARAM_SUPPORTED_FEATURES to (supportedFeatures ?: 0).toString(),
                ViewItemAction.PARAM_ITEM_UID to item.uid
            )
        )

        val startPad = if (hzPad > 8.dp) (hzPad - 8.dp) else 2.dp
        val rowModifier = GlanceModifier
            .fillMaxWidth()
            .then(overdueModifier)
            .padding(start = startPad, top = vertPad, end = hzPad, bottom = vertPad)
            .let { if (!settings.checkboxOnly && !isPending) it.clickable(toggleAction) else it }

        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .let { if (settings.checkboxOnly && !isPending) it.clickable(toggleAction) else it },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        when {
                            isPending -> R.drawable.ic_widget_refresh
                            item.isCompleted -> R.drawable.ic_widget_checked
                            else -> R.drawable.ic_widget_unchecked
                        }
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(if (isPending) 18.dp else 20.dp),
                    colorFilter = ColorFilter.tint(
                        when {
                            isPending -> GlanceTheme.colors.onSurfaceVariant
                            item.isCompleted -> GlanceTheme.colors.outline
                            else -> GlanceTheme.colors.primary
                        }
                    )
                )
            }
            Column(modifier = GlanceModifier.defaultWeight()
                .let { if (settings.checkboxOnly && !isPending) it.clickable(viewAction) else it }
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.summary,
                        style = TextStyle(
                            fontSize = settings.fontSize.itemSp.sp,
                            color = if (item.isCompleted) GlanceTheme.colors.outline
                            else GlanceTheme.colors.onSurface,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough
                            else TextDecoration.None
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    if (item.due != null && !item.isCompleted) {
                        val dueText = buildWidgetDueText(LocalContext.current, item)
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = dueText,
                            style = TextStyle(
                                fontSize = (settings.fontSize.itemSp - 2).sp,
                                color = if (item.isOverdue) GlanceTheme.colors.error
                                else GlanceTheme.colors.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }
                if (!item.description.isNullOrBlank() && !item.isCompleted) {
                    Text(
                        text = item.description!!.lines().first(),
                        style = TextStyle(
                            fontSize = (settings.fontSize.itemSp - 2).sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    private fun buildWidgetDueText(context: Context, item: TodoItem): String {
        val now = java.time.LocalDate.now()
        val icon = if (item.isOverdue) "⚠ " else "📅 "

        val dt = item.dueDateTime
        if (dt != null) {
            val dueDate = dt.toLocalDate()
            val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(now, dueDate)
            val timeStr = dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            val relative = when {
                daysDiff < -1L -> context.getString(R.string.due_days_ago, -daysDiff)
                daysDiff == -1L -> context.getString(R.string.due_yesterday)
                daysDiff == 0L -> context.getString(R.string.due_today_time, timeStr)
                daysDiff == 1L -> context.getString(R.string.due_tomorrow_time, timeStr)
                daysDiff <= 7L -> context.getString(R.string.due_in_days, daysDiff)
                else -> dt.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
            }
            return icon + relative
        }

        val d = item.dueDate
        if (d != null) {
            val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(now, d)
            val relative = when {
                daysDiff < -1L -> context.getString(R.string.due_days_ago, -daysDiff)
                daysDiff == -1L -> context.getString(R.string.due_yesterday)
                daysDiff == 0L -> context.getString(R.string.due_today)
                daysDiff == 1L -> context.getString(R.string.due_tomorrow)
                daysDiff <= 7L -> context.getString(R.string.due_in_days, daysDiff)
                else -> d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
            }
            return icon + relative
        }

        return ""
    }

    @Composable
    private fun EmptyHintRow(settings: WidgetSettings) {
        Text(
            text = LocalContext.current.getString(R.string.widget_all_done),
            style = TextStyle(
                fontSize = (settings.fontSize.itemSp - 2).sp,
                color = GlanceTheme.colors.outline
            ),
            modifier = GlanceModifier.padding(start = 36.dp, top = 0.dp, bottom = 0.dp)
        )
    }
}

sealed class WidgetRow {
    abstract val stableId: Long

    data class Header(val entityId: String, val name: String, val iconType: String?, val iconValue: String?, val supportedFeatures: Int?, val isFirstHeader: Boolean) : WidgetRow() {
        override val stableId: Long get() = entityId.hashCode().toLong()
    }

    data class Item(val entityId: String, val item: TodoItem, val listName: String = "", val supportedFeatures: Int? = null) : WidgetRow() {
        override val stableId: Long get() = item.uid.hashCode().toLong()
    }

    data class EmptyHint(val entityId: String) : WidgetRow() {
        override val stableId: Long get() = (entityId + "_empty").hashCode().toLong()
    }
}

object TodoWidgetKeys {
    val ALL_LISTS_KEY = stringPreferencesKey("widget_all_lists")
    val PENDING_TOGGLE_IDS_KEY = stringPreferencesKey("widget_pending_toggle_ids")
    val SETTINGS_JSON_KEY = stringPreferencesKey("widget_settings_json")
    val APP_WIDGET_ID_KEY = stringPreferencesKey("widget_app_widget_id")
}

fun pendingToggleKey(entityId: String, itemUid: String): String = "$entityId|$itemUid"

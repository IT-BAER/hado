package com.baer.hado.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.baer.hado.data.model.TodoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WidgetStateMutator {

    private val gson = Gson()
    private val listType = object : TypeToken<List<WidgetListData>>() {}.type

    suspend fun prependItem(
        context: Context,
        entityId: String,
        item: TodoItem,
        preferredAppWidgetId: Int? = null
    ): Set<Int> {
        return mutateMatchingWidgets(context, entityId, preferredAppWidgetId) { list ->
            list.copy(items = listOf(item) + list.items.filterNot { it.uid == item.uid })
        }
    }

    private suspend fun mutateMatchingWidgets(
        context: Context,
        entityId: String,
        preferredAppWidgetId: Int?,
        transform: (WidgetListData) -> WidgetListData
    ): Set<Int> {
        val widgetManager = GlanceAppWidgetManager(context)
        val glanceIds = widgetManager.getGlanceIds(TodoWidget::class.java)
        val appWidgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodoWidgetReceiver::class.java))
        val glanceToWidgetId = glanceIds.zip(appWidgetIds.toList())
        val orderedTargets = if (preferredAppWidgetId != null) {
            val preferred = glanceToWidgetId.firstOrNull { it.second == preferredAppWidgetId }
            listOfNotNull(preferred) + glanceToWidgetId.filter { it.second != preferredAppWidgetId }
        } else {
            glanceToWidgetId
        }

        val affectedWidgetIds = linkedSetOf<Int>()
        for ((glanceId, appWidgetId) in orderedTargets) {
            var updated = false
            updateAppWidgetState(context, glanceId) { prefs ->
                val listsJson = prefs[TodoWidgetKeys.ALL_LISTS_KEY] ?: return@updateAppWidgetState
                val lists = parseLists(listsJson)
                if (lists.none { it.entityId == entityId }) {
                    return@updateAppWidgetState
                }

                val updatedLists = lists.map { list ->
                    if (list.entityId == entityId) transform(list) else list
                }
                if (updatedLists == lists) {
                    return@updateAppWidgetState
                }

                prefs[TodoWidgetKeys.ALL_LISTS_KEY] = gson.toJson(updatedLists)
                updated = true
            }

            if (updated) {
                TodoWidget().update(context, glanceId)
                affectedWidgetIds += appWidgetId
            }
        }

        return affectedWidgetIds
    }

    private fun parseLists(listsJson: String): List<WidgetListData> {
        return try {
            gson.fromJson<List<WidgetListData>>(listsJson, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
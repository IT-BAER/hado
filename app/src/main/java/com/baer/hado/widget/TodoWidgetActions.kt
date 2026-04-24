package com.baer.hado.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.TodoItemStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class OpenSettingsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val appWidgetId = parameters[PARAM_WIDGET_ID]?.toIntOrNull()
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val intent = Intent(context, WidgetSettingsActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        val PARAM_WIDGET_ID = ActionParameters.Key<String>("settings_widget_id")
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class AddItemAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("HAdo", "AddItemAction triggered, params: $parameters")
        val entityId = parameters[PARAM_ENTITY_ID]
        val listName = parameters[PARAM_LIST_NAME] ?: ""
        val supportedFeatures = parameters[PARAM_SUPPORTED_FEATURES]?.toIntOrNull() ?: 0
        val autoFocus = parameters[PARAM_AUTO_FOCUS]?.toBooleanStrictOrNull() ?: false
        val appWidgetId = parameters[PARAM_WIDGET_ID]?.toIntOrNull()
            ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
        Log.d("HAdo", "AddItemAction entityId=$entityId listName=$listName features=$supportedFeatures autoFocus=$autoFocus")

        if (entityId == null) {
            Log.e("HAdo", "AddItemAction: entityId is null, aborting")
            return
        }

        val intent = Intent(context, AddItemActivity::class.java).apply {
            putExtra("entity_id", entityId)
            putExtra("list_name", listName)
            putExtra("supported_features", supportedFeatures)
            putExtra("auto_focus_input", autoFocus)
            putExtra("app_widget_id", appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        // Brief delay so the list selector press state is visible before activity covers widget
        kotlinx.coroutines.delay(80)
        context.startActivity(intent)
    }

    companion object {
        val PARAM_ENTITY_ID = ActionParameters.Key<String>("add_entity_id")
        val PARAM_LIST_NAME = ActionParameters.Key<String>("add_list_name")
        val PARAM_SUPPORTED_FEATURES = ActionParameters.Key<String>("add_supported_features")
        val PARAM_AUTO_FOCUS = ActionParameters.Key<String>("add_auto_focus")
        val PARAM_WIDGET_ID = ActionParameters.Key<String>("add_widget_id")
    }
}

class ToggleItemAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val entityId = parameters[PARAM_ENTITY_ID] ?: return
        val itemUid = parameters[PARAM_ITEM_UID] ?: return
        val wasCompleted = parameters[PARAM_COMPLETED]?.toBooleanStrictOrNull() ?: return
        val actionStartedAt = System.currentTimeMillis()

        val newStatus = if (wasCompleted) "needs_action" else "completed"
        val tokenManager = TokenManager(context)
        val newItemStatus = if (wasCompleted) TodoItemStatus.NEEDS_ACTION else TodoItemStatus.COMPLETED
        val previousItemStatus = if (wasCompleted) TodoItemStatus.COMPLETED else TodoItemStatus.NEEDS_ACTION

        // Apply state immediately and lock the item while HA sync is in flight.
        updateItemUiInWidgets(context, entityId, itemUid, status = newItemStatus, pending = true)

        withContext(Dispatchers.IO) {
            try {
                if (tokenManager.isDemoMode) {
                    LocalTodoStore(context).updateItemStatus(entityId, itemUid, newStatus == "completed")
                } else {
                    val payload = Gson().toJson(mapOf(
                        "entity_id" to entityId,
                        "item" to itemUid,
                        "status" to newStatus
                    ))
                    val httpClient = WidgetHttpClient(context)
                    val response = httpClient.post("api/services/todo/update_item", payload)
                    val wasSuccessful = response?.use { it.isSuccessful } ?: false
                    if (!wasSuccessful) {
                        throw IllegalStateException("Widget toggle sync failed")
                    }
                }

                waitForMinimumToggleLock(actionStartedAt)
                updateItemUiInWidgets(context, entityId, itemUid, pending = false)
                TodoWidgetWorker.enqueueOneTime(context)
            } catch (e: Exception) {
                Log.w("HAdo", "Widget toggle sync failed, reverting optimistic state", e)
                waitForMinimumToggleLock(actionStartedAt)
                updateItemUiInWidgets(context, entityId, itemUid, status = previousItemStatus, pending = false)
            }
        }
    }

    private suspend fun waitForMinimumToggleLock(startedAt: Long) {
        val elapsed = System.currentTimeMillis() - startedAt
        val remaining = MIN_TOGGLE_LOCK_MS - elapsed
        if (remaining > 0) {
            delay(remaining)
        }
    }

    private suspend fun updateItemUiInWidgets(
        context: Context,
        entityId: String,
        itemUid: String,
        status: TodoItemStatus? = null,
        pending: Boolean? = null
    ) {
        val gson = Gson()
        val listType = object : TypeToken<List<WidgetListData>>() {}.type
        val pendingType = object : TypeToken<List<String>>() {}.type
        val widgetManager = GlanceAppWidgetManager(context)
        val pendingKey = pendingToggleKey(entityId, itemUid)

        widgetManager.getGlanceIds(TodoWidget::class.java).forEach { widgetId ->
            updateAppWidgetState(context, widgetId) { prefs ->
                val listsJson = prefs[TodoWidgetKeys.ALL_LISTS_KEY] ?: "[]"
                if (status != null) {
                    val lists: List<WidgetListData> = gson.fromJson(listsJson, listType)
                    val updated = lists.map { list ->
                        if (list.entityId == entityId) {
                            list.copy(items = list.items.map { item -> item.withStatus(itemUid, status) })
                        } else list
                    }
                    prefs[TodoWidgetKeys.ALL_LISTS_KEY] = gson.toJson(updated)
                }

                if (pending != null) {
                    val pendingJson = prefs[TodoWidgetKeys.PENDING_TOGGLE_IDS_KEY] ?: "[]"
                    val pendingIds = gson.fromJson<List<String>>(pendingJson, pendingType).toMutableSet()
                    if (pending) pendingIds += pendingKey else pendingIds -= pendingKey
                    prefs[TodoWidgetKeys.PENDING_TOGGLE_IDS_KEY] = gson.toJson(pendingIds.toList())
                }
            }

            TodoWidget().update(context, widgetId)
        }
    }

    companion object {
        private const val MIN_TOGGLE_LOCK_MS = 750L
        val PARAM_ENTITY_ID = ActionParameters.Key<String>("entity_id")
        val PARAM_ITEM_UID = ActionParameters.Key<String>("item_uid")
        val PARAM_COMPLETED = ActionParameters.Key<String>("completed")
    }
}

private fun TodoItem.withStatus(itemUid: String, status: TodoItemStatus): TodoItem {
    return if (uid == itemUid) copy(status = status) else this
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        TodoWidgetWorker.enqueueOneTime(context)
    }
}

class ViewItemAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val entityId = parameters[PARAM_ENTITY_ID] ?: return
        val listName = parameters[PARAM_LIST_NAME] ?: ""
        val supportedFeatures = parameters[PARAM_SUPPORTED_FEATURES]?.toIntOrNull() ?: 0
        val itemUid = parameters[PARAM_ITEM_UID] ?: return
        val appWidgetId = parameters[PARAM_WIDGET_ID]?.toIntOrNull()
            ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

        val intent = Intent(context, AddItemActivity::class.java).apply {
            putExtra("entity_id", entityId)
            putExtra("list_name", listName)
            putExtra("supported_features", supportedFeatures)
            putExtra("detail_item_uid", itemUid)
            putExtra("app_widget_id", appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    companion object {
        val PARAM_ENTITY_ID = ActionParameters.Key<String>("view_entity_id")
        val PARAM_LIST_NAME = ActionParameters.Key<String>("view_list_name")
        val PARAM_SUPPORTED_FEATURES = ActionParameters.Key<String>("view_supported_features")
        val PARAM_ITEM_UID = ActionParameters.Key<String>("view_item_uid")
        val PARAM_WIDGET_ID = ActionParameters.Key<String>("view_widget_id")
    }
}

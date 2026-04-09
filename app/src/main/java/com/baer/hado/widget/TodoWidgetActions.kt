package com.baer.hado.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.baer.hado.data.model.TodoItemStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
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
        Log.d("HAdo", "AddItemAction entityId=$entityId listName=$listName features=$supportedFeatures")

        if (entityId == null) {
            Log.e("HAdo", "AddItemAction: entityId is null, aborting")
            return
        }

        val intent = Intent(context, AddItemActivity::class.java).apply {
            putExtra("entity_id", entityId)
            putExtra("list_name", listName)
            putExtra("supported_features", supportedFeatures)
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

        val newStatus = if (wasCompleted) "needs_action" else "completed"
        val payload = Gson().toJson(mapOf(
            "entity_id" to entityId,
            "item" to itemUid,
            "status" to newStatus
        ))

        withContext(Dispatchers.IO) {
            try {
                val httpClient = WidgetHttpClient(context)
                httpClient.post("api/services/todo/update_item", payload)?.close()

                // Optimistic UI update
                updateAppWidgetState(context, glanceId) { prefs ->
                    val listsJson = prefs[TodoWidgetKeys.ALL_LISTS_KEY] ?: "[]"
                    val type = object : TypeToken<List<WidgetListData>>() {}.type
                    val lists: List<WidgetListData> = Gson().fromJson(listsJson, type)
                    val updated = lists.map { list ->
                        if (list.entityId == entityId) {
                            list.copy(items = list.items.map { item ->
                                if (item.uid == itemUid) item.copy(
                                    status = if (wasCompleted) TodoItemStatus.NEEDS_ACTION
                                    else TodoItemStatus.COMPLETED
                                ) else item
                            })
                        } else list
                    }
                    prefs[TodoWidgetKeys.ALL_LISTS_KEY] = Gson().toJson(updated)
                }

                TodoWidget().update(context, glanceId)
            } catch (_: Exception) {
                // Silently fail; next refresh will sync state
            }
        }
    }

    companion object {
        val PARAM_ENTITY_ID = ActionParameters.Key<String>("entity_id")
        val PARAM_ITEM_UID = ActionParameters.Key<String>("item_uid")
        val PARAM_COMPLETED = ActionParameters.Key<String>("completed")
    }
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

        val intent = Intent(context, AddItemActivity::class.java).apply {
            putExtra("entity_id", entityId)
            putExtra("list_name", listName)
            putExtra("supported_features", supportedFeatures)
            putExtra("detail_item_uid", itemUid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    companion object {
        val PARAM_ENTITY_ID = ActionParameters.Key<String>("view_entity_id")
        val PARAM_LIST_NAME = ActionParameters.Key<String>("view_list_name")
        val PARAM_SUPPORTED_FEATURES = ActionParameters.Key<String>("view_supported_features")
        val PARAM_ITEM_UID = ActionParameters.Key<String>("view_item_uid")
    }
}

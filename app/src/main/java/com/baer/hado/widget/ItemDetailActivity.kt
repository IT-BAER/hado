package com.baer.hado.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.model.TodoListFeature
import com.baer.hado.ui.theme.HadoTheme
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Translucent activity that floats only the item detail dialog over the home screen
 * (launched from a widget item tap), instead of opening the full list editor.
 */
class ItemDetailActivity : ComponentActivity() {

    // Outlives the activity so the HA/local write completes after finish().
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the window extend under the system bars so the launcher shows through
        // the status/navigation regions instead of opaque black bands.
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val entityId = intent.getStringExtra("entity_id")
        val itemJson = intent.getStringExtra("item_json")
        val supportedFeatures = intent.getIntExtra("supported_features", 0)
        val targetAppWidgetId = intent.getIntExtra("app_widget_id", AppWidgetManager.INVALID_APPWIDGET_ID)
            .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

        val original = itemJson?.let {
            try {
                Gson().fromJson(it, TodoItem::class.java)
            } catch (_: Exception) {
                null
            }
        }

        if (entityId.isNullOrBlank() || original == null) {
            finish()
            return
        }

        val isLocalMode = TokenManager(this).isDemoMode
        val supportsDescription = TodoListFeature.hasFeature(supportedFeatures, TodoListFeature.SET_DESCRIPTION_ON_ITEM)
        val supportsDueDate = TodoListFeature.hasFeature(supportedFeatures, TodoListFeature.SET_DUE_DATE_ON_ITEM)
        val supportsDueDatetime = TodoListFeature.hasFeature(supportedFeatures, TodoListFeature.SET_DUE_DATETIME_ON_ITEM)

        setContent {
            HadoTheme {
                ItemDetailDialog(
                    context = this,
                    item = original,
                    supportsDescription = supportsDescription,
                    supportsDueDate = supportsDueDate,
                    supportsDueDatetime = supportsDueDatetime,
                    onDismiss = { finish() },
                    onSave = { updated ->
                        persist(entityId, original, updated, isLocalMode, targetAppWidgetId)
                        finish()
                    }
                )
            }
        }
    }

    private fun persist(
        entityId: String,
        original: TodoItem,
        updated: TodoItem,
        isLocalMode: Boolean,
        appWidgetId: Int?
    ) {
        val appContext = applicationContext
        saveScope.launch {
            // Optimistic: reflect the edit in any widget showing this list right away.
            WidgetStateMutator.updateItem(appContext, entityId, updated, appWidgetId)

            if (isLocalMode) {
                LocalTodoStore(appContext).updateItemDetails(
                    entityId,
                    original.uid,
                    rename = updated.summary,
                    description = updated.description,
                    due = updated.due
                )
            } else {
                val body = mutableMapOf("entity_id" to entityId, "item" to original.uid)
                if (updated.summary != original.summary) {
                    body["rename"] = updated.summary
                }
                if (updated.description != original.description) {
                    body["description"] = updated.description ?: ""
                }
                val newDue = updated.due
                if (newDue != original.due) {
                    if (newDue == null) {
                        body["due_date"] = ""
                    } else if (newDue.contains("T") || (newDue.contains(" ") && newDue.length > 10)) {
                        body["due_datetime"] = newDue.replace("T", " ")
                    } else {
                        body["due_date"] = newDue
                    }
                }
                if (body.size > 2) {
                    val payload = Gson().toJson(body)
                    WidgetHttpClient(appContext).post("api/services/todo/update_item", payload)?.close()
                }
            }

            if (appWidgetId != null) {
                TodoWidgetWorker.enqueueOneTime(appContext, appWidgetId)
            } else {
                TodoWidgetWorker.enqueueOneTime(appContext)
            }
        }
    }
}

package com.baer.hado.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.SimpleState
import com.baer.hado.data.model.TodoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TodoWidgetWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager
) : CoroutineWorker(context, workerParams) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        if (!tokenManager.isLoggedIn) return Result.failure()
        val targetAppWidgetId = inputData
            .getInt(KEY_INPUT_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            .takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

        val allLists = if (tokenManager.isDemoMode) {
            fetchLocalModeLists()
        } else {
            fetchHaLists() ?: return Result.retry()
        }

        // Build GlanceId → appWidgetId mapping
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(TodoWidget::class.java)
        val appWidgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodoWidgetReceiver::class.java))
        val glanceToWidgetId = glanceIds.zip(appWidgetIds.toList()).toMap()
        val targetWidgets = if (targetAppWidgetId != null) {
            glanceToWidgetId.filterValues { it == targetAppWidgetId }
        } else {
            glanceToWidgetId
        }

        // Update each widget instance with per-widget filtered data
        for ((glanceId, appWidgetId) in targetWidgets) {
            val settings = WidgetSettingsManager.load(context, appWidgetId)

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[TodoWidgetKeys.ALL_LISTS_KEY] = gson.toJson(allLists)
                prefs[TodoWidgetKeys.SETTINGS_JSON_KEY] = gson.toJson(settings)
                prefs[TodoWidgetKeys.APP_WIDGET_ID_KEY] = appWidgetId.toString()
            }
            TodoWidget().update(context, glanceId)
        }

        return Result.success()
    }

    private fun fetchLocalModeLists(): List<WidgetListData> {
        val localStore = LocalTodoStore(context)
        return localStore.getLists().map { list ->
            val items = localStore.getItems(list.entityId)
            val resolved = ListIconManager.resolveIcon(context, list.entityId)
            WidgetListData(
                entityId = list.entityId,
                name = list.attributes.friendlyName ?: list.entityId,
                items = items,
                iconType = resolved?.type?.name?.lowercase(),
                iconValue = resolved?.value,
                supportedFeatures = list.attributes.supportedFeatures
            )
        }
    }

    private fun fetchHaLists(): List<WidgetListData>? {
        val httpClient = WidgetHttpClient(context)

        val statesResponse = httpClient.get("api/states") ?: return null
        val statesJson = statesResponse.use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string() ?: return null
        }

        val statesType = object : TypeToken<List<SimpleState>>() {}.type
        val states: List<SimpleState> = gson.fromJson(statesJson, statesType)
        val todoEntities = states.filter { it.entity_id.startsWith("todo.") }

        val allLists = mutableListOf<WidgetListData>()
        for (entity in todoEntities) {
            val friendlyName = entity.attributes?.get("friendly_name") as? String
                ?: entity.entity_id
            val haIcon = entity.attributes?.get("icon") as? String
            val supportedFeatures = (entity.attributes?.get("supported_features") as? Number)?.toInt()

            val payload = gson.toJson(mapOf("entity_id" to entity.entity_id))
            val items = try {
                val response = httpClient.post(
                    "api/services/todo/get_items?return_response", payload
                )
                response?.use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val body = resp.body?.string() ?: return@use emptyList()
                    parseItems(body, entity.entity_id)
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            val resolved = ListIconManager.resolveIcon(context, entity.entity_id, haIcon)
            allLists.add(
                WidgetListData(
                    entityId = entity.entity_id,
                    name = friendlyName,
                    items = items,
                    iconType = resolved?.type?.name?.lowercase(),
                    iconValue = resolved?.value,
                    supportedFeatures = supportedFeatures
                )
            )
        }
        return allLists
    }

    private fun parseItems(json: String, entityId: String): List<TodoItem> {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val root: Map<String, Any> = gson.fromJson(json, type)
            val serviceResponse = root["service_response"] as? Map<*, *> ?: root
            val entityData = serviceResponse[entityId] as? Map<*, *> ?: return emptyList()
            val itemsList = entityData["items"] ?: return emptyList()
            val itemsJson = gson.toJson(itemsList)
            val itemsType = object : TypeToken<List<TodoItem>>() {}.type
            gson.fromJson(itemsJson, itemsType)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val LEGACY_WORK_NAME_PERIODIC = "todo_widget_sync"
        private const val WORK_NAME_PERIODIC_PREFIX = "todo_widget_sync_"
        private const val WORK_NAME_ONETIME = "todo_widget_refresh"
        private const val WORK_NAME_ONETIME_PREFIX = "todo_widget_refresh_"
        private const val KEY_INPUT_APP_WIDGET_ID = "input_app_widget_id"

        fun enqueuePeriodic(context: Context, appWidgetId: Int) {
            cancelLegacyPeriodic(context)
            val interval = WidgetSettingsManager.load(context, appWidgetId).refreshInterval
            val request = PeriodicWorkRequestBuilder<TodoWidgetWorker>(
                interval.minutes, TimeUnit.MINUTES
            ).setInputData(
                workDataOf(KEY_INPUT_APP_WIDGET_ID to appWidgetId)
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                periodicWorkName(appWidgetId),
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueuePeriodic(context: Context, appWidgetIds: IntArray) {
            cancelLegacyPeriodic(context)
            appWidgetIds.forEach { enqueuePeriodic(context, it) }
        }

        fun cancelPeriodic(context: Context, appWidgetId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(periodicWorkName(appWidgetId))
        }

        fun enqueueOneTime(context: Context, appWidgetId: Int? = null) {
            val builder = OneTimeWorkRequestBuilder<TodoWidgetWorker>()
            if (appWidgetId != null) {
                builder.setInputData(workDataOf(KEY_INPUT_APP_WIDGET_ID to appWidgetId))
            }
            val request = builder.build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                oneTimeWorkName(appWidgetId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        suspend fun applySettingsImmediately(context: Context, appWidgetId: Int, settings: WidgetSettings) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodoWidget::class.java)
            val appWidgetIds = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, TodoWidgetReceiver::class.java))
            val glanceId = glanceIds.zip(appWidgetIds.toList())
                .firstOrNull { it.second == appWidgetId }
                ?.first
                ?: return

            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[TodoWidgetKeys.SETTINGS_JSON_KEY] = Gson().toJson(settings)
                prefs[TodoWidgetKeys.APP_WIDGET_ID_KEY] = appWidgetId.toString()
            }
            TodoWidget().update(context, glanceId)
        }

        private fun periodicWorkName(appWidgetId: Int): String = "$WORK_NAME_PERIODIC_PREFIX$appWidgetId"

        private fun oneTimeWorkName(appWidgetId: Int?): String {
            return if (appWidgetId != null) "$WORK_NAME_ONETIME_PREFIX$appWidgetId" else WORK_NAME_ONETIME
        }

        private fun cancelLegacyPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME_PERIODIC)
        }
    }
}

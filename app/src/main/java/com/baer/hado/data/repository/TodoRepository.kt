package com.baer.hado.data.repository

import android.content.Context
import com.baer.hado.data.api.HaApiService
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.HaState
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.model.TodoItemStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val apiService: HaApiService,
    private val gson: Gson,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) {

    private val localStore by lazy { LocalTodoStore(context) }

    suspend fun getTodoLists(): Result<List<HaState>> {
        if (tokenManager.isDemoMode) {
            return Result.success(localStore.getLists())
        }
        return try {
            val states = apiService.getStates()
            val todoEntities = states.filter { it.entityId.startsWith("todo.") }
            Result.success(todoEntities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTodoItems(entityId: String): Result<List<TodoItem>> {
        if (tokenManager.isDemoMode) {
            return Result.success(localStore.getItems(entityId))
        }
        return try {
            val body = mapOf(
                "entity_id" to entityId
            )
            val response = executeTodoItemsRequest(body)

            if (!response.isSuccessful) {
                return Result.failure(Exception("API error: ${response.code()}"))
            }

            val responseBody = response.body()?.string()
                ?: return Result.success(emptyList())

            val parsed = parseItemsResponse(responseBody, entityId)
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeTodoItemsRequest(body: Map<String, String>): Response<ResponseBody> {
        val response = apiService.getTodoItems(body)
        if (response.code() != 429) {
            return response
        }

        val retryAfterSeconds = response.headers()["Retry-After"]?.trim()?.toLongOrNull()
        if (retryAfterSeconds == null || retryAfterSeconds <= 0 || retryAfterSeconds > 5) {
            return response
        }

        response.body()?.close()
        response.errorBody()?.close()
        delay(retryAfterSeconds * 1000)
        return apiService.getTodoItems(body)
    }

    private fun parseItemsResponse(json: String, entityId: String): List<TodoItem> {
        return try {
            // Response format: { "service_response": { "entity_id": { "items": [...] } } }
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

    fun createList(name: String): HaState {
        return localStore.createList(name)
    }

    fun deleteList(entityId: String) {
        localStore.deleteList(entityId)
    }

    suspend fun addItem(
        entityId: String,
        summary: String,
        description: String? = null,
        dueDate: String? = null,
        dueDatetime: String? = null
    ): Result<Unit> {
        if (tokenManager.isDemoMode) {
            localStore.addItem(entityId, summary, description, dueDatetime ?: dueDate)
            return Result.success(Unit)
        }
        return try {
            val body = mutableMapOf(
                "entity_id" to entityId,
                "item" to summary
            )
            description?.let { body["description"] = it }
            dueDate?.let { body["due_date"] = it }
            dueDatetime?.let { body["due_datetime"] = it }
            val response = apiService.addTodoItem(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to add item: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateItemStatus(
        entityId: String,
        itemUid: String,
        completed: Boolean
    ): Result<Unit> {
        if (tokenManager.isDemoMode) {
            localStore.updateItemStatus(entityId, itemUid, completed)
            return Result.success(Unit)
        }
        return try {
            val status = if (completed) "completed" else "needs_action"
            val body = mapOf(
                "entity_id" to entityId,
                "item" to itemUid,
                "status" to status
            )
            val response = apiService.updateTodoItem(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to update item: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameItem(
        entityId: String,
        itemUid: String,
        newName: String
    ): Result<Unit> {
        if (tokenManager.isDemoMode) {
            localStore.updateItemDetails(entityId, itemUid, rename = newName)
            return Result.success(Unit)
        }
        return try {
            val body = mapOf(
                "entity_id" to entityId,
                "item" to itemUid,
                "rename" to newName
            )
            val response = apiService.updateTodoItem(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to rename item: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateItemDetails(
        entityId: String,
        itemUid: String,
        rename: String? = null,
        status: String? = null,
        description: String? = null,
        dueDate: String? = null,
        dueDatetime: String? = null
    ): Result<Unit> {
        if (tokenManager.isDemoMode) {
            localStore.updateItemDetails(
                entityId, itemUid,
                rename = rename,
                status = status,
                description = description,
                due = dueDatetime ?: dueDate
            )
            return Result.success(Unit)
        }
        return try {
            val body = mutableMapOf(
                "entity_id" to entityId,
                "item" to itemUid
            )
            rename?.let { body["rename"] = it }
            status?.let { body["status"] = it }
            description?.let { body["description"] = it }
            dueDate?.let { body["due_date"] = it }
            dueDatetime?.let { body["due_datetime"] = it }
            val response = apiService.updateTodoItem(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to update item: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeItem(entityId: String, itemUid: String): Result<Unit> {
        if (tokenManager.isDemoMode) {
            localStore.removeItem(entityId, itemUid)
            return Result.success(Unit)
        }
        return try {
            val body = mapOf(
                "entity_id" to entityId,
                "item" to itemUid
            )
            val response = apiService.removeTodoItem(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to remove item: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

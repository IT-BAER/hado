package com.baer.hado.data.repository

import com.baer.hado.data.api.HaApiService
import com.baer.hado.data.model.HaState
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.model.TodoItemStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val apiService: HaApiService,
    private val gson: Gson
) {
    suspend fun getTodoLists(): Result<List<HaState>> {
        return try {
            val states = apiService.getStates()
            val todoEntities = states.filter { it.entityId.startsWith("todo.") }
            Result.success(todoEntities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTodoItems(entityId: String): Result<List<TodoItem>> {
        return try {
            val body = mapOf(
                "entity_id" to entityId
            )
            val response = apiService.getTodoItems(body)

            if (!response.isSuccessful) {
                return Result.failure(Exception("API error: ${response.code()}"))
            }

            val responseBody = response.body()?.string()
                ?: return Result.success(emptyList())

            val parsed = parseItemsResponse(responseBody, entityId)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    suspend fun addItem(
        entityId: String,
        summary: String,
        description: String? = null,
        dueDate: String? = null,
        dueDatetime: String? = null
    ): Result<Unit> {
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

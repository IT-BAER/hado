package com.baer.hado.data.local

import android.content.Context
import com.baer.hado.data.model.HaAttributes
import com.baer.hado.data.model.HaState
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.model.TodoItemStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class LocalTodoStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- Lists ---

    fun getLists(): List<HaState> {
        val json = prefs.getString(KEY_LISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<LocalList>>() {}.type
        val locals: List<LocalList> = gson.fromJson(json, type)
        return locals.map { it.toHaState() }
    }

    fun createList(name: String): HaState {
        val id = "local_todo.${UUID.randomUUID().toString().take(8)}"
        val lists = getLocalLists().toMutableList()
        val newList = LocalList(id, name, ALL_FEATURES)
        lists.add(newList)
        saveLists(lists)
        saveItems(id, emptyList())
        return newList.toHaState()
    }

    fun deleteList(entityId: String) {
        val lists = getLocalLists().toMutableList()
        lists.removeAll { it.entityId == entityId }
        saveLists(lists)
        prefs.edit().remove(itemsKey(entityId)).apply()
    }

    fun renameList(entityId: String, newName: String) {
        val lists = getLocalLists().toMutableList()
        val index = lists.indexOfFirst { it.entityId == entityId }
        if (index >= 0) {
            lists[index] = lists[index].copy(name = newName)
            saveLists(lists)
        }
    }

    // --- Items ---

    fun getItems(entityId: String): List<TodoItem> {
        val json = prefs.getString(itemsKey(entityId), null) ?: return emptyList()
        val type = object : TypeToken<List<TodoItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun addItem(
        entityId: String,
        summary: String,
        description: String? = null,
        due: String? = null
    ): TodoItem {
        val items = getItems(entityId).toMutableList()
        val item = TodoItem(
            uid = UUID.randomUUID().toString(),
            summary = summary,
            status = TodoItemStatus.NEEDS_ACTION,
            description = description,
            due = due
        )
        items.add(0, item)
        saveItems(entityId, items)
        updateListCount(entityId, items)
        return item
    }

    fun updateItemStatus(entityId: String, itemUid: String, completed: Boolean) {
        val items = getItems(entityId).toMutableList()
        val index = items.indexOfFirst { it.uid == itemUid }
        if (index >= 0) {
            val newStatus = if (completed) TodoItemStatus.COMPLETED else TodoItemStatus.NEEDS_ACTION
            items[index] = items[index].copy(status = newStatus)
            saveItems(entityId, items)
            updateListCount(entityId, items)
        }
    }

    fun updateItemDetails(
        entityId: String,
        itemUid: String,
        rename: String? = null,
        status: String? = null,
        description: String? = null,
        due: String? = null
    ) {
        val items = getItems(entityId).toMutableList()
        val index = items.indexOfFirst { it.uid == itemUid }
        if (index >= 0) {
            var item = items[index]
            rename?.let { item = item.copy(summary = it) }
            status?.let { s ->
                item = item.copy(status = if (s == "completed") TodoItemStatus.COMPLETED else TodoItemStatus.NEEDS_ACTION)
            }
            description?.let { item = item.copy(description = it) }
            due?.let { item = item.copy(due = it) }
            items[index] = item
            saveItems(entityId, items)
            updateListCount(entityId, items)
        }
    }

    fun removeItem(entityId: String, itemUid: String) {
        val items = getItems(entityId).toMutableList()
        items.removeAll { it.uid == itemUid }
        saveItems(entityId, items)
        updateListCount(entityId, items)
    }

    fun moveItem(entityId: String, itemUid: String, previousUid: String?) {
        val items = getItems(entityId).toMutableList()
        val item = items.firstOrNull { it.uid == itemUid } ?: return
        items.removeAll { it.uid == itemUid }
        if (previousUid == null) {
            items.add(0, item)
        } else {
            val prevIndex = items.indexOfFirst { it.uid == previousUid }
            if (prevIndex >= 0) items.add(prevIndex + 1, item)
            else items.add(item)
        }
        saveItems(entityId, items)
    }

    // --- Internal ---

    private data class LocalList(
        val entityId: String,
        val name: String,
        val supportedFeatures: Int
    ) {
        fun toHaState() = HaState(entityId, "0", HaAttributes(name, supportedFeatures))
    }

    private fun getLocalLists(): List<LocalList> {
        val json = prefs.getString(KEY_LISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<LocalList>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun saveLists(lists: List<LocalList>) {
        prefs.edit().putString(KEY_LISTS, gson.toJson(lists)).apply()
    }

    private fun saveItems(entityId: String, items: List<TodoItem>) {
        prefs.edit().putString(itemsKey(entityId), gson.toJson(items)).apply()
    }

    private fun updateListCount(entityId: String, items: List<TodoItem>) {
        val active = items.count { it.status == TodoItemStatus.NEEDS_ACTION }
        val lists = getLocalLists().toMutableList()
        val index = lists.indexOfFirst { it.entityId == entityId }
        if (index >= 0) {
            // State field stores the active item count (same as HA convention)
            // We don't store state in LocalList, so this is a no-op for now
        }
    }

    private fun itemsKey(entityId: String) = "${KEY_ITEMS_PREFIX}$entityId"

    companion object {
        private const val PREFS_NAME = "hado_local_todo"
        private const val KEY_LISTS = "lists"
        private const val KEY_ITEMS_PREFIX = "items_"
        // All features enabled for local lists
        const val ALL_FEATURES = 1 or 2 or 4 or 8 or 16 or 32 or 64 // 127
    }
}

package com.baer.hado.data.local

import android.content.Context

/**
 * Controls whether new items are added to the top (prepend) or bottom (append) of a list.
 */
enum class AddItemPosition {
    TOP,
    BOTTOM;
}

/**
 * Consolidated manager for app-level user preferences stored in SharedPreferences.
 */
object AppPreferencesManager {

    private const val PREFS_NAME = "hado_app_prefs"
    private const val KEY_ADD_ITEM_POSITION = "add_item_position"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Add Item Position ---

    fun loadAddItemPosition(context: Context): AddItemPosition {
        val value = prefs(context).getString(KEY_ADD_ITEM_POSITION, null)
        return try {
            if (value != null) AddItemPosition.valueOf(value) else AddItemPosition.TOP
        } catch (_: Exception) {
            AddItemPosition.TOP
        }
    }

    fun saveAddItemPosition(context: Context, position: AddItemPosition) {
        prefs(context).edit()
            .putString(KEY_ADD_ITEM_POSITION, position.name)
            .apply()
    }
}

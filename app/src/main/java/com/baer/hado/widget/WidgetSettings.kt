package com.baer.hado.widget

import android.content.Context

/**
 * Per-widget-instance settings stored in SharedPreferences.
 * Each widget instance gets its own settings keyed by appWidgetId.
 */
data class WidgetSettings(
    val selectedListIds: Set<String> = emptySet(), // empty = show all
    val showCompleted: Boolean = true,
    val fontSize: FontSize = FontSize.MEDIUM,
    val backgroundOpacity: Float = 1.0f,
    val compactMode: Boolean = false,
    val checkboxOnly: Boolean = false,  // true = only checkbox toggles, false = entire row toggles
    val showTitle: Boolean = true  // show HAdo icon + title bar
) {
    enum class FontSize(val label: String, val titleSp: Float, val itemSp: Float, val headerSp: Float) {
        SMALL("Small", 15f, 14f, 12f),
        MEDIUM("Medium", 18f, 16f, 13f),
        LARGE("Large", 20f, 18f, 15f)
    }

    enum class RefreshInterval(val label: String, val minutes: Long) {
        MIN_15("15 minutes", 15),
        MIN_30("30 minutes", 30),
        HOUR_1("1 hour", 60),
        HOUR_2("2 hours", 120),
        HOUR_4("4 hours", 240)
    }
}

object WidgetSettingsManager {

    private const val PREFS_NAME = "hado_widget_settings"
    private const val KEY_SELECTED_LISTS = "selected_lists_"
    private const val KEY_SHOW_COMPLETED = "show_completed_"
    private const val KEY_FONT_SIZE = "font_size_"
    private const val KEY_BG_OPACITY = "bg_opacity_"
    private const val KEY_COMPACT_MODE = "compact_mode_"
    private const val KEY_CHECKBOX_ONLY = "checkbox_only_"
    private const val KEY_SHOW_TITLE = "show_title_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context, appWidgetId: Int): WidgetSettings {
        val p = prefs(context)
        return WidgetSettings(
            selectedListIds = p.getStringSet("$KEY_SELECTED_LISTS$appWidgetId", emptySet()) ?: emptySet(),
            showCompleted = p.getBoolean("$KEY_SHOW_COMPLETED$appWidgetId", true),
            fontSize = try {
                WidgetSettings.FontSize.valueOf(
                    p.getString("$KEY_FONT_SIZE$appWidgetId", "MEDIUM") ?: "MEDIUM"
                )
            } catch (_: Exception) {
                WidgetSettings.FontSize.MEDIUM
            },
            backgroundOpacity = p.getFloat("$KEY_BG_OPACITY$appWidgetId", 1.0f),
            compactMode = p.getBoolean("$KEY_COMPACT_MODE$appWidgetId", false),
            checkboxOnly = p.getBoolean("$KEY_CHECKBOX_ONLY$appWidgetId", false),
            showTitle = p.getBoolean("$KEY_SHOW_TITLE$appWidgetId", true)
        )
    }

    fun save(context: Context, appWidgetId: Int, settings: WidgetSettings) {
        prefs(context).edit().apply {
            putStringSet("$KEY_SELECTED_LISTS$appWidgetId", settings.selectedListIds)
            putBoolean("$KEY_SHOW_COMPLETED$appWidgetId", settings.showCompleted)
            putString("$KEY_FONT_SIZE$appWidgetId", settings.fontSize.name)
            putFloat("$KEY_BG_OPACITY$appWidgetId", settings.backgroundOpacity)
            putBoolean("$KEY_COMPACT_MODE$appWidgetId", settings.compactMode)
            putBoolean("$KEY_CHECKBOX_ONLY$appWidgetId", settings.checkboxOnly)
            putBoolean("$KEY_SHOW_TITLE$appWidgetId", settings.showTitle)
            apply()
        }
    }

    fun delete(context: Context, appWidgetId: Int) {
        prefs(context).edit().apply {
            remove("$KEY_SELECTED_LISTS$appWidgetId")
            remove("$KEY_SHOW_COMPLETED$appWidgetId")
            remove("$KEY_FONT_SIZE$appWidgetId")
            remove("$KEY_BG_OPACITY$appWidgetId")
            remove("$KEY_COMPACT_MODE$appWidgetId")
            remove("$KEY_CHECKBOX_ONLY$appWidgetId")
            remove("$KEY_SHOW_TITLE$appWidgetId")
            apply()
        }
    }

    private const val KEY_REFRESH_INTERVAL = "refresh_interval"

    fun loadRefreshInterval(context: Context): WidgetSettings.RefreshInterval {
        val name = prefs(context).getString(KEY_REFRESH_INTERVAL, null)
        return try {
            if (name != null) WidgetSettings.RefreshInterval.valueOf(name)
            else WidgetSettings.RefreshInterval.MIN_30
        } catch (_: Exception) {
            WidgetSettings.RefreshInterval.MIN_30
        }
    }

    fun saveRefreshInterval(context: Context, interval: WidgetSettings.RefreshInterval) {
        prefs(context).edit().putString(KEY_REFRESH_INTERVAL, interval.name).apply()
    }
}

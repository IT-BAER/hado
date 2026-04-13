package com.baer.hado.widget

import android.content.Context
import androidx.annotation.StringRes
import com.baer.hado.R

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
    enum class FontSize(@StringRes val labelResId: Int, val titleSp: Float, val itemSp: Float, val headerSp: Float) {
        SMALL(R.string.font_small, 15f, 14f, 12f),
        MEDIUM(R.string.font_medium, 18f, 16f, 13f),
        LARGE(R.string.font_large, 20f, 18f, 15f)
    }

    enum class RefreshInterval(@StringRes val labelResId: Int, val minutes: Long) {
        MIN_15(R.string.refresh_15min, 15),
        MIN_30(R.string.refresh_30min, 30),
        HOUR_1(R.string.refresh_1hour, 60),
        HOUR_2(R.string.refresh_2hours, 120),
        HOUR_4(R.string.refresh_4hours, 240)
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

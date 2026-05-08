package com.baer.hado.notifications

import android.content.Context
import androidx.annotation.StringRes
import com.baer.hado.R

data class OverdueNotificationSettings(
    val enabled: Boolean = false,
    val timing: NotificationTiming = NotificationTiming.WHEN_OVERDUE,
    val cadence: ReminderCadence = ReminderCadence.ONCE,
    val selectedListIds: Set<String> = emptySet()
) {
    enum class NotificationTiming(@StringRes val labelResId: Int, val leadMinutes: Long) {
        WHEN_OVERDUE(R.string.notification_timing_when_overdue, 0L),
        MINUTES_15_BEFORE(R.string.notification_timing_15_min_before, 15L),
        HOUR_1_BEFORE(R.string.notification_timing_1_hour_before, 60L),
        DAY_1_BEFORE(R.string.notification_timing_1_day_before, 24L * 60L)
    }

    enum class ReminderCadence(@StringRes val labelResId: Int) {
        ONCE(R.string.notification_cadence_once),
        DAILY_UNTIL_DONE(R.string.notification_cadence_daily_until_done)
    }
}

private fun notificationTimingOrNull(name: String): OverdueNotificationSettings.NotificationTiming? {
    return try {
        OverdueNotificationSettings.NotificationTiming.valueOf(name)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun reminderCadenceOrNull(name: String): OverdueNotificationSettings.ReminderCadence? {
    return try {
        OverdueNotificationSettings.ReminderCadence.valueOf(name)
    } catch (_: IllegalArgumentException) {
        null
    }
}

object OverdueNotificationSettingsManager {
    private const val PREFS_NAME = "hado_overdue_notification_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TIMING = "timing"
    private const val KEY_CADENCE = "cadence"
    private const val KEY_SELECTED_LIST_IDS = "selected_list_ids"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): OverdueNotificationSettings {
        val preferences = prefs(context)
        return OverdueNotificationSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            timing = preferences.getString(KEY_TIMING, null)
                ?.let(::notificationTimingOrNull)
                ?: OverdueNotificationSettings.NotificationTiming.WHEN_OVERDUE,
            cadence = preferences.getString(KEY_CADENCE, null)
                ?.let(::reminderCadenceOrNull)
                ?: OverdueNotificationSettings.ReminderCadence.ONCE,
            selectedListIds = preferences.getStringSet(KEY_SELECTED_LIST_IDS, emptySet()) ?: emptySet()
        )
    }

    fun save(context: Context, settings: OverdueNotificationSettings) {
        prefs(context).edit().apply {
            putBoolean(KEY_ENABLED, settings.enabled)
            putString(KEY_TIMING, settings.timing.name)
            putString(KEY_CADENCE, settings.cadence.name)
            putStringSet(KEY_SELECTED_LIST_IDS, settings.selectedListIds)
            apply()
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
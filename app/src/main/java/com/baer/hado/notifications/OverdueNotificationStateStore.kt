package com.baer.hado.notifications

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object OverdueNotificationStateStore {
    private const val PREFS_NAME = "hado_overdue_notification_state"
    private const val KEY_SENT_EVENTS = "sent_events"
    private const val ONCE_PREFIX = "once|"
    private const val DAILY_PREFIX = "daily|"
    private const val SNOOZE_PREFIX = "snooze|"

    fun baseKey(
        listId: String,
        itemUid: String,
        dueValue: String,
        timing: OverdueNotificationSettings.NotificationTiming
    ): String {
        return "$listId|$itemUid|$dueValue|${timing.name}"
    }

    fun hasSentOnce(context: Context, baseKey: String): Boolean {
        return entries(context).contains(ONCE_PREFIX + baseKey)
    }

    fun hasSentDaily(context: Context, baseKey: String, date: LocalDate): Boolean {
        return entries(context).contains(dailyKey(baseKey, date))
    }

    fun markSentOnce(context: Context, baseKey: String) {
        update(context) { current ->
            current.apply { add(ONCE_PREFIX + baseKey) }
        }
    }

    fun markSentDaily(context: Context, baseKey: String, date: LocalDate) {
        update(context) { current ->
            current.apply { add(dailyKey(baseKey, date)) }
        }
    }

    fun clearDelivered(context: Context, baseKey: String, date: LocalDate) {
        update(context) { current ->
            current.apply {
                remove(ONCE_PREFIX + baseKey)
                remove(dailyKey(baseKey, date))
            }
        }
    }

    fun snoozeUntil(context: Context, baseKey: String, until: LocalDateTime) {
        prefs(context).edit()
            .putLong(snoozeKey(baseKey), until.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .apply()
    }

    fun snoozedUntil(context: Context, baseKey: String): LocalDateTime? {
        val epochMillis = prefs(context).getLong(snoozeKey(baseKey), -1L)
        if (epochMillis <= 0L) return null
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault()
        )
    }

    fun clearSnooze(context: Context, baseKey: String) {
        prefs(context).edit().remove(snoozeKey(baseKey)).apply()
    }

    fun prune(context: Context, activeBaseKeys: Set<String>) {
        update(context) { current ->
            current.filterTo(mutableSetOf()) { entry ->
                entryBaseKey(entry) in activeBaseKeys
            }
        }

        val editor = prefs(context).edit()
        prefs(context).all.keys
            .filter { it.startsWith(SNOOZE_PREFIX) }
            .forEach { key ->
                if (key.removePrefix(SNOOZE_PREFIX) !in activeBaseKeys) {
                    editor.remove(key)
                }
            }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun entries(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_SENT_EVENTS, emptySet()) ?: emptySet()
    }

    private fun update(context: Context, transform: (MutableSet<String>) -> MutableSet<String>) {
        val updated = transform(entries(context).toMutableSet())
        prefs(context).edit().putStringSet(KEY_SENT_EVENTS, updated).apply()
    }

    private fun dailyKey(baseKey: String, date: LocalDate): String = "$DAILY_PREFIX$baseKey|$date"

    private fun snoozeKey(baseKey: String): String = "$SNOOZE_PREFIX$baseKey"

    private fun entryBaseKey(entry: String): String {
        return when {
            entry.startsWith(ONCE_PREFIX) -> entry.removePrefix(ONCE_PREFIX)
            entry.startsWith(DAILY_PREFIX) -> entry.removePrefix(DAILY_PREFIX).substringBeforeLast("|")
            else -> entry
        }
    }
}
package com.baer.hado.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baer.hado.data.local.LocalTodoStore
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.TodoItem
import com.baer.hado.widget.TodoWidgetWorker
import com.baer.hado.widget.WidgetHttpClient
import com.baer.hado.widget.WidgetListData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime

@HiltWorker
class OverdueNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = OverdueNotificationSettingsManager.load(appContext)
        if (!settings.enabled || !tokenManager.isLoggedIn || !hasNotificationPermission(appContext)) {
            OverdueNotificationScheduler.cancelOneTime(appContext)
            return Result.success()
        }

        // Every signed-in server is checked; Local Mode has a single local "server" (null).
        val accountIds: List<String?> = if (tokenManager.isDemoMode) listOf(null) else tokenManager.accountIds
        var anyFetchFailed = false
        var anyFetchSucceeded = false
        val activeEntries = mutableListOf<NotificationEntry>()
        for (accountId in accountIds) {
            val lists = fetchLists(accountId)
            if (lists == null) {
                anyFetchFailed = true
                continue
            }
            anyFetchSucceeded = true
            val selectedListIds = OverdueNotificationSettingsManager.load(appContext, accountId).selectedListIds
            lists
                .filter { selectedListIds.isEmpty() || it.entityId in selectedListIds }
                .forEach { list ->
                    list.items
                        .filter { !it.isCompleted && !it.due.isNullOrBlank() }
                        .mapTo(activeEntries) { item ->
                            NotificationEntry(
                                accountId = accountId,
                                listId = list.entityId,
                                listName = list.name,
                                item = item
                            )
                        }
                }
        }
        if (!anyFetchSucceeded) return Result.retry()
        val now = LocalDateTime.now()

        // An unreachable server's items are unknown; pruning now would re-send its reminders later.
        if (!anyFetchFailed) {
            val activeBaseKeys = activeEntries.mapTo(mutableSetOf()) { entry -> baseKey(entry, settings) }
            OverdueNotificationStateStore.prune(appContext, activeBaseKeys)
        }

        val dueNow = activeEntries.filter { entry -> shouldNotifyNow(entry, settings, now) }
        if (dueNow.isNotEmpty()) {
            OverdueNotifier.notify(
                context = appContext,
                items = dueNow.map { entry ->
                    OverdueNotificationItem(
                        notificationId = notificationIdFor(entry, settings),
                        accountId = entry.accountId,
                        listId = entry.listId,
                        listName = entry.listName,
                        itemUid = entry.item.uid,
                        itemTitle = entry.item.summary,
                        dueValue = entry.item.due.orEmpty(),
                        timing = settings.timing
                    )
                }
            )

            dueNow.forEach { entry -> markDelivered(entry, settings, now.toLocalDate()) }
        }

        val nextRunAt = activeEntries.mapNotNull { nextTriggerAt(it, settings, now) }.minOrNull()
        OverdueNotificationScheduler.scheduleNext(appContext, nextRunAt)
        return Result.success()
    }

    /** All lists with items for one server, or null when that server could not be read completely. */
    private fun fetchLists(accountId: String?): List<WidgetListData>? {
        if (accountId == null) {
            val localStore = LocalTodoStore(appContext)
            return localStore.getLists().map { list ->
                WidgetListData(
                    entityId = list.entityId,
                    name = list.attributes.friendlyName ?: list.entityId,
                    items = localStore.getItems(list.entityId)
                )
            }
        }
        return TodoWidgetWorker.fetchHaLists(appContext, WidgetHttpClient(appContext, accountId), strict = true)
    }

    private fun shouldNotifyNow(
        entry: NotificationEntry,
        settings: OverdueNotificationSettings,
        now: LocalDateTime
    ): Boolean {
        val target = targetAt(entry.item, settings.timing) ?: return false
        if (now.isBefore(target)) return false

        val baseKey = baseKey(entry, settings)
        val snoozedUntil = OverdueNotificationStateStore.snoozedUntil(appContext, baseKey)
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) return false

        return when (settings.cadence) {
            OverdueNotificationSettings.ReminderCadence.ONCE -> {
                !OverdueNotificationStateStore.hasSentOnce(appContext, baseKey)
            }

            OverdueNotificationSettings.ReminderCadence.DAILY_UNTIL_DONE -> {
                !OverdueNotificationStateStore.hasSentDaily(appContext, baseKey, now.toLocalDate())
            }
        }
    }

    private fun nextTriggerAt(
        entry: NotificationEntry,
        settings: OverdueNotificationSettings,
        now: LocalDateTime
    ): LocalDateTime? {
        val target = targetAt(entry.item, settings.timing) ?: return null
        if (now.isBefore(target)) {
            return target
        }

        val baseKey = baseKey(entry, settings)
        val snoozedUntil = OverdueNotificationStateStore.snoozedUntil(appContext, baseKey)
        if (snoozedUntil != null && now.isBefore(snoozedUntil)) {
            return snoozedUntil
        }

        return when (settings.cadence) {
            OverdueNotificationSettings.ReminderCadence.ONCE -> {
                if (OverdueNotificationStateStore.hasSentOnce(appContext, baseKey)) null else target
            }

            OverdueNotificationSettings.ReminderCadence.DAILY_UNTIL_DONE -> {
                if (OverdueNotificationStateStore.hasSentDaily(appContext, baseKey, now.toLocalDate())) {
                    nextDailyReminder(now)
                } else {
                    target
                }
            }
        }
    }

    private fun markDelivered(
        entry: NotificationEntry,
        settings: OverdueNotificationSettings,
        today: LocalDate
    ) {
        val baseKey = baseKey(entry, settings)
        OverdueNotificationStateStore.clearSnooze(appContext, baseKey)
        when (settings.cadence) {
            OverdueNotificationSettings.ReminderCadence.ONCE -> {
                OverdueNotificationStateStore.markSentOnce(appContext, baseKey)
            }

            OverdueNotificationSettings.ReminderCadence.DAILY_UNTIL_DONE -> {
                OverdueNotificationStateStore.markSentDaily(appContext, baseKey, today)
            }
        }
    }

    private fun baseKey(
        entry: NotificationEntry,
        settings: OverdueNotificationSettings
    ): String {
        return OverdueNotificationStateStore.baseKey(
            listId = TokenManager.scopedKey(entry.accountId, entry.listId),
            itemUid = entry.item.uid,
            dueValue = entry.item.due.orEmpty(),
            timing = settings.timing
        )
    }

    private fun notificationIdFor(
        entry: NotificationEntry,
        settings: OverdueNotificationSettings
    ): Int {
        return baseKey(entry, settings).hashCode()
    }

    private fun targetAt(
        item: TodoItem,
        timing: OverdueNotificationSettings.NotificationTiming
    ): LocalDateTime? {
        item.dueDateTime?.let { dueDateTime ->
            return when (timing) {
                OverdueNotificationSettings.NotificationTiming.WHEN_OVERDUE -> dueDateTime.plusMinutes(1)
                else -> dueDateTime.minusMinutes(timing.leadMinutes)
            }
        }

        item.dueDate?.let { dueDate ->
            val baseReminderTime = dueDate.atTime(9, 0)
            return when (timing) {
                OverdueNotificationSettings.NotificationTiming.WHEN_OVERDUE -> dueDate.plusDays(1).atTime(9, 0)
                else -> baseReminderTime.minusMinutes(timing.leadMinutes)
            }
        }

        return null
    }

    private fun nextDailyReminder(now: LocalDateTime): LocalDateTime {
        return now.toLocalDate().plusDays(1).atTime(9, 0)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class NotificationEntry(
        val accountId: String?,
        val listId: String,
        val listName: String,
        val item: TodoItem
    )
}
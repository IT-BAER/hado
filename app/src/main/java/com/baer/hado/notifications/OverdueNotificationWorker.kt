package com.baer.hado.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.model.HaState
import com.baer.hado.data.model.TodoItem
import com.baer.hado.data.repository.TodoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime

@HiltWorker
class OverdueNotificationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val todoRepository: TodoRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = OverdueNotificationSettingsManager.load(appContext)
        if (!settings.enabled || !tokenManager.isLoggedIn || !hasNotificationPermission(appContext)) {
            OverdueNotificationScheduler.cancelOneTime(appContext)
            return Result.success()
        }

        val lists = todoRepository.getTodoLists().getOrElse {
            return Result.retry()
        }.filterSelected(settings)

        val snapshots = buildListSnapshots(lists) ?: return Result.retry()
        val now = LocalDateTime.now()
        val activeEntries = snapshots.flatMap { snapshot ->
            snapshot.items
                .filter { !it.isCompleted && !it.due.isNullOrBlank() }
                .map { item ->
                    NotificationEntry(
                        listId = snapshot.list.entityId,
                        listName = snapshot.list.attributes.friendlyName ?: snapshot.list.entityId,
                        item = item
                    )
                }
        }

        val activeBaseKeys = activeEntries.mapTo(mutableSetOf()) { entry ->
            OverdueNotificationStateStore.baseKey(
                listId = entry.listId,
                itemUid = entry.item.uid,
                dueValue = entry.item.due.orEmpty(),
                timing = settings.timing
            )
        }
        OverdueNotificationStateStore.prune(appContext, activeBaseKeys)

        val dueNow = activeEntries.filter { entry -> shouldNotifyNow(entry, settings, now) }
        if (dueNow.isNotEmpty()) {
            OverdueNotifier.notify(
                context = appContext,
                items = dueNow.map { entry ->
                    OverdueNotificationItem(
                        notificationId = notificationIdFor(entry, settings),
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

    private suspend fun buildListSnapshots(lists: List<HaState>): List<ListSnapshot>? {
        val snapshots = mutableListOf<ListSnapshot>()
        for (list in lists) {
            val items = todoRepository.getTodoItems(list.entityId).getOrElse {
                return null
            }
            snapshots += ListSnapshot(list = list, items = items)
        }
        return snapshots
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
            listId = entry.listId,
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

    private fun List<HaState>.filterSelected(
        settings: OverdueNotificationSettings
    ): List<HaState> {
        if (settings.selectedListIds.isEmpty()) return this
        return filter { it.entityId in settings.selectedListIds }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class ListSnapshot(
        val list: HaState,
        val items: List<TodoItem>
    )

    private data class NotificationEntry(
        val listId: String,
        val listName: String,
        val item: TodoItem
    )
}
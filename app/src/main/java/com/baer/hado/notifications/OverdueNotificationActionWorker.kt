package com.baer.hado.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baer.hado.data.repository.TodoRepository
import com.baer.hado.widget.TodoWidgetWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

@HiltWorker
class OverdueNotificationActionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val todoRepository: TodoRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val listId = inputData.getString(OverdueNotificationActionReceiver.EXTRA_LIST_ID) ?: return Result.failure()
        val itemUid = inputData.getString(OverdueNotificationActionReceiver.EXTRA_ITEM_UID) ?: return Result.failure()
        val dueValue = inputData.getString(OverdueNotificationActionReceiver.EXTRA_DUE_VALUE).orEmpty()
        val timingName = inputData.getString(OverdueNotificationActionReceiver.EXTRA_TIMING) ?: return Result.failure()
        val notificationId = inputData.getInt(OverdueNotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0)
        val timing = runCatching {
            OverdueNotificationSettings.NotificationTiming.valueOf(timingName)
        }.getOrElse {
            return Result.failure()
        }

        val baseKey = OverdueNotificationStateStore.baseKey(
            listId = listId,
            itemUid = itemUid,
            dueValue = dueValue,
            timing = timing
        )

        when (action) {
            OverdueNotificationActionReceiver.ACTION_SNOOZE -> {
                OverdueNotificationStateStore.clearDelivered(appContext, baseKey, LocalDateTime.now().toLocalDate())
                OverdueNotificationStateStore.snoozeUntil(appContext, baseKey, LocalDateTime.now().plusHours(1))
            }

            OverdueNotificationActionReceiver.ACTION_MARK_DONE -> {
                todoRepository.updateItemStatus(listId, itemUid, true).getOrElse {
                    return Result.retry()
                }
                TodoWidgetWorker.enqueueOneTime(appContext)
            }

            else -> return Result.failure()
        }

        OverdueNotifier.cancel(appContext, notificationId)
        OverdueNotificationScheduler.reschedule(appContext)
        return Result.success()
    }

    companion object {
        const val KEY_ACTION = "action"
    }
}
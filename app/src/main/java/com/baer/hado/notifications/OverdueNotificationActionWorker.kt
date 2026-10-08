package com.baer.hado.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baer.hado.data.local.TokenManager
import com.baer.hado.data.repository.TodoRepository
import com.baer.hado.widget.TodoWidgetWorker
import com.baer.hado.widget.WidgetHttpClient
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime

@HiltWorker
class OverdueNotificationActionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val todoRepository: TodoRepository,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        // Notifications posted before multi-server carry no account and belong to the first server.
        val accountId = if (tokenManager.isDemoMode) null else {
            tokenManager.resolveWidgetAccountId(
                inputData.getString(OverdueNotificationActionReceiver.EXTRA_ACCOUNT_ID)
            ) ?: return Result.failure()
        }
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
            listId = TokenManager.scopedKey(accountId, listId),
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
                if (accountId == null) {
                    todoRepository.updateItemStatus(listId, itemUid, true).getOrElse {
                        return Result.retry()
                    }
                } else {
                    val payload = Gson().toJson(
                        mapOf("entity_id" to listId, "item" to itemUid, "status" to "completed")
                    )
                    val ok = WidgetHttpClient(appContext, accountId)
                        .post("api/services/todo/update_item", payload)
                        ?.use { it.isSuccessful } ?: false
                    if (!ok) return Result.retry()
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
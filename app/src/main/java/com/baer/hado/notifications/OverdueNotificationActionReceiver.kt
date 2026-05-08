package com.baer.hado.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

class OverdueNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val inputData = Data.Builder()
            .putString(OverdueNotificationActionWorker.KEY_ACTION, action)
            .putString(EXTRA_LIST_ID, intent.getStringExtra(EXTRA_LIST_ID))
            .putString(EXTRA_ITEM_UID, intent.getStringExtra(EXTRA_ITEM_UID))
            .putString(EXTRA_DUE_VALUE, intent.getStringExtra(EXTRA_DUE_VALUE))
            .putString(EXTRA_TIMING, intent.getStringExtra(EXTRA_TIMING))
            .putInt(EXTRA_NOTIFICATION_ID, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            .build()

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<OverdueNotificationActionWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )
    }

    companion object {
        const val ACTION_SNOOZE = "com.baer.hado.notifications.SNOOZE"
        const val ACTION_MARK_DONE = "com.baer.hado.notifications.MARK_DONE"
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_LIST_NAME = "list_name"
        const val EXTRA_ITEM_UID = "item_uid"
        const val EXTRA_ITEM_TITLE = "item_title"
        const val EXTRA_DUE_VALUE = "due_value"
        const val EXTRA_TIMING = "timing"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
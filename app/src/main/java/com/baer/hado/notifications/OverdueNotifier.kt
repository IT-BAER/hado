package com.baer.hado.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.baer.hado.R
import com.baer.hado.ui.MainActivity

data class OverdueNotificationItem(
    val notificationId: Int,
    val listId: String,
    val listName: String,
    val itemUid: String,
    val itemTitle: String,
    val dueValue: String,
    val timing: OverdueNotificationSettings.NotificationTiming
)

object OverdueNotifier {
    private const val GROUP_KEY = "hado_overdue_items"
    private const val SUMMARY_NOTIFICATION_ID = 4_200_000
    private const val REQUEST_CONTENT = 1_000
    private const val REQUEST_SNOOZE = 2_000
    private const val REQUEST_DONE = 3_000

    fun notify(context: Context, items: List<OverdueNotificationItem>) {
        if (items.isEmpty()) return

        val manager = NotificationManagerCompat.from(context)
        items.forEach { item ->
            val notification = NotificationCompat.Builder(context, OverdueNotificationChannel.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(item.itemTitle)
                .setContentText(item.listName)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.listName))
                .setContentIntent(createContentIntent(context, item.notificationId))
                .setAutoCancel(true)
                .setGroup(GROUP_KEY)
                .addAction(
                    0,
                    context.getString(R.string.action_remind_later),
                    createActionIntent(
                        context = context,
                        requestCode = REQUEST_SNOOZE + item.notificationId,
                        action = OverdueNotificationActionReceiver.ACTION_SNOOZE,
                        item = item
                    )
                )
                .addAction(
                    0,
                    context.getString(R.string.action_mark_done),
                    createActionIntent(
                        context = context,
                        requestCode = REQUEST_DONE + item.notificationId,
                        action = OverdueNotificationActionReceiver.ACTION_MARK_DONE,
                        item = item
                    )
                )
                .build()

            manager.notify(item.notificationId, notification)
        }

        val summary = NotificationCompat.Builder(context, OverdueNotificationChannel.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_summary_title))
            .setContentText(
                context.getString(
                    R.string.notification_summary_text,
                    items.size
                )
            )
            .setContentIntent(createContentIntent(context, REQUEST_CONTENT))
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .build()

        manager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    fun cancel(context: Context, notificationId: Int) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notificationId)
        manager.cancel(SUMMARY_NOTIFICATION_ID)
    }

    private fun createContentIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createActionIntent(
        context: Context,
        requestCode: Int,
        action: String,
        item: OverdueNotificationItem
    ): PendingIntent {
        val intent = Intent(context, OverdueNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(OverdueNotificationActionReceiver.EXTRA_LIST_ID, item.listId)
            putExtra(OverdueNotificationActionReceiver.EXTRA_LIST_NAME, item.listName)
            putExtra(OverdueNotificationActionReceiver.EXTRA_ITEM_UID, item.itemUid)
            putExtra(OverdueNotificationActionReceiver.EXTRA_ITEM_TITLE, item.itemTitle)
            putExtra(OverdueNotificationActionReceiver.EXTRA_DUE_VALUE, item.dueValue)
            putExtra(OverdueNotificationActionReceiver.EXTRA_TIMING, item.timing.name)
            putExtra(OverdueNotificationActionReceiver.EXTRA_NOTIFICATION_ID, item.notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
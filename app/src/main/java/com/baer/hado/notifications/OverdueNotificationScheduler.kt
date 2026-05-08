package com.baer.hado.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object OverdueNotificationScheduler {
    private const val PERIODIC_WORK_NAME = "overdue_notification_periodic"
    private const val ONE_TIME_WORK_NAME = "overdue_notification_next"

    fun reschedule(context: Context) {
        val settings = OverdueNotificationSettingsManager.load(context)
        if (!settings.enabled) {
            cancelAll(context)
            return
        }

        enqueuePeriodic(context)
        enqueueImmediate(context)
    }

    fun scheduleNext(context: Context, nextRunAt: LocalDateTime?) {
        if (nextRunAt == null) {
            cancelOneTime(context)
            return
        }

        val delay = Duration.between(LocalDateTime.now(), nextRunAt)
            .toMillis()
            .coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<OverdueNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    fun cancelOneTime(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<OverdueNotificationWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun enqueuePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<OverdueNotificationWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
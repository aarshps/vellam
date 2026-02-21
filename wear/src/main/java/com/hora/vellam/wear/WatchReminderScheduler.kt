package com.hora.vellam.wear

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WatchReminderScheduler {
    private const val DEFAULT_INTERVAL_MINS = 60
    private const val MIN_INTERVAL_MINS = 15
    private const val MAX_INTERVAL_MINS = 240

    fun ensureScheduled(
        context: Context,
        intervalMins: Int = DEFAULT_INTERVAL_MINS,
        replace: Boolean = false
    ) {
        val safeInterval = sanitizeInterval(intervalMins).toLong()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

        val request = OneTimeWorkRequestBuilder<WatchReminderWorker>()
            .setInitialDelay(safeInterval, TimeUnit.MINUTES)
            .addTag(WearReminderContract.WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WearReminderContract.WORK_NAME,
            policy,
            request
        )
    }

    fun sanitizeInterval(intervalMins: Int): Int =
        intervalMins.coerceIn(MIN_INTERVAL_MINS, MAX_INTERVAL_MINS)
}

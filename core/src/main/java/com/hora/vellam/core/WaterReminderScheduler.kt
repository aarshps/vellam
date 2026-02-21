package com.hora.vellam.core

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WaterReminderScheduler {
    private const val UNIQUE_WORK_NAME = "water_reminder"
    private const val MIN_INTERVAL_MINS = 15
    private const val MAX_INTERVAL_MINS = 240

    fun schedule(context: Context, intervalMins: Int) {
        val safeInterval = intervalMins.coerceIn(MIN_INTERVAL_MINS, MAX_INTERVAL_MINS).toLong()
        val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(
            safeInterval,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

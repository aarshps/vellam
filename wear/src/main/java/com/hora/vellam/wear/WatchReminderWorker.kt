package com.hora.vellam.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hora.vellam.core.data.FirestoreRepository
import java.time.LocalTime

class WatchReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        var settings = WearSettingsStore.read(applicationContext)
        val shouldFetchFromCloud =
            settings.updatedAtMillis == 0L ||
                System.currentTimeMillis() - settings.updatedAtMillis >= 6 * 60 * 60 * 1000L

        if (shouldFetchFromCloud) {
            val repo = FirestoreRepository()
            val cloudSettings = repo.getSettingsOnce()
            if (cloudSettings != null) {
                settings = WearSettingsStore.writeFromUserSettings(applicationContext, cloudSettings)
                WearTileUpdater.request(applicationContext)
            }
        }

        val intervalMins = WatchReminderScheduler.sanitizeInterval(settings.reminderIntervalMins)

        try {
            if (!isInSleepWindow(settings) && shouldNotifyNow(intervalMins)) {
                showNotification(intakeAmountMl = settings.intakeAmountMl.coerceAtLeast(1))
                markReminderShownNow()
            }
        } finally {
            WatchReminderScheduler.ensureScheduled(
                context = applicationContext,
                intervalMins = intervalMins,
                replace = true
            )
        }

        return Result.success()
    }

    private fun showNotification(intakeAmountMl: Int) {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                WearReminderContract.CHANNEL_ID,
                "Hydration reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val actionIntent = Intent(applicationContext, WearDrinkActionReceiver::class.java).apply {
            action = WearReminderContract.ACTION_MARK_DRANK
        }

        val actionPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openPendingIntent = PendingIntent.getActivity(
            applicationContext,
            1,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, WearReminderContract.CHANNEL_ID)
            .setSmallIcon(com.hora.vellam.core.R.drawable.ic_logo)
            .setContentTitle("Time to hydrate")
            .setContentText("Log $intakeAmountMl ml now")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "I Drank", actionPendingIntent)
            .build()

        manager.notify(WearReminderContract.NOTIFICATION_ID, notification)
    }

    private fun shouldNotifyNow(intervalMins: Int): Boolean {
        val prefs = applicationContext.getSharedPreferences("watch_reminders", Context.MODE_PRIVATE)
        val lastShownAt = prefs.getLong("last_shown_at", 0L)
        if (lastShownAt == 0L) return true

        val elapsed = System.currentTimeMillis() - lastShownAt
        return elapsed >= intervalMins * 60_000L
    }

    private fun markReminderShownNow() {
        val prefs = applicationContext.getSharedPreferences("watch_reminders", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_shown_at", System.currentTimeMillis()).apply()
    }

    private fun isInSleepWindow(settings: WearSettingsSnapshot): Boolean {
        val now = LocalTime.now()
        val start = parseTime(settings.sleepStartTime, default = LocalTime.of(22, 0))
        val end = parseTime(settings.sleepEndTime, default = LocalTime.of(7, 0))

        return if (start <= end) {
            now >= start && now <= end
        } else {
            now >= start || now <= end
        }
    }

    private fun parseTime(value: String, default: LocalTime): LocalTime {
        return try {
            LocalTime.parse(value)
        } catch (_: Exception) {
            default
        }
    }
}

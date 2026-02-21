package com.hora.vellam.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.*

class WaterReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferenceManager(applicationContext)
        val sleepStart = prefs.sleepStartFlow.first()
        val sleepEnd = prefs.sleepEndFlow.first()
        
        if (isInSleepTime(sleepStart, sleepEnd)) {
            return Result.success()
        }
        
        sendNotification()
        return Result.success()
    }

    private fun isInSleepTime(start: String, end: String): Boolean {
        val now = Calendar.getInstance()
        val current = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        
        return if (start < end) {
            current >= start && current <= end
        } else {
            current >= start || current <= end
        }
    }

    private fun sendNotification() {
        val channelId = "water_reminder"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Modern API: Channel creation is required and supported directly
        val channel = NotificationChannel(channelId, "Water Reminder", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        // We will need a broadcast receiver in the app module to handle the "Drank Water" action
        // Explicitly target the receiver
        val drankIntent = Intent("com.hora.vellam.DRANK_WATER").apply {
             setClassName(applicationContext.packageName, "com.hora.vellam.WaterActionReceiver")
        }
        val drankPendingIntent = PendingIntent.getBroadcast(
            applicationContext, 
            0, 
            drankIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.hora.vellam.core.R.drawable.ic_logo)
            .setContentTitle("Drink Water!")
            .setContentText("It's time to hydrate. Stay healthy!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_edit, "I Drank", drankPendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        manager.notify(1, notification)
    }
}

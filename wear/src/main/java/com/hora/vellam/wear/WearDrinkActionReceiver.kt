package com.hora.vellam.wear

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hora.vellam.core.data.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WearDrinkActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WearReminderContract.ACTION_MARK_DRANK) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = FirestoreRepository()
                val amount = repo.getSettingsOnce()?.intakeAmountMl?.coerceAtLeast(1) ?: 250
                repo.addWaterIntake(amount)
                com.hora.vellam.core.HapticManager.vibrateSwallow(context)

                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(WearReminderContract.NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.e("WearDrinkAction", "Failed to log intake from notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

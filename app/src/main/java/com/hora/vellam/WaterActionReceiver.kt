package com.hora.vellam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

class WaterActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.hora.vellam.DRANK_WATER") {
            val pendingResult = goAsync()
            val repo = com.hora.vellam.core.data.FirestoreRepository()
            vibrateSwallow(context)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    repo.addWaterIntake(250)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun vibrateSwallow(context: Context) =
        com.hora.vellam.core.HapticManager.vibrateSwallow(context)
}

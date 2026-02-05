package com.hora.vellam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class WaterActionReceiver : BroadcastReceiver() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.hora.vellam.DRANK_WATER") {
            val pendingResult = goAsync()
            val repo = com.hora.vellam.core.data.FirestoreRepository()
            vibrateSwallow(context)
            scope.launch {
                try {
                    repo.addWaterIntake(250)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun vibrateSwallow(context: Context) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 100, 150, 80, 100, 120)
            val amplitudes = intArrayOf(0, 50, 0, 80, 0, 120)
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }
}

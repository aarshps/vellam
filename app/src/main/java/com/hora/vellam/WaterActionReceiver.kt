package com.hora.vellam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hora.vellam.core.PreferenceManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class WaterActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.hora.vellam.DRANK_WATER") {
            val prefs = PreferenceManager(context)
            vibrateSwallow(context)
            GlobalScope.launch {
                prefs.updateIntake(250)
            }
        }
    }

    private fun vibrateSwallow(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 100, 150, 80, 100, 120)
            val amplitudes = intArrayOf(0, 50, 0, 80, 0, 120)
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            vibrator.vibrate(200)
        }
    }
}

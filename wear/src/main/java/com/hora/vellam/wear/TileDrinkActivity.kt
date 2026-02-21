package com.hora.vellam.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.hora.vellam.core.data.FirestoreRepository
import kotlinx.coroutines.launch

class TileDrinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                val repo = FirestoreRepository()
                val amount = WearSettingsStore.read(this@TileDrinkActivity).intakeAmountMl
                repo.addWaterIntake(amount)
                com.hora.vellam.core.HapticManager.vibrateSwallow(this@TileDrinkActivity)
                WearTileUpdater.request(this@TileDrinkActivity)
            } catch (e: Exception) {
                Log.e("TileDrinkActivity", "Tile intake logging failed", e)
            } finally {
                finish()
            }
        }
    }
}

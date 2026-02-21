package com.hora.vellam.wear

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import com.hora.vellam.wear.tile.DrinkTileService

object WearTileUpdater {
    fun request(context: Context) {
        try {
            TileService.getUpdater(context).requestUpdate(DrinkTileService::class.java)
        } catch (e: Exception) {
            Log.w("WearTileUpdater", "Tile update request failed", e)
        }
    }
}

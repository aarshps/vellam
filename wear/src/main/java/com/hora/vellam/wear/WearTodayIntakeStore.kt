package com.hora.vellam.wear

import android.content.Context
import java.time.LocalDate

data class WearTodayIntakeSnapshot(
    val dayKey: String,
    val totalMl: Int
)

object WearTodayIntakeStore {
    private const val PREFS_NAME = "wear_today_intake"
    private const val KEY_DAY = "day_key"
    private const val KEY_TOTAL = "today_total_ml"

    private fun todayKey(): String = LocalDate.now().toString()

    fun read(context: Context): WearTodayIntakeSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedDay = prefs.getString(KEY_DAY, null)
        val day = todayKey()
        if (storedDay == day) {
            return WearTodayIntakeSnapshot(
                dayKey = day,
                totalMl = prefs.getInt(KEY_TOTAL, 0).coerceAtLeast(0)
            )
        }

        prefs.edit()
            .putString(KEY_DAY, day)
            .putInt(KEY_TOTAL, 0)
            .apply()
        return WearTodayIntakeSnapshot(dayKey = day, totalMl = 0)
    }

    fun setTodayTotal(context: Context, totalMl: Int): WearTodayIntakeSnapshot {
        val day = todayKey()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DAY, day)
            .putInt(KEY_TOTAL, totalMl.coerceAtLeast(0))
            .apply()
        return read(context)
    }

    fun addIntake(context: Context, amountMl: Int): WearTodayIntakeSnapshot {
        val current = read(context)
        return setTodayTotal(context, current.totalMl + amountMl.coerceAtLeast(0))
    }
}

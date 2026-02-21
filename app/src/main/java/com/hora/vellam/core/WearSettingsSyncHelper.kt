package com.hora.vellam.core

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.hora.vellam.core.data.UserSettings
import java.time.LocalDate
import kotlinx.coroutines.tasks.await

object WearSettingsSyncHelper {
    suspend fun sendSettingsToWear(context: Context, settings: UserSettings) {
        try {
            val putDataMapRequest = PutDataMapRequest.create(WearSettingsSyncContract.SETTINGS_PATH)
            putDataMapRequest.dataMap.putLong(
                WearSettingsSyncContract.KEY_TIMESTAMP,
                System.currentTimeMillis()
            )
            putDataMapRequest.dataMap.putInt(
                WearSettingsSyncContract.KEY_DAILY_GOAL_ML,
                settings.dailyGoalMl
            )
            putDataMapRequest.dataMap.putInt(
                WearSettingsSyncContract.KEY_INTAKE_AMOUNT_ML,
                settings.intakeAmountMl
            )
            putDataMapRequest.dataMap.putInt(
                WearSettingsSyncContract.KEY_REMINDER_INTERVAL_MINS,
                settings.reminderIntervalMins
            )
            putDataMapRequest.dataMap.putString(
                WearSettingsSyncContract.KEY_SLEEP_START_TIME,
                settings.sleepStartTime
            )
            putDataMapRequest.dataMap.putString(
                WearSettingsSyncContract.KEY_SLEEP_END_TIME,
                settings.sleepEndTime
            )

            val request = putDataMapRequest.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            Log.w("WearSettingsSync", "Settings sync to watch failed", e)
        }
    }

    suspend fun sendTodayIntakeToWear(
        context: Context,
        todayTotalMl: Int,
        dayKey: String = LocalDate.now().toString()
    ) {
        try {
            val putDataMapRequest = PutDataMapRequest.create(WearSettingsSyncContract.TODAY_INTAKE_PATH)
            putDataMapRequest.dataMap.putLong(
                WearSettingsSyncContract.KEY_TIMESTAMP,
                System.currentTimeMillis()
            )
            putDataMapRequest.dataMap.putString(
                WearSettingsSyncContract.KEY_DAY_KEY,
                dayKey
            )
            putDataMapRequest.dataMap.putInt(
                WearSettingsSyncContract.KEY_TODAY_TOTAL_ML,
                todayTotalMl.coerceAtLeast(0)
            )

            val request = putDataMapRequest.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            Log.w("WearSettingsSync", "Today intake sync to watch failed", e)
        }
    }
}

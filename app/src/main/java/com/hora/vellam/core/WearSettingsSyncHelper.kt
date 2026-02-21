package com.hora.vellam.core

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.hora.vellam.core.data.UserSettings
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
}

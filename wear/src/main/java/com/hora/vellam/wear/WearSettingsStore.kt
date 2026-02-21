package com.hora.vellam.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap
import com.hora.vellam.core.WearSettingsSyncContract
import com.hora.vellam.core.data.UserSettings

data class WearSettingsSnapshot(
    val dailyGoalMl: Int = 2000,
    val intakeAmountMl: Int = 250,
    val reminderIntervalMins: Int = 60,
    val sleepStartTime: String = "22:00",
    val sleepEndTime: String = "07:00",
    val updatedAtMillis: Long = 0L
)

object WearSettingsStore {
    const val PREFS_NAME = "wear_settings_cache"

    private const val KEY_DAILY_GOAL_ML = "daily_goal_ml"
    private const val KEY_INTAKE_AMOUNT_ML = "intake_amount_ml"
    private const val KEY_REMINDER_INTERVAL_MINS = "reminder_interval_mins"
    private const val KEY_SLEEP_START_TIME = "sleep_start_time"
    private const val KEY_SLEEP_END_TIME = "sleep_end_time"
    private const val KEY_UPDATED_AT = "updated_at"

    fun read(context: Context): WearSettingsSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WearSettingsSnapshot(
            dailyGoalMl = prefs.getInt(KEY_DAILY_GOAL_ML, 2000).coerceAtLeast(1),
            intakeAmountMl = prefs.getInt(KEY_INTAKE_AMOUNT_ML, 250).coerceAtLeast(1),
            reminderIntervalMins = prefs.getInt(KEY_REMINDER_INTERVAL_MINS, 60).coerceAtLeast(15),
            sleepStartTime = prefs.getString(KEY_SLEEP_START_TIME, "22:00") ?: "22:00",
            sleepEndTime = prefs.getString(KEY_SLEEP_END_TIME, "07:00") ?: "07:00",
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun write(context: Context, snapshot: WearSettingsSnapshot): WearSettingsSnapshot {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DAILY_GOAL_ML, snapshot.dailyGoalMl.coerceAtLeast(1))
            .putInt(KEY_INTAKE_AMOUNT_ML, snapshot.intakeAmountMl.coerceAtLeast(1))
            .putInt(KEY_REMINDER_INTERVAL_MINS, snapshot.reminderIntervalMins.coerceAtLeast(15))
            .putString(KEY_SLEEP_START_TIME, snapshot.sleepStartTime)
            .putString(KEY_SLEEP_END_TIME, snapshot.sleepEndTime)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAtMillis)
            .apply()
        return read(context)
    }

    fun writeFromDataMap(context: Context, dataMap: DataMap): WearSettingsSnapshot {
        val existing = read(context)
        val snapshot = WearSettingsSnapshot(
            dailyGoalMl = dataMap.getInt(
                WearSettingsSyncContract.KEY_DAILY_GOAL_ML,
                existing.dailyGoalMl
            ),
            intakeAmountMl = dataMap.getInt(
                WearSettingsSyncContract.KEY_INTAKE_AMOUNT_ML,
                existing.intakeAmountMl
            ),
            reminderIntervalMins = dataMap.getInt(
                WearSettingsSyncContract.KEY_REMINDER_INTERVAL_MINS,
                existing.reminderIntervalMins
            ),
            sleepStartTime = dataMap.getString(
                WearSettingsSyncContract.KEY_SLEEP_START_TIME
            ) ?: existing.sleepStartTime,
            sleepEndTime = dataMap.getString(
                WearSettingsSyncContract.KEY_SLEEP_END_TIME
            ) ?: existing.sleepEndTime,
            updatedAtMillis = dataMap.getLong(
                WearSettingsSyncContract.KEY_TIMESTAMP,
                System.currentTimeMillis()
            )
        )
        return write(context, snapshot)
    }

    fun writeFromUserSettings(context: Context, settings: UserSettings): WearSettingsSnapshot {
        return write(
            context,
            WearSettingsSnapshot(
                dailyGoalMl = settings.dailyGoalMl,
                intakeAmountMl = settings.intakeAmountMl,
                reminderIntervalMins = settings.reminderIntervalMins,
                sleepStartTime = settings.sleepStartTime,
                sleepEndTime = settings.sleepEndTime,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }
}

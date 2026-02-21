package com.hora.vellam.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hora.vellam.core.data.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {
    companion object {
        val INTAKE_KEY = intPreferencesKey("water_intake")
        val INTERVAL_KEY = intPreferencesKey("reminder_interval_mins")
        val SLEEP_START_KEY = stringPreferencesKey("sleep_start_time") // HH:mm
        val SLEEP_END_KEY = stringPreferencesKey("sleep_end_time") // HH:mm
        val LAST_REMINDER_TIME = longPreferencesKey("last_reminder_time")
        val GOOGLE_SANS_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("use_google_sans")
        val DAILY_GOAL_KEY = intPreferencesKey("daily_goal")
        val INTAKE_AMOUNT_KEY = intPreferencesKey("intake_amount")
        val APP_THEME_KEY = intPreferencesKey("app_theme") // 0=System, 1=Light, 2=Dark
    }

    val intakeFlow: Flow<Int> = context.dataStore.data.map { it[INTAKE_KEY] ?: 0 }
    val intervalFlow: Flow<Int> = context.dataStore.data.map { it[INTERVAL_KEY] ?: 60 }
    val sleepStartFlow: Flow<String> = context.dataStore.data.map { it[SLEEP_START_KEY] ?: "22:00" }
    val sleepEndFlow: Flow<String> = context.dataStore.data.map { it[SLEEP_END_KEY] ?: "07:00" }
    val googleSansFlow: Flow<Boolean> = context.dataStore.data.map { it[GOOGLE_SANS_KEY] ?: true }
    val dailyGoalFlow: Flow<Int> = context.dataStore.data.map { it[DAILY_GOAL_KEY] ?: 2000 }
    val intakeAmountFlow: Flow<Int> = context.dataStore.data.map { it[INTAKE_AMOUNT_KEY] ?: 250 }

    suspend fun updateIntake(delta: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[INTAKE_KEY] ?: 0
            prefs[INTAKE_KEY] = (current + delta).coerceAtLeast(0)
        }
    }

    suspend fun resetIntake() {
        context.dataStore.edit { it[INTAKE_KEY] = 0 }
    }

    suspend fun setInterval(mins: Int) {
        context.dataStore.edit { it[INTERVAL_KEY] = mins }
    }

    suspend fun setSleepTimes(start: String, end: String) {
        context.dataStore.edit {
            it[SLEEP_START_KEY] = start
            it[SLEEP_END_KEY] = end
        }
    }

    suspend fun setGoogleSans(enabled: Boolean) {
        context.dataStore.edit { it[GOOGLE_SANS_KEY] = enabled }
    }

    suspend fun setDailyGoal(goal: Int) {
        context.dataStore.edit { it[DAILY_GOAL_KEY] = goal }
    }

    suspend fun setIntakeAmount(amount: Int) {
        context.dataStore.edit { it[INTAKE_AMOUNT_KEY] = amount }
    }

    suspend fun applyRemoteSettings(settings: UserSettings) {
        context.dataStore.edit {
            it[DAILY_GOAL_KEY] = settings.dailyGoalMl
            it[INTAKE_AMOUNT_KEY] = settings.intakeAmountMl
            it[INTERVAL_KEY] = settings.reminderIntervalMins
            it[SLEEP_START_KEY] = settings.sleepStartTime
            it[SLEEP_END_KEY] = settings.sleepEndTime
            it[GOOGLE_SANS_KEY] = settings.useGoogleSans
            it[APP_THEME_KEY] = settings.appTheme
        }
    }

    suspend fun resetAllSettings() {
        context.dataStore.edit { it.clear() }
    }

    val themeFlow: Flow<Int> = context.dataStore.data.map { it[APP_THEME_KEY] ?: 0 }

    suspend fun setAppTheme(theme: Int) {
        context.dataStore.edit { it[APP_THEME_KEY] = theme }
    }
}

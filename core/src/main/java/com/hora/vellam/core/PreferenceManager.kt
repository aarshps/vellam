package com.hora.vellam.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    }

    val intakeFlow: Flow<Int> = context.dataStore.data.map { it[INTAKE_KEY] ?: 0 }
    val intervalFlow: Flow<Int> = context.dataStore.data.map { it[INTERVAL_KEY] ?: 60 }
    val sleepStartFlow: Flow<String> = context.dataStore.data.map { it[SLEEP_START_KEY] ?: "22:00" }
    val sleepEndFlow: Flow<String> = context.dataStore.data.map { it[SLEEP_END_KEY] ?: "07:00" }

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
}

package com.hora.vellam.wear.services

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.hora.vellam.core.WearSettingsSyncContract
import com.hora.vellam.core.auth.AuthManager
import com.hora.vellam.wear.WearSettingsStore
import com.hora.vellam.wear.WearTileUpdater
import com.hora.vellam.wear.WearTodayIntakeStore
import com.hora.vellam.wear.WatchReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AuthDataListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var authManager: AuthManager

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach

            val path = event.dataItem.uri.path ?: return@forEach
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (path) {
                "/auth/google_token" -> {
                    val token = dataMap.getString("token")
                    if (token.isNullOrEmpty()) return@forEach

                    Log.d("AuthDataListener", "Received token from phone")
                    scope.launch {
                        try {
                            authManager.signInWithGoogle(token)
                            Log.d("AuthDataListener", "Sign in successful")
                            WatchReminderScheduler.ensureScheduled(
                                context = this@AuthDataListenerService,
                                replace = true
                            )
                        } catch (e: Exception) {
                            Log.e("AuthDataListener", "Sign in failed", e)
                        }
                    }
                }

                WearSettingsSyncContract.SETTINGS_PATH -> {
                    val snapshot = WearSettingsStore.writeFromDataMap(this, dataMap)
                    WatchReminderScheduler.ensureScheduled(
                        context = this,
                        intervalMins = snapshot.reminderIntervalMins,
                        replace = true
                    )
                    WearTileUpdater.request(this)
                }

                WearSettingsSyncContract.TODAY_INTAKE_PATH -> {
                    val dayKey = dataMap.getString(WearSettingsSyncContract.KEY_DAY_KEY)
                    val todayKey = WearTodayIntakeStore.currentDayKey()
                    if (!dayKey.isNullOrBlank() && dayKey == todayKey) {
                        WearTodayIntakeStore.setTodayTotal(
                            context = this,
                            totalMl = dataMap.getInt(WearSettingsSyncContract.KEY_TODAY_TOTAL_ML, 0)
                        )
                    } else {
                        // Ignore stale day snapshots and let local day rollover logic normalize state.
                        WearTodayIntakeStore.read(this)
                    }
                    WearTileUpdater.request(this)
                }
            }
        }
    }
}

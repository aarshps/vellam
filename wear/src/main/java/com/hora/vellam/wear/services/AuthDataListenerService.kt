package com.hora.vellam.wear.services

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.hora.vellam.core.auth.AuthManager
import com.hora.vellam.wear.MainActivity
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
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (path == "/auth/google_token") {
                    val dataMapItem = com.google.android.gms.wearable.DataMapItem.fromDataItem(event.dataItem)
                    val token = dataMapItem.dataMap.getString("token")
                    
                    if (!token.isNullOrEmpty()) {
                        Log.d("AuthDataListener", "Received token from phone")
                        scope.launch {
                            try {
                                authManager.signInWithGoogle(token)
                                Log.d("AuthDataListener", "Sign in successful")
                                // Optional: Start Activity if needed, but UI updates via AuthManager state usually suffice
                            } catch (e: Exception) {
                                Log.e("AuthDataListener", "Sign in failed", e)
                            }
                        }
                    }
                }
            }
        }
    }
}

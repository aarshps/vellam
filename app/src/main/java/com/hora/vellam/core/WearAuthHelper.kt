package com.hora.vellam.core

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object WearAuthHelper {
    private const val AUTH_PATH = "/auth/google_token"
    private const val KEY_TOKEN = "token"
    private const val KEY_TIMESTAMP = "timestamp"

    suspend fun sendTokenToWear(context: Context, token: String) {
        try {
            val dataClient = Wearable.getDataClient(context)
            
            // Create a PutDataRequest with the token
            val putDataMapRequest = PutDataMapRequest.create(AUTH_PATH)
            putDataMapRequest.dataMap.putString(KEY_TOKEN, token)
            // Add timestamp to ensure data change event is triggered even if token is same
            putDataMapRequest.dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            
            val request = putDataMapRequest.asPutDataRequest()
            request.setUrgent() // Deliver immediately
            
            dataClient.putDataItem(request).await()
            Log.d("WearAuthHelper", "Token sent to Wear OS")
        } catch (e: Exception) {
            Log.e("WearAuthHelper", "Failed to send token to Wear OS", e)
        }
    }
}

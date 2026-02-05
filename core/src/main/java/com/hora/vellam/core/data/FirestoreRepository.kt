package com.hora.vellam.core.data

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    init {
        // Enable persistent cache for better watch-to-phone sync
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings
    }

    private val userDocument
        get() = auth.currentUser?.let { user ->
            db.collection("users").document(user.uid)
        }

    private val userIntakeCollection
        get() = userDocument?.collection("intake")

    fun getSettings(): Flow<UserSettings> = callbackFlow {
        val doc = userDocument
        if (doc == null) {
            trySend(UserSettings())
            close()
            return@callbackFlow
        }

        val subscription = doc.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            val settings = snapshot?.toObject(UserSettings::class.java) ?: UserSettings()
            trySend(settings)
        }
        awaitClose { subscription.remove() }
    }

    suspend fun updateSettings(settings: UserSettings) {
        userDocument?.set(settings)?.await()
    }

    suspend fun addWaterIntake(amountMl: Int) {
        val collection = userIntakeCollection ?: return
        val intake = WaterIntake(
            amountMl = amountMl,
            timestamp = com.google.firebase.Timestamp.now()
        )
        // Let Firestore generate the ID
        collection.add(intake).await()
    }

    suspend fun deleteIntake(id: String) {
        userIntakeCollection?.document(id)?.delete()?.await()
    }

    fun getTodayIntake(): Flow<Int> = callbackFlow {
        val collection = userIntakeCollection
        if (collection == null) {
            trySend(0)
            close()
            return@callbackFlow
        }

        // Calculate start of day
        val now = java.time.LocalDate.now()
        val startOfDay = java.time.ZoneId.systemDefault().let { zone ->
            val instant = now.atStartOfDay(zone).toInstant()
            com.google.firebase.Timestamp(instant.epochSecond, instant.nano)
        }

        val query = collection
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)

        val subscription = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                // handle error
                return@addSnapshotListener
            }

            val total = snapshot?.documents?.sumOf { 
                it.toObject(WaterIntake::class.java)?.amountMl ?: 0 
            } ?: 0
            trySend(total)
        }

        awaitClose { subscription.remove() }
    }

    fun getHistory(): Flow<List<WaterIntake>> = callbackFlow {
        val collection = userIntakeCollection
        if (collection == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = collection.orderBy("timestamp", Query.Direction.DESCENDING)

        val subscription = query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(WaterIntake::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            trySend(list)
        }

        awaitClose { subscription.remove() }
    }
}

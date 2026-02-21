package com.hora.vellam.core.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    init {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings
    }

    private fun userDocument(uid: String) =
        db.collection("users").document(uid)

    private fun userIntakeCollection(uid: String) =
        userDocument(uid).collection("intake")

    // ── Auth-aware flow helper ───────────────────────────────────────────
    // Instead of checking auth.currentUser once and closing, these flows
    // listen for auth state changes and re-subscribe to Firestore when
    // a user signs in.  This fixes watch-to-phone sync where auth may
    // complete *after* the composable already started collecting.
    // ─────────────────────────────────────────────────────────────────────

    fun getSettings(): Flow<UserSettings> = callbackFlow {
        var firestoreReg: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            // Clean up previous Firestore listener
            firestoreReg?.remove()

            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(UserSettings())
                return@AuthStateListener
            }

            firestoreReg = userDocument(user.uid).addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val settings = snapshot?.toObject(UserSettings::class.java) ?: UserSettings()
                trySend(settings)
            }
        }

        auth.addAuthStateListener(authListener)

        awaitClose {
            auth.removeAuthStateListener(authListener)
            firestoreReg?.remove()
        }
    }

    suspend fun updateSettings(settings: UserSettings) {
        val uid = auth.currentUser?.uid ?: return
        userDocument(uid).set(settings).await()
    }

    suspend fun addWaterIntake(amountMl: Int) {
        val uid = auth.currentUser?.uid ?: return
        val intake = WaterIntake(
            amountMl = amountMl,
            timestamp = com.google.firebase.Timestamp.now()
        )
        userIntakeCollection(uid).add(intake).await()
    }

    suspend fun deleteIntake(id: String) {
        val uid = auth.currentUser?.uid ?: return
        userIntakeCollection(uid).document(id).delete().await()
    }

    fun getTodayIntake(): Flow<Int> = callbackFlow {
        var firestoreReg: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firestoreReg?.remove()

            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(0)
                return@AuthStateListener
            }

            val now = java.time.LocalDate.now()
            val startOfDay = java.time.ZoneId.systemDefault().let { zone ->
                val instant = now.atStartOfDay(zone).toInstant()
                com.google.firebase.Timestamp(instant.epochSecond, instant.nano)
            }

            firestoreReg = userIntakeCollection(user.uid)
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    val total = snapshot?.documents?.sumOf {
                        it.toObject(WaterIntake::class.java)?.amountMl ?: 0
                    } ?: 0
                    trySend(total)
                }
        }

        auth.addAuthStateListener(authListener)

        awaitClose {
            auth.removeAuthStateListener(authListener)
            firestoreReg?.remove()
        }
    }

    fun getHistory(): Flow<List<WaterIntake>> = callbackFlow {
        var firestoreReg: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firestoreReg?.remove()

            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(emptyList())
                return@AuthStateListener
            }

            firestoreReg = userIntakeCollection(user.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(WaterIntake::class.java)?.copy(id = doc.id)
                    } ?: emptyList()
                    trySend(list)
                }
        }

        auth.addAuthStateListener(authListener)

        awaitClose {
            auth.removeAuthStateListener(authListener)
            firestoreReg?.remove()
        }
    }
}

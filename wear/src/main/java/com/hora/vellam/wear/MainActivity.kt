package com.hora.vellam.wear

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.hora.vellam.core.auth.AuthManager
import com.hora.vellam.core.data.FirestoreRepository
import com.hora.vellam.wear.ui.theme.VellamWearTheme
import kotlinx.coroutines.launch

private const val DEFAULT_WATCH_INTAKE_ML = 250

class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private val firestoreRepository = FirestoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        setContent {
            VellamWearTheme {
                val user by authManager.currentUser.collectAsStateWithLifecycle()
                if (user == null) {
                    LoginScreen(authManager)
                } else {
                    DrinkDoneScreen(firestoreRepository)
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(authManager: AuthManager) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by rememberSaveable { mutableStateOf(false) }
    var silentAttempted by rememberSaveable { mutableStateOf(false) }

    suspend fun requestGoogleIdToken(autoSelect: Boolean): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("1051691694392-mqhhd8k6ufp1jfntuihjid5bofm4rlfe.apps.googleusercontent.com")
            .setAutoSelectEnabled(autoSelect)
            .build()

        val request = GetCredentialRequest.Builder()
            .setCredentialOptions(listOf(googleIdOption))
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.e("WearLogin", "Invalid Google token payload", e)
            null
        } catch (e: GetCredentialException) {
            Log.e("WearLogin", "Credential request failed", e)
            null
        } catch (e: Exception) {
            Log.e("WearLogin", "Unexpected sign in failure", e)
            null
        }
    }

    LaunchedEffect(silentAttempted) {
        if (!silentAttempted) {
            silentAttempted = true
            val token = requestGoogleIdToken(autoSelect = true) ?: return@LaunchedEffect
            try {
                authManager.signInWithGoogle(token)
            } catch (e: Exception) {
                Log.e("WearLogin", "Silent Firebase auth failed", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sign in on watch",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        scope.launch {
                            if (isLoading) return@launch
                            isLoading = true

                            val token = requestGoogleIdToken(autoSelect = false)
                            if (token == null) {
                                isLoading = false
                                return@launch
                            }

                            try {
                                authManager.signInWithGoogle(token)
                            } catch (e: Exception) {
                                Log.e("WearLogin", "Firebase auth failed", e)
                                isLoading = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Google Sign In")
                }
            }
        }
    }
}

@Composable
private fun DrinkDoneScreen(repo: FirestoreRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var isSaving by rememberSaveable { mutableStateOf(false) }
    var statusText by rememberSaveable { mutableStateOf("Tap to log water") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    statusText = "Saving..."
                    scope.launch {
                        try {
                            repo.addWaterIntake(DEFAULT_WATCH_INTAKE_ML)
                            vibrateSwallow(context)
                            statusText = "Done"
                        } catch (e: Exception) {
                            Log.e("WearDrink", "Failed to log water", e)
                            statusText = "Failed, try again"
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(if (isSaving) "Saving..." else "I Drank")
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

fun vibrateSwallow(context: Context) = com.hora.vellam.core.HapticManager.vibrateSwallow(context)

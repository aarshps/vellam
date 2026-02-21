package com.hora.vellam.wear

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.hora.vellam.core.auth.AuthManager
import com.hora.vellam.core.data.FirestoreRepository
import com.hora.vellam.wear.ui.theme.VellamWearTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private val firestoreRepository = FirestoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)
        WatchReminderScheduler.ensureScheduled(this)

        setContent {
            VellamWearTheme {
                AppScaffold(
                    timeText = {
                        TimeText {
                            text("Vellam")
                            separator()
                            time()
                        }
                    }
                ) {
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
            .setFilterByAuthorizedAccounts(autoSelect)
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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 44.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(36.dp)
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sign in on watch",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
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
                        shape = RoundedCornerShape(30.dp)
                    ) {
                        Text("Google Sign In")
                    }
                }
            }
        }
    }
}

@Composable
private fun DrinkDoneScreen(repo: FirestoreRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsPrefs = remember(context) {
        context.getSharedPreferences(WearSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val intakePrefs = remember(context) {
        context.getSharedPreferences(WearTodayIntakeStore.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var settings by remember { mutableStateOf(WearSettingsStore.read(context)) }
    var todayIntake by remember { mutableStateOf(WearTodayIntakeStore.read(context).totalMl) }
    val intakeAmountMl = settings.intakeAmountMl.coerceAtLeast(1)
    val dailyGoalMl = settings.dailyGoalMl.coerceAtLeast(1)
    val reminderInterval = WatchReminderScheduler.sanitizeInterval(settings.reminderIntervalMins)
    val progress = (todayIntake / dailyGoalMl.toFloat()).coerceIn(0f, 1f)

    var isSaving by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(settingsPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            settings = WearSettingsStore.read(context)
        }
        settingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settingsPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(intakePrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            todayIntake = WearTodayIntakeStore.read(context).totalMl
        }
        intakePrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { intakePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(reminderInterval) {
        WatchReminderScheduler.ensureScheduled(
            context = context,
            intervalMins = reminderInterval,
            replace = true
        )
    }

    LaunchedEffect(intakeAmountMl) {
        WearTileUpdater.request(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 12.dp, end = 12.dp, top = 42.dp, bottom = 6.dp)
    ) {
        Card(
            onClick = {},
            shape = RoundedCornerShape(44.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hydration Today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .padding(2.dp),
                        strokeWidth = CircularProgressIndicatorDefaults.largeStrokeWidth,
                        gapSize = 5.dp
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$todayIntake / $dailyGoalMl ml",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        EdgeButton(
            onClick = {
                if (isSaving) return@EdgeButton
                isSaving = true
                scope.launch {
                    try {
                        repo.addWaterIntake(intakeAmountMl)
                        todayIntake = WearTodayIntakeStore.addIntake(context, intakeAmountMl).totalMl
                        vibrateSwallow(context)
                        WearTileUpdater.request(context)
                    } catch (e: Exception) {
                        Log.e("WearDrink", "Failed to log water", e)
                    } finally {
                        isSaving = false
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            enabled = !isSaving,
            buttonSize = EdgeButtonSize.Medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(if (isSaving) "Saving..." else "I Drank $intakeAmountMl ml")
        }
    }
}

fun vibrateSwallow(context: Context) = com.hora.vellam.core.HapticManager.vibrateSwallow(context)

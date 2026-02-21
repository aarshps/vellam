package com.hora.vellam.wear

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.hora.vellam.core.auth.AuthManager
import com.hora.vellam.core.data.FirestoreRepository
import com.hora.vellam.core.data.UserSettings
import com.hora.vellam.core.data.WaterIntake
import com.hora.vellam.wear.ui.theme.VellamWearTheme
import kotlinx.coroutines.launch

private enum class WearTab {
    HOME,
    HISTORY,
    SETTINGS
}

private data class HistoryUiRow(
    val id: String,
    val amountLabel: String,
    val timeLabel: String,
    val dateHeader: String?
)

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
                    WearApp(firestoreRepository)
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
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                googleCredential.idToken
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Vellam",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sign in to sync with phone",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(14.dp))
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
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text("Google Sign In")
                }
            }
        }
    }
}

@Composable
private fun WearApp(repo: FirestoreRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val todayIntakeFlow = remember(repo) { repo.getTodayIntake() }
    val settingsFlow = remember(repo) { repo.getSettings() }
    val historyFlow = remember(repo) { repo.getHistory(limit = 60) }

    val intake by todayIntakeFlow.collectAsStateWithLifecycle(initialValue = 0)
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var activeTab by rememberSaveable { mutableStateOf(WearTab.HOME) }
    var isSavingIntake by rememberSaveable { mutableStateOf(false) }

    AppScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Crossfade(
                targetState = activeTab,
                animationSpec = tween(durationMillis = 220),
                label = "WearTabTransition"
            ) { tab ->
                when (tab) {
                    WearTab.HOME -> {
                        HomeScreen(
                            intake = intake,
                            settings = settings,
                            isSavingIntake = isSavingIntake,
                            onDrink = {
                                if (!isSavingIntake) {
                                    isSavingIntake = true
                                    scope.launch {
                                        try {
                                            repo.addWaterIntake(settings.intakeAmountMl)
                                            vibrateSwallow(context)
                                        } finally {
                                            isSavingIntake = false
                                        }
                                    }
                                }
                            },
                            onOpenHistory = {
                                activeTab = WearTab.HISTORY
                                vibrateSmall(context)
                            },
                            onOpenSettings = {
                                activeTab = WearTab.SETTINGS
                                vibrateSmall(context)
                            }
                        )
                    }

                    WearTab.HISTORY -> {
                        HistoryScreen(
                            history = history,
                            onDelete = { id ->
                                scope.launch {
                                    repo.deleteIntake(id)
                                    vibrateSmall(context)
                                }
                            }
                        )
                    }

                    WearTab.SETTINGS -> {
                        SettingsScreen(settings = settings)
                    }
                }
            }

            BottomTabBar(
                activeTab = activeTab,
                onTabSelected = { tab ->
                    if (tab != activeTab) {
                        activeTab = tab
                        vibrateSmall(context)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    intake: Int,
    settings: UserSettings,
    isSavingIntake: Boolean,
    onDrink: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val safeGoal = settings.dailyGoalMl.coerceAtLeast(1)
    val progress = (intake.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "HydrationProgress"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "DrinkButtonScale"
    )

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp, start = 10.dp, end = 10.dp)
    ) {
        item {
            ListHeader {
                Text(
                    text = "Hydration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f))
                    .padding(horizontal = 10.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(132.dp),
                            strokeWidth = 12.dp,
                            colors = ProgressIndicatorDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$intake",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "/ $safeGoal ml",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${(animatedProgress * 100f).toInt()}% complete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDrink,
                enabled = !isSavingIntake,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSavingIntake) "Saving..." else "Drink ${settings.intakeAmountMl} ml")
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("History", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Settings", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    history: List<WaterIntake>,
    onDelete: (String) -> Unit
) {
    val historyRows = remember(history) {
        val zone = java.time.ZoneId.systemDefault()
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)

        var previousDate: java.time.LocalDate? = null
        history.map { entry ->
            val dateTime = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(entry.timestamp.seconds, entry.timestamp.nanoseconds.toLong()),
                zone
            )
            val date = dateTime.toLocalDate()
            val header = if (date != previousDate) {
                previousDate = date
                when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(dateFormatter)
                }
            } else {
                null
            }
            HistoryUiRow(
                id = entry.id,
                amountLabel = "${entry.amountMl} ml",
                timeLabel = dateTime.format(timeFormatter),
                dateHeader = header
            )
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp, start = 8.dp, end = 8.dp)
    ) {
        item {
            ListHeader {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (historyRows.isEmpty()) {
            item {
                Text(
                    text = "No entries yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(historyRows.size) { index ->
                val row = historyRows[index]

                if (row.dateHeader != null) {
                    Text(
                        text = row.dateHeader,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                Button(
                    onClick = { onDelete(row.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.amountLabel, style = MaterialTheme.typography.labelMedium)
                            Text(
                                row.timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: UserSettings) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp, start = 8.dp, end = 8.dp)
    ) {
        item {
            ListHeader {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            ExpressiveInfoCard(
                icon = Icons.Rounded.Notifications,
                title = "Daily Goal",
                value = "${settings.dailyGoalMl} ml"
            )
        }

        item {
            ExpressiveInfoCard(
                icon = Icons.Rounded.WaterDrop,
                title = "Drink Amount",
                value = "${settings.intakeAmountMl} ml"
            )
        }

        item {
            ExpressiveInfoCard(
                icon = Icons.Rounded.Bedtime,
                title = "Sleep Window",
                value = "${settings.sleepStartTime} - ${settings.sleepEndTime}"
            )
        }

        item {
            ExpressiveInfoCard(
                icon = Icons.Rounded.Settings,
                title = "Theme",
                value = when (settings.appTheme) {
                    1 -> "Light"
                    2 -> "Dark"
                    else -> "System"
                }
            )
        }
    }
}

@Composable
private fun ExpressiveInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BottomTabBar(
    activeTab: WearTab,
    onTabSelected: (WearTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomTabButton(
            tab = WearTab.HOME,
            activeTab = activeTab,
            label = "Home",
            icon = Icons.Rounded.Home,
            onTabSelected = onTabSelected
        )
        BottomTabButton(
            tab = WearTab.HISTORY,
            activeTab = activeTab,
            label = "Logs",
            icon = Icons.Rounded.History,
            onTabSelected = onTabSelected
        )
        BottomTabButton(
            tab = WearTab.SETTINGS,
            activeTab = activeTab,
            label = "Prefs",
            icon = Icons.Rounded.Settings,
            onTabSelected = onTabSelected
        )
    }
}

@Composable
private fun BottomTabButton(
    tab: WearTab,
    activeTab: WearTab,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onTabSelected: (WearTab) -> Unit
) {
    val selected = tab == activeTab

    Button(
        onClick = { onTabSelected(tab) },
        modifier = Modifier
            .weight(1f)
            .height(42.dp),
        shape = RoundedCornerShape(18.dp),
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        }
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

fun vibrateSmall(context: Context) = com.hora.vellam.core.HapticManager.vibrateSmall(context)

fun vibrateSwallow(context: Context) = com.hora.vellam.core.HapticManager.vibrateSwallow(context)

package com.hora.vellam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hora.vellam.ui.theme.VellamTheme
import com.hora.vellam.core.PreferenceManager
import com.hora.vellam.core.auth.AuthManager
import com.hora.vellam.core.data.FirestoreRepository
import kotlinx.coroutines.launch
import android.media.MediaPlayer
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.hora.vellam.ui.components.SettingsGroup
import com.hora.vellam.ui.components.ExpressiveSettingsItem
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.hora.vellam.ui.components.TimePickerDialog
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var authManager: AuthManager
    private val firestoreRepository = FirestoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager(this)
        authManager = AuthManager(this)
        
        setupReminders()

        setContent {
            val isGoogleSans by preferenceManager.googleSansFlow.collectAsStateWithLifecycle(initialValue = true)
            val appTheme by preferenceManager.themeFlow.collectAsStateWithLifecycle(initialValue = 0)
            
            val darkTheme = when(appTheme) {
                1 -> false // Light
                2 -> true  // Dark
                else -> androidx.compose.foundation.isSystemInDarkTheme() // System
            }
            
            VellamTheme(
                useGoogleSans = isGoogleSans,
                darkTheme = darkTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val user by authManager.currentUser.collectAsStateWithLifecycle()
                    
                    if (user == null) {
                        LoginScreen(onLoginSuccess = { idToken ->
                            // AuthManager handles sign in, but we trigger it from UI
                        })
                    } else {
                        VellamApp(preferenceManager, firestoreRepository, authManager)
                    }
                }
            }
        }
    }

    private fun setupReminders() {
        val workManager = androidx.work.WorkManager.getInstance(this)
        val request = androidx.work.PeriodicWorkRequestBuilder<com.hora.vellam.core.WaterReminderWorker>(
            60, java.util.concurrent.TimeUnit.MINUTES
        ).build()
        
        workManager.enqueueUniquePeriodicWork(
            "water_reminder",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { AuthManager(context) }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true

                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId("1051691694392-mqhhd8k6ufp1jfntuihjid5bofm4rlfe.apps.googleusercontent.com") 
                            .setAutoSelectEnabled(false)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .setCredentialOptions(listOf(googleIdOption))
                            .build()

                        try {
                            val result = credentialManager.getCredential(
                                request = request,
                                context = context
                            )
                            
                            val credential = result.credential
                            if (credential is CustomCredential && 
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val idToken = googleIdTokenCredential.idToken
                                authManager.signInWithGoogle(idToken)
                                onLoginSuccess(idToken)
                                
                                // Sync with Watch
                                com.hora.vellam.core.WearAuthHelper.sendTokenToWear(context, idToken)
                            }
                        } catch (e: GoogleIdTokenParsingException) {
                            Log.e("Login", "Invalid Google token payload", e)
                            isLoading = false
                        } catch (e: Exception) {
                            Log.e("Login", "Sign in failed", e)
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
fun VellamApp(
    prefs: PreferenceManager, 
    repo: FirestoreRepository,
    authManager: AuthManager
) {
    var currentTab by remember { mutableStateOf(0) } // 0=Home, 1=Settings, 2=History
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsFlow = remember(repo) { repo.getSettings() }
    
    val interval by prefs.intervalFlow.collectAsStateWithLifecycle(initialValue = 60)
    val dailyGoal by prefs.dailyGoalFlow.collectAsStateWithLifecycle(initialValue = 2000)
    val intakeAmount by prefs.intakeAmountFlow.collectAsStateWithLifecycle(initialValue = 250)
    val sleepStart by prefs.sleepStartFlow.collectAsStateWithLifecycle(initialValue = "22:00")
    val sleepEnd by prefs.sleepEndFlow.collectAsStateWithLifecycle(initialValue = "07:00")
    val isGoogleSans by prefs.googleSansFlow.collectAsStateWithLifecycle(initialValue = true)
    val appTheme by prefs.themeFlow.collectAsStateWithLifecycle(initialValue = 0)

    val firestoreSettings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val localSettings = remember(interval, dailyGoal, intakeAmount, sleepStart, sleepEnd, isGoogleSans, appTheme) {
        com.hora.vellam.core.data.UserSettings(
            dailyGoalMl = dailyGoal,
            intakeAmountMl = intakeAmount,
            reminderIntervalMins = interval,
            sleepStartTime = sleepStart,
            sleepEndTime = sleepEnd,
            useGoogleSans = isGoogleSans,
            appTheme = appTheme
        )
    }
    var observedRemoteSettings by remember { mutableStateOf<com.hora.vellam.core.data.UserSettings?>(null) }

    // Keep phone settings in sync without write loops or startup churn.
    LaunchedEffect(localSettings, firestoreSettings) {
        val remote = firestoreSettings ?: return@LaunchedEffect

        // Remote changed: mirror it locally and skip immediate pushback.
        if (remote != observedRemoteSettings) {
            observedRemoteSettings = remote
            if (remote != localSettings) {
                prefs.applyRemoteSettings(remote)
            }
            return@LaunchedEffect
        }

        // Local changed: push to cloud (debounced) only when different from current remote.
        if (remote != localSettings) {
            // Debounce rapid UI updates (e.g. slider drags) into one network write.
            kotlinx.coroutines.delay(350)
            if (firestoreSettings == remote && remote != localSettings) {
                repo.updateSettings(localSettings)
            }
        }
    }

    fun navigateTo(tab: Int) {
        if (currentTab != tab) {
            currentTab = tab
            vibrateSmall(context)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { navigateTo(0) },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { navigateTo(1) },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            androidx.compose.animation.Crossfade(
                targetState = currentTab,
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(prefs, repo)
                    1 -> SettingsScreen(prefs, authManager, onNavigateHistory = { navigateTo(2) })
                    2 -> HistoryScreen(repo, onBack = { navigateTo(1) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(prefs: PreferenceManager, repo: FirestoreRepository) {
    val todayIntakeFlow = remember(repo) { repo.getTodayIntake() }
    val dailyTotal by todayIntakeFlow.collectAsStateWithLifecycle(initialValue = 0)
    val dailyGoal by prefs.dailyGoalFlow.collectAsStateWithLifecycle(initialValue = 2000)
    val intakeAmount by prefs.intakeAmountFlow.collectAsStateWithLifecycle(initialValue = 250)
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Animations
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (dailyTotal / dailyGoal.toFloat()).coerceIn(0f, 1f),
        label = "ProgressAnimation",
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    )

    // Button Interaction
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "ButtonScale"
    )

    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                kotlinx.coroutines.delay(200)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background for progress
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
                
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(320.dp),
                    strokeWidth = 32.dp, // Thicker stroke
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.hora.vellam.core.R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape), // Rounded Logo
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Daily Goal", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$dailyTotal", 
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 64.sp // Bigger
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "/ $dailyGoal ml", 
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            Button(
                onClick = { 
                    vibrateSwallow(context)
                    scope.launch {
                        repo.addWaterIntake(intakeAmount)
                    }
                },
                modifier = Modifier
                    .height(88.dp) // Taller button
                    .fillMaxWidth(0.85f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = MaterialTheme.shapes.extraLarge, // Max Rounded
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                interactionSource = interactionSource
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("Drink ${intakeAmount}ml", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
fun SettingsScreen(
    prefs: PreferenceManager, 
    authManager: AuthManager,
    onNavigateHistory: () -> Unit
) {
    val interval by prefs.intervalFlow.collectAsStateWithLifecycle(initialValue = 60)
    val dailyGoal by prefs.dailyGoalFlow.collectAsStateWithLifecycle(initialValue = 2000)
    val intakeAmount by prefs.intakeAmountFlow.collectAsStateWithLifecycle(initialValue = 250)
    val sleepStart by prefs.sleepStartFlow.collectAsStateWithLifecycle(initialValue = "22:00")
    val sleepEnd by prefs.sleepEndFlow.collectAsStateWithLifecycle(initialValue = "07:00")
    val isGoogleSans by prefs.googleSansFlow.collectAsStateWithLifecycle(initialValue = true)
    val appTheme by prefs.themeFlow.collectAsStateWithLifecycle(initialValue = 0)
    
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var intervalDraft by remember(interval) { mutableStateOf(interval.toFloat()) }
    var dailyGoalDraft by remember(dailyGoal) { mutableStateOf(dailyGoal.toFloat()) }
    var intakeAmountDraft by remember(intakeAmount) { mutableStateOf(intakeAmount.toFloat()) }
    
    // Time Picker State
    var showTimePicker by remember { mutableStateOf(false) }
    var isPickingStartTime by remember { mutableStateOf(true) }
    
    fun formatTime(hour: Int, min: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, min)
    }

    if (showTimePicker) {
        @OptIn(ExperimentalMaterial3Api::class)
        TimePickerDialog(
            title = if (isPickingStartTime) "Sleep Start Time" else "Sleep End Time",
            onDismissRequest = { showTimePicker = false },
            onConfirm = { state ->
                val timeStr = formatTime(state.hour, state.minute)
                scope.launch {
                    if (isPickingStartTime) {
                        prefs.setSleepTimes(start = timeStr, end = sleepEnd)
                        isPickingStartTime = false
                    } else {
                        prefs.setSleepTimes(start = sleepStart, end = timeStr)
                        showTimePicker = false
                    }
                }
            },
            content = { state ->
                TimePicker(state = state)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Settings", 
            style = MaterialTheme.typography.displaySmall, 
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                com.hora.vellam.ui.components.SettingsGroup(title = "Reminders") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Interval: $interval mins", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = intervalDraft,
                            onValueChange = { 
                                val snapped = it.roundToInt().coerceIn(15, 120)
                                intervalDraft = snapped.toFloat()
                            },
                            onValueChangeFinished = {
                                val newInt = intervalDraft.roundToInt()
                                if (newInt != interval) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch { prefs.setInterval(newInt) }
                                }
                            },
                            valueRange = 15f..120f
                        )
                    }
                    
                    com.hora.vellam.ui.components.ExpressiveSettingsItem(
                        title = "Sleep Schedule",
                        subtitle = "$sleepStart - $sleepEnd",
                        onClick = { 
                            isPickingStartTime = true
                            showTimePicker = true 
                        }
                    )
                }
            }

            item {
                com.hora.vellam.ui.components.SettingsGroup(title = "Hydration") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Daily Goal: $dailyGoal ml", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = dailyGoalDraft,
                            onValueChange = { 
                                val snapped = ((it / 100f).roundToInt() * 100).coerceIn(1000, 4000)
                                dailyGoalDraft = snapped.toFloat()
                            },
                            onValueChangeFinished = {
                                val newInt = dailyGoalDraft.roundToInt()
                                if (newInt != dailyGoal) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch { prefs.setDailyGoal(newInt) }
                                }
                            },
                            valueRange = 1000f..4000f,
                            steps = 29 // (4000-1000)/100 = 30 steps -> 29 intermediate
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Drink Amount: $intakeAmount ml", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = intakeAmountDraft,
                            onValueChange = { 
                                val snapped = ((it / 50f).roundToInt() * 50).coerceIn(50, 500)
                                intakeAmountDraft = snapped.toFloat()
                            },
                            onValueChangeFinished = {
                                val newInt = intakeAmountDraft.roundToInt()
                                if (newInt != intakeAmount) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch { prefs.setIntakeAmount(newInt) }
                                }
                            },
                            valueRange = 50f..500f,
                            steps = 8 // (500-50)/50 = 9 steps -> 8 intermediate
                        )
                    }
                }
            }

            item {
                com.hora.vellam.ui.components.SettingsGroup(title = "Appearance") {
                    // Theme Selector
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Theme", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("System" to 0, "Light" to 1, "Dark" to 2).forEach { (label, value) ->
                                val selected = (value == appTheme)
                                FilterChip(
                                    selected = selected,
                                    onClick = { scope.launch { prefs.setAppTheme(value) } },
                                    label = { Text(label) },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = CircleShape // Expressive pill shape
                                )
                            }
                        }
                    }

                    com.hora.vellam.ui.components.ExpressiveSettingsItem(
                        title = "Use Google Sans",
                        subtitle = "Toggle premium typography",
                        onClick = { scope.launch { prefs.setGoogleSans(!isGoogleSans) } },
                        trailing = {
                            Switch(
                                checked = isGoogleSans,
                                onCheckedChange = { scope.launch { prefs.setGoogleSans(it) } }
                            )
                        }
                    )
                }
            }

            item {
                com.hora.vellam.ui.components.SettingsGroup(title = "Data") {
                    com.hora.vellam.ui.components.ExpressiveSettingsItem(
                        title = "Hydration History",
                        subtitle = "View your logs",
                        onClick = onNavigateHistory
                    )
                }
            }

            item {
                com.hora.vellam.ui.components.SettingsGroup(title = "General") {
                    com.hora.vellam.ui.components.ExpressiveSettingsItem(
                        title = "Reset Settings",
                        subtitle = "Restore defaults",
                        onClick = { 
                             scope.launch {
                                 prefs.resetAllSettings()
                             }
                        }
                    )
                }
            }

            item {
                 // Account Section (Sign Out)
                 com.hora.vellam.ui.components.SettingsGroup(title = "Account") {
                     com.hora.vellam.ui.components.ExpressiveSettingsItem(
                        title = "Sign Out",
                        subtitle = "Disconnect account",
                        onClick = { 
                            scope.launch {
                                authManager.signOut() 
                            }
                        },
                        // Reddish Tint
                        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                        iconContentColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                 }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repo: FirestoreRepository, onBack: () -> Unit) {
    val historyFlow = remember(repo) { repo.getHistory() }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val groupedHistory = remember(history) {
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)
        history
            .groupBy {
                java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(it.timestamp.seconds, it.timestamp.nanoseconds.toLong()),
                    java.time.ZoneId.systemDefault()
                ).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
            .map { (date, entries) ->
                val dateLabel = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                }
                val rows = entries.map { entry ->
                    val timestamp = java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochSecond(entry.timestamp.seconds, entry.timestamp.nanoseconds.toLong()),
                        java.time.ZoneId.systemDefault()
                    )
                    val timeStr = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(timestamp)
                    Triple(entry.id, "${entry.amountMl} ml", timeStr)
                }
                dateLabel to rows
            }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                kotlinx.coroutines.delay(200)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
         item {
            Spacer(modifier = Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "History",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

            groupedHistory.forEach { (dateLabel, rows) ->
                item {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                    )
                }

                items(rows, key = { it.first }) { row ->
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                         com.hora.vellam.ui.components.ExpressiveSettingsItem(
                            icon = Icons.Rounded.WaterDrop,
                            title = row.second,
                            subtitle = row.third,
                            onClick = {},
                            trailing = {
                                IconButton(onClick = {
                                    scope.launch {
                                        repo.deleteIntake(row.first)
                                    }
                                }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            },
                             colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

fun vibrateSmall(context: Context) = com.hora.vellam.core.HapticManager.vibrateSmall(context)

fun vibrateSwallow(context: Context) = com.hora.vellam.core.HapticManager.vibrateSwallow(context)

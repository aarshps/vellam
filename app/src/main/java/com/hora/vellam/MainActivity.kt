package com.hora.vellam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.hora.vellam.ui.components.ExpressiveSectionHeader
import com.hora.vellam.ui.components.ExpressiveSettingsItem
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

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
            VellamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val user by authManager.currentUser.collectAsState()
                    
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
    var isLoading by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = true
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                 scope.launch {
                     try {
                         authManager.signInWithGoogle(token)
                     } catch (e: Exception) {
                         isLoading = false
                     }
                 }
            }
        } catch (e: ApiException) {
            Log.w("Login", "Google sign in failed", e)
            isLoading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("YOUR_WEB_CLIENT_ID_HERE") // Placeholder
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    launcher.launch(googleSignInClient.signInIntent)
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
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { navigateTo(1) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> HomeScreen(prefs, repo)
                1 -> SettingsScreen(prefs, authManager, onNavigateHistory = { navigateTo(2) })
                2 -> HistoryScreen(repo, onBack = { navigateTo(1) })
            }
        }
    }
}

@Composable
fun HomeScreen(prefs: PreferenceManager, repo: FirestoreRepository) {
    val dailyTotal by repo.getTodayIntake().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (dailyTotal / 2000f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(280.dp),
                    strokeWidth = 12.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.hora.vellam.core.R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Vellam", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$dailyTotal ml", 
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("of 2000 ml", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { 
                    vibrateSwallow(context)
                    scope.launch {
                        repo.addWaterIntake(250)
                    }
                },
                modifier = Modifier.height(64.dp).width(200.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Drink 250ml")
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
    val interval by prefs.intervalFlow.collectAsState(initial = 60)
    val sleepStart by prefs.sleepStartFlow.collectAsState(initial = "22:00")
    val sleepEnd by prefs.sleepEndFlow.collectAsState(initial = "07:00")
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
             Spacer(modifier = Modifier.height(24.dp))
             Text(
                 "Settings", 
                 style = MaterialTheme.typography.displaySmall, 
                 modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)
             )
        }

        item {
            ExpressiveSectionHeader("Preferences")
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Interval: $interval mins")
                Slider(
                    value = interval.toFloat(),
                    onValueChange = { scope.launch { prefs.setInterval(it.toInt()) } },
                    valueRange = 15f..120f
                )
            }
            ExpressiveSettingsItem(
                icon = Icons.Filled.Bedtime,
                title = "Sleep Schedule",
                subtitle = "$sleepStart - $sleepEnd",
                onClick = { /* TODO: Time Picker */ }
            )
        }
        
        item {
             ExpressiveSectionHeader("Data")
             ExpressiveSettingsItem(
                icon = Icons.Filled.History,
                title = "Hydration History",
                subtitle = "View your logs",
                onClick = onNavigateHistory
            )
             ExpressiveSettingsItem(
                icon = Icons.Filled.ArrowBack,
                title = "Sign Out",
                subtitle = "Disconnect account",
                onClick = { authManager.signOut() }
            )
        }
    }
}

@Composable
fun HistoryScreen(repo: FirestoreRepository, onBack: () -> Unit) {
    val history by repo.getHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
         item {
            Spacer(modifier = Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "History",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        items(history) { item ->
            val date = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(item.timestamp.seconds, item.timestamp.nanoseconds.toLong()),
                java.time.ZoneId.systemDefault()
            )
            val timeStr = java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm").format(date)
            
            ExpressiveSettingsItem(
                icon = Icons.Filled.WaterDrop,
                title = "${item.amountMl} ml",
                subtitle = timeStr,
                onClick = {},
                trailing = {
                    IconButton(onClick = {
                        scope.launch {
                            repo.deleteIntake(item.id)
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}

fun vibrateSmall(context: Context) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
        vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(10)
    }
}

fun vibrateSwallow(context: Context) {
    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 150, 80, 100, 120), intArrayOf(0, 50, 0, 80, 0, 120), -1))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(200)
    }
}

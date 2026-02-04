package com.hora.vellam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hora.vellam.ui.theme.VellamTheme
import com.hora.vellam.core.PreferenceManager
import kotlinx.coroutines.launch
import android.media.MediaPlayer
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context

class MainActivity : ComponentActivity() {
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager(this)
        
        setupReminders()

        setContent {
            VellamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VellamApp(preferenceManager)
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
fun VellamApp(prefs: PreferenceManager) {
    var currentTab by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { 
                        if (currentTab != 0) {
                            currentTab = 0
                            vibrateSmall(context)
                        }
                    },
                    icon = { Icon(androidx.compose.material.icons.Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { 
                        if (currentTab != 1) {
                            currentTab = 1
                            vibrateSmall(context)
                        }
                    },
                    icon = { Icon(androidx.compose.material.icons.Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (currentTab == 0) {
                HomeScreen(prefs)
            } else {
                SettingsScreen(prefs)
            }
        }
    }
}

@Composable
fun HomeScreen(prefs: PreferenceManager) {
    val intake by prefs.intakeFlow.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Hydration Progress Circle
        CircularProgressIndicator(
            progress = { (intake / 2000f).coerceIn(0f, 1f) },
            modifier = Modifier.size(240.dp),
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
            Text("Vellam", style = MaterialTheme.typography.labelLarge)
            Text(
                "$intake ml", 
                style = MaterialTheme.typography.displayMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text("of 2000 ml", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        prefs.updateIntake(250)
                        vibrateSwallow(context)
                    }
                },
                modifier = Modifier.height(56.dp).width(160.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Drink 250ml")
            }
        }
    }
}

@Composable
fun SettingsScreen(prefs: PreferenceManager) {
    val interval by prefs.intervalFlow.collectAsState(initial = 60)
    val sleepStart by prefs.sleepStartFlow.collectAsState(initial = "22:00")
    val sleepEnd by prefs.sleepEndFlow.collectAsState(initial = "07:00")
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth()
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Reminder Interval: $interval mins")
        Slider(
            value = interval.toFloat(),
            onValueChange = { 
                scope.launch { 
                    prefs.setInterval(it.toInt())
                }
            },
            onValueChangeFinished = {
                vibrateSmall(context)
            },
            valueRange = 15f..240f,
            steps = 15
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sleep Time", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("From: $sleepStart", modifier = Modifier.weight(1f))
            Text("To: $sleepEnd", modifier = Modifier.weight(1f))
        }
    }
}

fun vibrateSmall(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    // Directly use modern API without checks
    vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
}

fun vibrateSwallow(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    // A "swallow" pattern: medium pulse, short gap, slightly stronger pulse
    val pattern = longArrayOf(0, 100, 150, 80, 100, 120)
    val amplitudes = intArrayOf(0, 50, 0, 80, 0, 120) // Increasing intensity
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
}

package com.hora.vellam.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.wear.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import com.hora.vellam.core.auth.AuthManager
import com.hora.vellam.core.data.FirestoreRepository
import com.hora.vellam.wear.ui.theme.VellamWearTheme
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.foundation.background
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.util.Log

import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private lateinit var authManager: AuthManager
    private val firestoreRepository = FirestoreRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        setContent {
            VellamWearTheme {
                val user by authManager.currentUser.collectAsState()
                
                if (user == null) {
                     // Try Silent Sign In
                     val context = androidx.compose.ui.platform.LocalContext.current
                     LaunchedEffect(Unit) {
                         val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                             .requestIdToken("1051691694392-mqhhd8k6ufp1jfntuihjid5bofm4rlfe.apps.googleusercontent.com")
                             .requestEmail()
                             .build()
                         val googleSignInClient = GoogleSignIn.getClient(context, gso)
                         googleSignInClient.silentSignIn().addOnCompleteListener { task ->
                             if (task.isSuccessful) {
                                 val account = task.result
                                 account?.idToken?.let { token ->
                                     launch {
                                         try {
                                             authManager.signInWithGoogle(token)
                                         } catch (e: Exception) {
                                             Log.e("WearSilentAuth", "Silent login failed", e)
                                         }
                                     }
                                 }
                             }
                         }
                     }
                     LoginScreen(authManager)
                } else {
                    WearApp(firestoreRepository)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(authManager: AuthManager) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
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
                         Log.e("WearLogin", "Firebase auth failed", e)
                         isLoading = false
                     }
                 }
            }
        } catch (e: ApiException) {
             Log.e("WearLogin", "Google sign in failed", e)
             isLoading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Sign In Required", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken("1051691694392-mqhhd8k6ufp1jfntuihjid5bofm4rlfe.apps.googleusercontent.com") // Use the same ID as phone
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        launcher.launch(googleSignInClient.signInIntent)
                    }
                ) {
                    Text("Google Sign In")
                }
            }
        }
    }
}

@OptIn(androidx.wear.compose.material3.ExperimentalWearMaterial3Api::class)
@Composable
fun WearApp(repo: FirestoreRepository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val intake by repo.getTodayIntake().collectAsState(initial = 0)
    val history by repo.getHistory().collectAsState(initial = emptyList())
    val settings by repo.getSettings().collectAsState(initial = com.hora.vellam.core.data.UserSettings())
    val scope = rememberCoroutineScope()
    
    // Pager for Navigation (0=Main, 1=History, 2=Settings)
    val pagerState = androidx.wear.compose.foundation.pager.rememberPagerState(pageCount = { 3 })

    // Haptic feedback on page change
    LaunchedEffect(pagerState.currentPage) {
        vibrateSmall(context)
    }

    AppScaffold {
        androidx.wear.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            PageWrapper(
                page = page,
                intake = intake,
                goal = settings.dailyGoalMl.toFloat(),
                intakeAmount = settings.intakeAmountMl,
                history = history,
                settings = settings,
                repo = repo,
                scope = scope,
                context = context
            )
        }
    }
}

@Composable
fun PageWrapper(
    page: Int,
    intake: Int,
    goal: Float,
    intakeAmount: Int,
    history: List<com.hora.vellam.core.data.WaterIntake>,
    settings: com.hora.vellam.core.data.UserSettings,
    repo: FirestoreRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    when (page) {
        0 -> MainScreen(
            intake = intake, 
            goal = goal,
            intakeAmount = intakeAmount,
            repo = repo,
            scope = scope,
            context = context
        )
        1 -> HistoryScreen(
            history = history, 
            onDelete = { id ->
                scope.launch {
                    repo.deleteIntake(id)
                    vibrateSmall(context)
                }
            }
        )
        2 -> SettingsScreen(settings = settings)
    }
}



@Composable
fun DrinkButton(intakeAmount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioHighBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "scale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth(0.92f)
            .height(76.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = 48.dp, 
            topEnd = 48.dp,
            bottomStart = 28.dp, 
            bottomEnd = 28.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = "Drink",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Drink ${intakeAmount}ml", 
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun MainScreen(
    intake: Int, 
    goal: Float, 
    intakeAmount: Int, 
    repo: FirestoreRepository, 
    scope: kotlinx.coroutines.CoroutineScope, 
    context: android.content.Context
) {
    val progress = (intake / goal).coerceIn(0f, 1f)
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress,
        animationSpec = androidx.compose.animation.core.tween(1000)
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // M3 Circular Progress - Edge Hugging Arc
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            startAngle = 140f, 
            endAngle = 40f,
            strokeWidth = 12.dp, 
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "$intake",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "of ${goal.toInt()}ml",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
            )
        }

        // Edge-hugging broad button (M3E Style)
        DrinkButton(
            intakeAmount = intakeAmount,
            onClick = {
                scope.launch {
                    repo.addWaterIntake(intakeAmount)
                    vibrateSwallow(context)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
fun HistoryScreen(history: List<com.hora.vellam.core.data.WaterIntake>, onDelete: (String) -> Unit) {
    androidx.wear.compose.foundation.lazy.ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp, start = 8.dp, end = 8.dp)
    ) {
        item {
            ListHeader {
                Text("History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        if (history.isEmpty()) {
            item {
                Text(
                    "No history", 
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(history.size) { index ->
                val entry = history[index]
                val date = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(entry.timestamp.seconds, entry.timestamp.nanoseconds.toLong()),
                    java.time.ZoneId.systemDefault()
                )
                val timeStr = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(date)

                Button(
                    onClick = { onDelete(entry.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${entry.amountMl} ml", style = MaterialTheme.typography.labelMedium)
                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(settings: com.hora.vellam.core.data.UserSettings) {
    androidx.wear.compose.foundation.lazy.ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        anchorType = androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType.ItemStart,
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp, start = 8.dp, end = 8.dp)
    ) {
        item {
            ListHeader {
                Text("Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            Button(
                onClick = { /* Handled on Phone */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Goal", style = MaterialTheme.typography.labelMedium)
                        Text("${settings.dailyGoalMl} ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { /* Handled on Phone */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.WaterDrop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Intake", style = MaterialTheme.typography.labelMedium)
                        Text("${settings.intakeAmountMl} ml", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { /* Handled on Phone */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Sleep", style = MaterialTheme.typography.labelMedium)
                        Text("${settings.sleepStartTime} - ${settings.sleepEndTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
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
    
    // Expressive "Liquid" Waveform: 3 quick pulses of increasing intensity
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
         vibrator.vibrate(VibrationEffect.createWaveform(
             longArrayOf(0, 40, 50, 40, 50, 60), 
             intArrayOf(0, 100, 0, 180, 0, 255), 
             -1
         ))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(200)
    }
}


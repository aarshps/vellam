package com.hora.vellam.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.wear.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hora.vellam.core.PreferenceManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager(this)

        setContent {
            WearApp(preferenceManager)
        }
    }
}

@Composable
fun WearApp(prefs: PreferenceManager) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val intake by prefs.intakeFlow.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.hora.vellam.core.R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = androidx.compose.ui.graphics.Color(0xFF64B5F6)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$intake",
                    style = MaterialTheme.typography.display1,
                    color = androidx.compose.ui.graphics.Color(0xFF64B5F6)
                )
                Text(
                    text = "ml",
                    style = MaterialTheme.typography.caption2
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            prefs.updateIntake(250)
                            vibrateSwallow(context)
                        }
                    },
                    modifier = Modifier.size(ButtonDefaults.LargeButtonSize)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = "Add Water"
                    )
                }
            }
        }
    }
}

fun vibrateSwallow(context: android.content.Context) {
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
    // A "swallow" pattern: medium pulse, short gap, slightly stronger pulse
    val pattern = longArrayOf(0, 100, 150, 80, 100, 120)
    val amplitudes = intArrayOf(0, 50, 0, 80, 0, 100)
    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, amplitudes, -1))
}

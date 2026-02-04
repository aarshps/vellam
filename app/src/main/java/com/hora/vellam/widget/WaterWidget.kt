package com.hora.vellam.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.action.*
import androidx.glance.unit.ColorProvider
import com.hora.vellam.core.PreferenceManager
import com.hora.vellam.ui.theme.WaterBlue
import kotlinx.coroutines.flow.first
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.ActionParameters
import androidx.glance.GlanceId

class WaterWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = PreferenceManager(context)
        
        provideContent {
            val intake = 0 // In a real widget we'd collect from flow or use GlanceStateDefinition
            
            GlanceTheme {
                Content(intake)
            }
        }
    }

    @Composable
    private fun Content(intake: Int) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(androidx.glance.unit.ColorProvider(WaterBlue))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = androidx.glance.ImageProvider(com.hora.vellam.core.R.drawable.ic_logo),
                contentDescription = null,
                modifier = GlanceModifier.size(32.dp),
                colorFilter = androidx.glance.ColorFilter.tint(androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Vellam",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorProvider(androidx.compose.ui.graphics.Color.White))
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "$intake ml",
                style = TextStyle(fontSize = 20.sp, color = ColorProvider(androidx.compose.ui.graphics.Color.White))
            )
            Button(
                text = "+ 250",
                onClick = actionRunCallback<AddWaterCallback>()
            )
        }
    }
}

class AddWaterCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = PreferenceManager(context)
        prefs.updateIntake(250)
        WaterWidget().updateAll(context)
    }
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterWidget()
}

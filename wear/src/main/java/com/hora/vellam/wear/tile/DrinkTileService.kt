package com.hora.vellam.wear.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.material3.CircularProgressIndicatorDefaults
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.circularProgressIndicator
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hora.vellam.wear.TileDrinkActivity
import com.hora.vellam.wear.WearSettingsStore
import com.hora.vellam.wear.WearTodayIntakeStore

class DrinkTileService : TileService() {
    companion object {
        private const val RESOURCES_VERSION = "2"
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val settings = WearSettingsStore.read(applicationContext)
        val intakeAmountMl = settings.intakeAmountMl.coerceAtLeast(1)
        val dailyGoalMl = settings.dailyGoalMl.coerceAtLeast(1)
        val todayTotalMl = WearTodayIntakeStore.read(applicationContext).totalMl.coerceAtLeast(0)
        val progress = (todayTotalMl / dailyGoalMl.toFloat()).coerceIn(0f, 1f)
        val progressPercent = (progress * 100f).toInt()

        val drinkAction = clickable(
            ActionBuilders.launchAction(
                ComponentName(applicationContext, TileDrinkActivity::class.java)
            ),
            "drink_action"
        )

        val layout = materialScope(
            context = applicationContext,
            deviceConfiguration = requestParams.deviceConfiguration
        ) {
            primaryLayout(
                titleSlot = {
                    text(
                        text = LayoutString("Hydration Today"),
                        typography = Typography.TITLE_SMALL
                    )
                },
                mainSlot = {
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(expand())
                        .setHeight(expand())
                        .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                        .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                        .addContent(
                            circularProgressIndicator(
                                staticProgress = progress,
                                strokeWidth = CircularProgressIndicatorDefaults.LARGE_STROKE_WIDTH,
                                modifier = LayoutModifier.contentDescription("$progressPercent percent")
                            )
                        )
                        .addContent(
                            text(
                                text = LayoutString("$progressPercent%"),
                                typography = Typography.NUMERAL_MEDIUM
                            )
                        )
                        .build()
                },
                labelForBottomSlot = {
                    text(
                        text = LayoutString("$todayTotalMl / $dailyGoalMl ml"),
                        typography = Typography.BODY_SMALL
                    )
                },
                bottomSlot = {
                    textEdgeButton(onClick = drinkAction) {
                        text(text = LayoutString("I Drank $intakeAmountMl ml"))
                    }
                }
            )
        }

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }
}

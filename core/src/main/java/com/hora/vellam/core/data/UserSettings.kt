package com.hora.vellam.core.data

data class UserSettings(
    val dailyGoalMl: Int = 2000,
    val intakeAmountMl: Int = 250,
    val reminderIntervalMins: Int = 60,
    val sleepStartTime: String = "22:00",
    val sleepEndTime: String = "07:00",
    val useGoogleSans: Boolean = true,
    val appTheme: Int = 0 // 0=System, 1=Light, 2=Dark
)

package com.hora.vellam.core.data

import com.google.firebase.Timestamp

data class WaterIntake(
    val id: String = "",
    val amountMl: Int = 0,
    val timestamp: Timestamp = Timestamp.now()
)

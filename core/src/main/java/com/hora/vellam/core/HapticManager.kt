package com.hora.vellam.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Centralized haptic feedback manager for the Vellam app.
 * Defines app-wide vibration standards so every module uses the same subtle patterns.
 */
object HapticManager {

    // ── App-wide vibration standards ──────────────────────────────────────
    private const val SMALL_DURATION_MS = 10L
    private const val SMALL_AMPLITUDE = 40          // Gentle tick

    private val SWALLOW_TIMINGS = longArrayOf(0, 50, 80, 50, 80, 60)
    private val SWALLOW_AMPLITUDES = intArrayOf(0, 20, 0, 40, 0, 60)
    private const val SWALLOW_FALLBACK_MS = 100L    // Pre-O fallback
    private const val SMALL_COOLDOWN_MS = 40L
    private const val SWALLOW_COOLDOWN_MS = 120L

    @Volatile
    private var lastSmallAtMs = 0L

    @Volatile
    private var lastSwallowAtMs = 0L

    // ── Public API ───────────────────────────────────────────────────────

    /** Light single-pulse tap – used for navigation and minor interactions. */
    fun vibrateSmall(context: Context) {
        val vibrator = vibrator(context)
        if (!vibrator.hasVibrator() || !shouldVibrate(isSmall = true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(SMALL_DURATION_MS, SMALL_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(SMALL_DURATION_MS)
        }
    }

    /** Subtle multi-pulse "liquid swallow" wave – used when logging water intake. */
    fun vibrateSwallow(context: Context) {
        val vibrator = vibrator(context)
        if (!vibrator.hasVibrator() || !shouldVibrate(isSmall = false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(SWALLOW_TIMINGS, SWALLOW_AMPLITUDES, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(SWALLOW_FALLBACK_MS)
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private fun vibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun shouldVibrate(isSmall: Boolean): Boolean = synchronized(this) {
        val now = android.os.SystemClock.elapsedRealtime()
        val (last, cooldown) = if (isSmall) {
            lastSmallAtMs to SMALL_COOLDOWN_MS
        } else {
            lastSwallowAtMs to SWALLOW_COOLDOWN_MS
        }

        if (now - last < cooldown) return false

        if (isSmall) {
            lastSmallAtMs = now
        } else {
            lastSwallowAtMs = now
        }
        true
    }
}

package com.thanhvan.cotuongluyenky.service.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Lightweight Android 10+ haptic feedback helper (Section 19.6).
 */
class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun lightTap(enabled: Boolean) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(18L, 80))
    }

    fun illegalMoveBuzz(enabled: Boolean) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 28L, 35L, 28L), intArrayOf(0, 120, 0, 120), -1))
    }

    fun checkPulse(enabled: Boolean) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(40L, 140))
    }
}

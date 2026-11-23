package com.thanhvan.cotuongluyenky.service.power

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import com.thanhvan.cotuongluyenky.core.model.EngineResourceMode

data class DevicePowerSnapshot(
    val isPowerSaveMode: Boolean,
    val batteryPercent: Int,
    val isThermallyThrottled: Boolean,
    val statusNote: String?
) {
    val shouldThrottle: Boolean
        get() = isThermallyThrottled || isPowerSaveMode || batteryPercent in 1..15

    val throttleReason: String?
        get() = statusNote
}

/**
 * Monitors Android battery saver, low battery, and Android 10+ thermal status
 * to protect device temperature and battery life during engine search/analysis
 * (Sections 6.6, 25.4 & 26 — Phase 4).
 */
class DeviceThermalPowerMonitor(private val context: Context) {

    fun inspect(): DevicePowerSnapshot {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val isPowerSave = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)
        val batteryPct = runCatching {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        }.getOrDefault(100).coerceIn(0, 100)

        // minSdk = 29 guarantees PowerManager.currentThermalStatus is available on all supported devices
        val thermalStatus = runCatching {
            powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
        }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)

        val isWarmOrHot = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        val isLowBattery = batteryPct in 1..15

        val note = when {
            isWarmOrHot -> "Thiết bị đang ấm/nóng — tự động hạ mức tài nguyên máy cờ để bảo vệ pin và nhiệt độ."
            isPowerSave -> "Chế độ Tiết kiệm pin của hệ thống đang bật — máy cờ tự động chạy ở mức tiết kiệm."
            isLowBattery -> "Pin thiết bị còn $batteryPct% — tự động ưu tiên mức tiết kiệm năng lượng."
            else -> null
        }

        return DevicePowerSnapshot(
            isPowerSaveMode = isPowerSave,
            batteryPercent = batteryPct,
            isThermallyThrottled = isWarmOrHot,
            statusNote = note
        )
    }

    /**
     * Returns the safe effective [EngineResourceMode] respecting both user choice and device thermal/battery guard.
     */
    fun effectiveResourceMode(requested: EngineResourceMode, autoGuardEnabled: Boolean = true): EngineResourceMode {
        if (!autoGuardEnabled) return requested
        val snap = inspect()
        return if (snap.shouldThrottle) {
            EngineResourceMode.ECO
        } else {
            requested
        }
    }

    companion object {
        fun maxSafeThreads(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}

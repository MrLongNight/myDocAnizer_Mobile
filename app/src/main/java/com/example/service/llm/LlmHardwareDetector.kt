package com.example.service.llm

import android.app.ActivityManager
import android.content.Context
import com.example.model.DeviceHardwareInfo
import com.example.model.ModelCompatibilityLevel

object LlmHardwareDetector {

    fun detectDeviceHardware(context: Context): DeviceHardwareInfo {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)

            val totalGb = (memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
            val availGb = (memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
            val usedGb = (totalGb - availGb).coerceAtLeast(0f)
            val usagePct = if (totalGb > 0f) ((usedGb / totalGb) * 100).toInt() else 42
            val cores = Runtime.getRuntime().availableProcessors()
            val isLowRam = memoryInfo.lowMemory || totalGb < 3.0f

            val roundedTotal = ((totalGb * 10).toInt() / 10f).coerceAtLeast(1.0f)
            val roundedAvail = ((availGb * 10).toInt() / 10f).coerceAtLeast(0.5f)
            val roundedUsed = ((usedGb * 10).toInt() / 10f).coerceAtLeast(0.5f)

            DeviceHardwareInfo(
                totalRamGb = roundedTotal,
                availableRamGb = roundedAvail,
                usedRamGb = roundedUsed,
                ramUsagePercent = usagePct.coerceIn(5, 95),
                cpuCores = cores.coerceAtLeast(4),
                isLowRamDevice = isLowRam
            )
        } catch (e: Exception) {
            DeviceHardwareInfo(
                totalRamGb = 6.0f,
                availableRamGb = 3.5f,
                usedRamGb = 2.5f,
                ramUsagePercent = 42,
                cpuCores = 8,
                isLowRamDevice = false
            )
        }
    }

    fun calculateCompatibility(recommendedRamGb: Float, hw: DeviceHardwareInfo): ModelCompatibilityLevel {
        return hw.getCompatibility(recommendedRamGb)
    }
}

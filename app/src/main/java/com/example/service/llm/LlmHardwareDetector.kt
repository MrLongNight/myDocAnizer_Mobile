package com.example.service.llm

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.DeviceHardwareInfo
import com.example.model.LlmPreferenceMode
import com.example.model.ModelCompatibilityLevel
import com.example.service.HuggingFaceModelInfo
import java.io.File

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

            val soc = detectSocModel()
            val maxFreq = detectMaxCpuFreqMhz()
            val dotProd = detectArmDotProd()
            val vulkan = detectVulkanGpu(context)
            val benchScore = runCpuMicroBenchmark()

            DeviceHardwareInfo(
                totalRamGb = roundedTotal,
                availableRamGb = roundedAvail,
                usedRamGb = roundedUsed,
                ramUsagePercent = usagePct.coerceIn(5, 95),
                cpuCores = cores.coerceAtLeast(4),
                isLowRamDevice = isLowRam,
                socModel = soc,
                maxCpuFreqMhz = maxFreq,
                supportsArmDotProd = dotProd,
                hasVulkanGpu = vulkan,
                cpuBenchmarkScore = benchScore
            )
        } catch (e: Exception) {
            DeviceHardwareInfo(
                totalRamGb = 6.0f,
                availableRamGb = 3.5f,
                usedRamGb = 2.5f,
                ramUsagePercent = 42,
                cpuCores = 8,
                isLowRamDevice = false,
                socModel = detectSocModel(),
                maxCpuFreqMhz = 2000,
                supportsArmDotProd = true,
                hasVulkanGpu = true,
                cpuBenchmarkScore = 75
            )
        }
    }

    fun detectSocModel(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val soc = Build.SOC_MODEL
                if (!soc.isNullOrBlank() && soc != Build.UNKNOWN) return soc
            }
            val hw = Build.HARDWARE
            if (!hw.isNullOrBlank() && hw != Build.UNKNOWN) return hw
            val board = Build.BOARD
            if (!board.isNullOrBlank() && board != Build.UNKNOWN) return board
            "Standard ARMv8"
        } catch (_: Throwable) {
            "Standard ARMv8"
        }
    }

    fun detectMaxCpuFreqMhz(): Int {
        return try {
            var maxKhz = 0
            val cpuDir = File("/sys/devices/system/cpu")
            if (cpuDir.exists()) {
                val cpuFiles = cpuDir.listFiles { f -> f.isDirectory && f.name.matches(Regex("cpu[0-9]+")) }
                cpuFiles?.forEach { dir ->
                    val freqFile = File(dir, "cpufreq/cpuinfo_max_freq")
                    val scalingFile = File(dir, "cpufreq/scaling_max_freq")
                    val target = if (freqFile.exists()) freqFile else if (scalingFile.exists()) scalingFile else null
                    target?.let {
                        val khz = it.readText().trim().toIntOrNull() ?: 0
                        if (khz > maxKhz) maxKhz = khz
                    }
                }
            }
            if (maxKhz > 100000) maxKhz / 1000 else 2000
        } catch (_: Throwable) {
            2000
        }
    }

    fun detectArmDotProd(): Boolean {
        return try {
            val cpuInfo = File("/proc/cpuinfo")
            if (cpuInfo.exists()) {
                val text = cpuInfo.readText().lowercase()
                if (text.contains("asimddp") || text.contains("i8mm") || text.contains("dotprod")) {
                    return true
                }
            }
            Build.SUPPORTED_ABIS.any { it.contains("arm64") || it.contains("v8a") }
        } catch (_: Throwable) {
            true
        }
    }

    fun detectVulkanGpu(context: Context): Boolean {
        return try {
            val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            val hw = Build.HARDWARE.lowercase()
            hasFeature || hw.contains("qcom") || hw.contains("mali") || hw.contains("exynos") ||
                hw.contains("tensor") || hw.contains("kirin") || hw.contains("mediatek")
        } catch (_: Throwable) {
            true
        }
    }

    fun runCpuMicroBenchmark(): Int {
        return try {
            val start = System.currentTimeMillis()
            var ops = 0L
            var dummy = 1.000137
            while (System.currentTimeMillis() - start < 30) {
                for (i in 0 until 5000) {
                    dummy = (dummy * 1.0001) / 1.00009 + 0.00001
                }
                ops += 5000
            }
            val score = (ops / 25000L).toInt().coerceIn(25, 100)
            score
        } catch (_: Throwable) {
            75
        }
    }

    fun estimateTps(model: HuggingFaceModelInfo, hw: DeviceHardwareInfo): Float {
        val baseTps = when {
            model.recommendedRamGb <= 1.5f -> 28f
            model.recommendedRamGb <= 2.2f -> 20f
            model.recommendedRamGb <= 3.5f -> 14f
            model.recommendedRamGb <= 5.0f -> 8.5f
            model.recommendedRamGb <= 6.5f -> 5.5f
            else -> 3.0f
        }
        var factor = 1.0f
        if (hw.hasVulkanGpu) factor += 0.35f
        if (hw.supportsArmDotProd) factor += 0.20f
        factor *= (hw.cpuCores / 8.0f).coerceIn(0.7f, 1.3f)
        factor *= (hw.maxCpuFreqMhz / 2000f).coerceIn(0.6f, 1.4f)
        val ramFactor = (hw.totalRamGb / model.recommendedRamGb.coerceAtLeast(1.0f)).coerceIn(0.5f, 1.4f)
        factor *= ramFactor
        val benchFactor = (hw.cpuBenchmarkScore / 75f).coerceIn(0.7f, 1.3f)
        factor *= benchFactor

        val res = (baseTps * factor * 10).toInt() / 10f
        return res.coerceIn(0.5f, 60.0f)
    }

    fun calculateModelScoreAndReason(
        model: HuggingFaceModelInfo,
        hw: DeviceHardwareInfo,
        preference: LlmPreferenceMode,
        estimatedTps: Float
    ): Pair<Int, String> {
        val baseQuality = when {
            model.id.contains("qwen2.5-1.5b") -> 86
            model.id.contains("qwen2.5-3b") -> 93
            model.id.contains("llama-3.2-3b") -> 94
            model.id.contains("llama-3.1-8b") || model.id.contains("qwen2.5-7b") -> 98
            model.id.contains("gemma-2-2b") -> 90
            model.id.contains("smollm2-1.7b") -> 84
            model.id.contains("qwen2.5-0.5b") -> 74
            model.id.contains("smollm2-360m") -> 64
            model.id.contains("smollm2-135m") -> 52
            model.id.contains("danube3") -> 46
            model.recommendedRamGb >= 6.0f -> 95
            model.recommendedRamGb >= 3.5f -> 88
            model.recommendedRamGb >= 2.0f -> 78
            else -> 60
        }
        val dmsBonus = if (model.descriptionDe.contains("Rechnung", ignoreCase = true) ||
            model.name.contains("Instruct", ignoreCase = true) ||
            model.criteria.any { it.contains("Rechnung", ignoreCase = true) }) 4 else 0
        val qualityScore = (baseQuality + dmsBonus).coerceIn(20, 100)

        val speedScore = ((estimatedTps / 24f) * 100).toInt().coerceIn(15, 100)

        val fitMultiplier = when {
            model.recommendedRamGb > hw.totalRamGb + 0.5f -> 0.15f
            model.recommendedRamGb > hw.totalRamGb -> 0.45f
            model.recommendedRamGb > hw.availableRamGb + 2.0f -> 0.75f
            model.recommendedRamGb <= hw.availableRamGb -> 1.0f
            else -> 0.90f
        }

        val rawScore = (qualityScore * preference.qualityWeight) + (speedScore * preference.speedWeight)
        val finalScore = (rawScore * fitMultiplier).toInt().coerceIn(5, 100)

        val reason = when {
            fitMultiplier < 0.5f ->
                "Geringer Score ($finalScore/100): Modell benötigt ${model.ramBadge}, übersteigt jedoch die verbauten ${hw.totalRamGb} GB RAM deines Geräts."
            preference == LlmPreferenceMode.SPEED_FOCUS ->
                "Score $finalScore/100 (Geschwindigkeits-Fokus): ca. $estimatedTps TPS bei ${qualityScore}% Extraktionsgüte. Sehr schnelle Reaktionszeit."
            else ->
                "Score $finalScore/100 (Qualitäts-Fokus): ${qualityScore}% Sprach- & Extraktionspräzision bei stabilen $estimatedTps TPS optimal abgestimmt auf ${hw.totalRamGb} GB RAM."
        }

        return Pair(finalScore, reason)
    }

    fun applyHardwareRecommendations(
        models: List<HuggingFaceModelInfo>,
        hw: DeviceHardwareInfo,
        preference: LlmPreferenceMode
    ): List<HuggingFaceModelInfo> {
        if (models.isEmpty()) return emptyList()

        val scored = models.map { model ->
            val tps = estimateTps(model, hw)
            val (score, reason) = calculateModelScoreAndReason(model, hw, preference, tps)
            model.copy(
                estimatedTps = tps,
                recommendationScore = score,
                hardwareRecommendationReason = reason,
                isHardwareRecommended = false
            )
        }

        val bestModel = scored.maxWithOrNull(
            compareBy<HuggingFaceModelInfo> { it.recommendationScore }
                .thenBy { -it.recommendedRamGb }
        )

        return scored.map { model ->
            if (bestModel != null && model.id == bestModel.id) {
                model.copy(
                    isHardwareRecommended = true,
                    hardwareRecommendationReason = "★ TOP-EMPFEHLUNG (Score ${model.recommendationScore}/100, ${preference.title}): ${model.hardwareRecommendationReason}"
                )
            } else {
                model.copy(isHardwareRecommended = false)
            }
        }
    }

    fun calculateCompatibility(recommendedRamGb: Float, hw: DeviceHardwareInfo): ModelCompatibilityLevel {
        return hw.getCompatibility(recommendedRamGb)
    }
}

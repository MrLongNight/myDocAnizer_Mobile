package com.example

import com.example.model.DeviceHardwareInfo
import com.example.model.LlmPreferenceMode
import com.example.service.AppAuditLogger
import com.example.service.HuggingFaceModelInfo
import com.example.service.llm.LlmHardwareDetector
import org.junit.Assert.*
import org.junit.Test

class LlmBenchmarkAndHardwareTest {

    @Test
    fun testLlmPreferenceModeWeights() {
        // QUALITY_FOCUS: 75% Qualität, 25% Geschwindigkeit
        assertEquals(0.75f, LlmPreferenceMode.QUALITY_FOCUS.qualityWeight, 0.001f)
        assertEquals(0.25f, LlmPreferenceMode.QUALITY_FOCUS.speedWeight, 0.001f)

        // SPEED_FOCUS: WICHTIG: qualityWeight muss zwingend 0.55f (55%) betragen
        assertEquals(0.55f, LlmPreferenceMode.SPEED_FOCUS.qualityWeight, 0.001f)
        assertEquals(0.45f, LlmPreferenceMode.SPEED_FOCUS.speedWeight, 0.001f)

        // Verifizierung: Auch im SPEED_FOCUS behält Qualität die Mehrheit (> 50%)
        assertTrue(LlmPreferenceMode.SPEED_FOCUS.qualityWeight > LlmPreferenceMode.SPEED_FOCUS.speedWeight)

        // ID-Fallback Tests
        assertEquals(LlmPreferenceMode.SPEED_FOCUS, LlmPreferenceMode.fromId("SPEED"))
        assertEquals(LlmPreferenceMode.SPEED_FOCUS, LlmPreferenceMode.fromId("speed"))
        assertEquals(LlmPreferenceMode.QUALITY_FOCUS, LlmPreferenceMode.fromId("QUALITY"))
        assertEquals(LlmPreferenceMode.QUALITY_FOCUS, LlmPreferenceMode.fromId("unknown_mode"))
    }

    @Test
    fun unknownHardwareIsNotPresentedAsMeasuredCapacity() {
        val unknown = DeviceHardwareInfo()
        assertEquals(0f, unknown.totalRamGb, 0.001f)
        assertEquals(0, unknown.cpuCores)
        assertEquals("Unbekannt", unknown.performanceTier)
        assertEquals(com.example.model.ModelCompatibilityLevel.LIMITED, unknown.getCompatibility(2.0f))
    }

    @Test
    fun testHardwareDetectionMethods() {
        val soc = LlmHardwareDetector.detectSocModel()
        assertNotNull(soc)
        assertTrue(soc.isNotBlank())

        val freq = LlmHardwareDetector.detectMaxCpuFreqMhz()
        assertTrue("CPU Frequenz ist unbekannt (0) oder plausibel positiv, war $freq", freq == 0 || freq >= 1000)

        val benchScore = LlmHardwareDetector.runCpuMicroBenchmark()
        assertTrue("CPU Micro-Benchmark Score sollte im Bereich 0..100 liegen, war $benchScore", benchScore in 0..100)
    }

    @Test
    fun testTpsEstimationAndScaling() {
        val hwHighEnd = DeviceHardwareInfo(
            totalRamGb = 12.0f,
            availableRamGb = 8.0f,
            usedRamGb = 4.0f,
            ramUsagePercent = 33,
            cpuCores = 8,
            isLowRamDevice = false,
            socModel = "Snapdragon 8 Gen 3",
            maxCpuFreqMhz = 3300,
            supportsArmDotProd = true,
            hasVulkanGpu = true,
            cpuBenchmarkScore = 90
        )

        val smallModel = HuggingFaceModelInfo(
            id = "smollm2-135m-instruct",
            name = "SmolLM2 135M",
            author = "HuggingFace",
            quantFormat = "Q4_K_M",
            downloadSizeMb = 105,
            parameterSize = "135 Mio",
            recommendedRamGb = 1.5f,
            ramBadge = "1.5 – 2 GB RAM",
            descriptionDe = "Test",
            criteria = emptyList()
        )

        val largeModel = HuggingFaceModelInfo(
            id = "llama-3.1-8b-instruct",
            name = "Llama 3.1 8B",
            author = "Meta",
            quantFormat = "Q4_K_M",
            downloadSizeMb = 4800,
            parameterSize = "8 Mrd",
            recommendedRamGb = 8.0f,
            ramBadge = "8 GB RAM",
            descriptionDe = "Test",
            criteria = emptyList()
        )

        val smallTps = LlmHardwareDetector.estimateTps(smallModel, hwHighEnd)
        val largeTps = LlmHardwareDetector.estimateTps(largeModel, hwHighEnd)

        assertTrue("Kleines Modell ($smallTps TPS) muss schneller sein als großes Modell ($largeTps TPS)", smallTps > largeTps)
        assertTrue("Small Model TPS sollte > 10 sein", smallTps > 10f)
    }

    @Test
    fun testHardwareRecommendationScoring() {
        val hwMidRange = DeviceHardwareInfo(
            totalRamGb = 6.0f,
            availableRamGb = 3.5f,
            usedRamGb = 2.5f,
            ramUsagePercent = 42,
            cpuCores = 8,
            isLowRamDevice = false,
            socModel = "Tensor G2",
            maxCpuFreqMhz = 2800,
            supportsArmDotProd = true,
            hasVulkanGpu = true,
            cpuBenchmarkScore = 75
        )

        val models = listOf(
            HuggingFaceModelInfo(
                id = "danube3-100m",
                name = "Danube3 100M",
                author = "H2O",
                quantFormat = "Q4",
                downloadSizeMb = 85,
                parameterSize = "100M",
                recommendedRamGb = 1.2f,
                ramBadge = "1.5 GB",
                descriptionDe = "Schnell",
                criteria = emptyList()
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-1.5b",
                name = "Qwen 2.5 1.5B Instruct",
                author = "Qwen",
                quantFormat = "Q4",
                downloadSizeMb = 980,
                parameterSize = "1.5B",
                recommendedRamGb = 3.2f,
                ramBadge = "3.5 GB",
                descriptionDe = "Rechnung Spezialist",
                criteria = listOf("Deutsche Rechnungen")
            ),
            HuggingFaceModelInfo(
                id = "llama-3.1-8b",
                name = "Llama 8B",
                author = "Meta",
                quantFormat = "Q4",
                downloadSizeMb = 4800,
                parameterSize = "8B",
                recommendedRamGb = 9.0f,
                ramBadge = "9 GB",
                descriptionDe = "Zu groß für 6 GB",
                criteria = emptyList()
            )
        )

        val recommended = LlmHardwareDetector.applyHardwareRecommendations(
            models = models,
            hw = hwMidRange,
            preference = LlmPreferenceMode.QUALITY_FOCUS
        )

        // Genau ein Modell muss als Hardware-Empfehlung markiert sein
        val topPicks = recommended.filter { it.isHardwareRecommended }
        assertEquals(1, topPicks.size)

        // Für 6 GB RAM sollte Qwen 1.5B der Sweet-Spot sein und nicht das 8B Modell (welches nicht in RAM passt)
        val best = topPicks.first()
        assertEquals("qwen2.5-1.5b", best.id)
        assertTrue(best.recommendationScore in 50..100)
        assertTrue(best.hardwareRecommendationReason.isNotBlank())

        // Das 8B Modell muss wegen RAM-Limitierung einen niedrigen Score haben
        val llama8b = recommended.find { it.id == "llama-3.1-8b" }!!
        assertTrue("Llama 8B Score sollte unter 50 liegen auf 6 GB Gerät", llama8b.recommendationScore < 50)
    }

    @Test
    fun testDualBenchmarkScoreFormulasAndAuditLogging() {
        val modelId = "test-model-qwen"
        val modelName = "Qwen 2.5 1.5B Test"
        val qualityScore = 90
        val quantityScore = 80

        // Berechnung Gesamtscore nach Anforderung: 65% Qualität + 35% Quantität
        val expectedOverall = ((qualityScore * 0.65f) + (quantityScore * 0.35f)).toInt()
        assertEquals(86, expectedOverall)

        // Protokollierung in AppAuditLogger testen
        AppAuditLogger.recordBenchmarkResult(
            modelId = modelId,
            modelName = modelName,
            qualityScore = qualityScore,
            quantityScore = quantityScore,
            overallScore = expectedOverall,
            tokensPerSecond = 18.5f,
            latencyMs = 320L
        )

        val stats = AppAuditLogger.modelBenchmarks.value[modelId]
        assertNotNull(stats)
        assertEquals(modelName, stats?.modelName)
        assertEquals(90, stats?.lastBenchmarkQualityScore)
        assertEquals(80, stats?.lastBenchmarkQuantityScore)
        assertEquals(86, stats?.lastBenchmarkOverallScore)
        assertEquals(18.5f, stats?.tokensPerSecond ?: 0f, 0.01f)
    }
}

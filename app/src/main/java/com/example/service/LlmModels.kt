package com.example.service

import com.example.model.ModelCompatibilityLevel

data class HuggingFaceModelInfo(
    val id: String,
    val name: String,
    val author: String,
    val quantFormat: String,
    val downloadSizeMb: Int,
    val parameterSize: String,
    val recommendedRamGb: Float,
    val ramBadge: String,
    val descriptionDe: String,
    val criteria: List<String>,
    val compatibilityLevel: ModelCompatibilityLevel = ModelCompatibilityLevel.OPTIMAL,
    val isHardwareRecommended: Boolean = false,
    val hardwareRecommendationReason: String = "",
    val estimatedTps: Float = 0f,
    val recommendationScore: Int = 0,
    val downloadUrl: String = "",
    val fileName: String = "",
    val localFileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isSelected: Boolean = false,
    val isCuratedApproved: Boolean = true,
    val approvalStatus: String = "Geprüft von mr.locke84",
    val isNewRelease: Boolean = false,
    val releaseDate: String = "2026",
    val modelCategory: String = "Allgemein"
)

data class LlmClassificationResult(
    val title: String,
    val sender: String,
    val mainCategoryId: String,
    val subCategoryId: String,
    val docType: String,
    val tags: List<String>,
    val explanation: String,
    val customFields: Map<String, String> = emptyMap(),
    val confidence: Float = 0.95f,
    val smartKeywords: List<String> = emptyList(),
    val modelIdUsed: String = "",
    val suggestedFolderPath: String = ""
)

data class LocalAiChatResult(
    val replyText: String,
    val actionType: String? = null,
    val actionLabel: String? = null
)

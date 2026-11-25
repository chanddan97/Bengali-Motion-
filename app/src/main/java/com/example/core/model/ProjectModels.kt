package com.example.core.model

import java.util.UUID

enum class AppLanguage(
    val code: String,
    val displayLabel: String,
    val shortBadge: String,
) {
    BENGALI("bn", "বাংলা", "বাং"),
    ENGLISH("en", "English", "EN"),
    BENGALI_ENGLISH("bn-en", "বাংলা + English", "বাং+EN");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: BENGALI_ENGLISH
    }
}

enum class AspectRatio(
    val label: String,
    val ratioValue: Float,
    val orientationTagBn: String,
    val orientationTagEn: String,
) {
    PORTRAIT_9_16("9:16", 9f / 16f, "রিলস / শর্টস", "Reels / Shorts"),
    SQUARE_1_1("1:1", 1f, "বর্গাকার", "Square"),
    PORTRAIT_4_5("4:5", 4f / 5f, "ফিড পোর্ট্রেট", "Feed Portrait"),
    LANDSCAPE_16_9("16:9", 16f / 9f, "ইউটিউব / ওয়াইড", "YouTube / Wide");

    companion object {
        fun fromLabel(label: String?): AspectRatio =
            entries.firstOrNull { it.label == label } ?: PORTRAIT_9_16
    }
}

enum class ExportResolution(val label: String) {
    HD_720P("720p"),
    FHD_1080P("1080p");

    companion object {
        fun fromLabel(label: String?): ExportResolution =
            entries.firstOrNull { it.label == label } ?: FHD_1080P
    }
}

data class WatermarkLayer(
    val enabled: Boolean,
    val kind: String,
    val text: String?,
    val assetId: String?,
    val position: String,
    val scale: Float,
    val opacity: Float,
    val marginPercent: Float,
    val offsetXPercent: Float = 0f,
    val offsetYPercent: Float = 0f,
    val startMs: Long = 0L,
    val endMs: Long? = null,
)

data class WatermarkSettings(
    val user: WatermarkLayer = defaultUserLayer(),
    val developer: WatermarkLayer = defaultDeveloperLayer(),
) {
    companion object {
        fun defaultUserLayer(): WatermarkLayer = WatermarkLayer(
            enabled = false,
            kind = "text",
            text = "",
            assetId = null,
            position = "bottom-right",
            scale = 1.0f,
            opacity = 0.85f,
            marginPercent = 3f,
            offsetXPercent = 0f,
            offsetYPercent = 0f,
            startMs = 0L,
            endMs = null,
        )

        fun defaultDeveloperLayer(): WatermarkLayer = WatermarkLayer(
            enabled = false,
            kind = "text",
            text = "Made with BanglaMotion",
            assetId = null,
            position = "top-right",
            scale = 0.7f,
            opacity = 0.55f,
            marginPercent = 3f,
            offsetXPercent = 0f,
            offsetYPercent = 0f,
            startMs = 0L,
            endMs = null,
        )
    }
}

data class SubtitleCue(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val language: AppLanguage = AppLanguage.BENGALI_ENGLISH,
    val aspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val fps: Int = DEFAULT_FPS,
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val script: String = "",
    val audioAssetId: String? = null,
    val audioAsset: AudioAsset? = null,
    val subtitleTrackId: String? = null,
    val subtitleCues: List<SubtitleCue> = emptyList(),
    val templateId: String = DEFAULT_TEMPLATE_ID,
    val scenes: List<SceneItem> = emptyList(),
    val sceneIds: List<String> = scenes.map { it.id },
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val watermarkSettings: WatermarkSettings = WatermarkSettings(),
) {
    val totalDurationMs: Long
        get() {
            val sceneEnd = scenes.maxOfOrNull { it.endMs } ?: 0L
            val cueEnd = subtitleCues.maxOfOrNull { it.endMs } ?: 0L
            val audioEnd = audioAsset?.effectiveDurationMs ?: 0L
            return maxOf(sceneEnd, cueEnd, audioEnd).coerceAtLeast(3_000L)
        }

    companion object {
        const val DEFAULT_FPS = 30
        const val MAX_NAME_LENGTH = 80
        const val DEFAULT_TEMPLATE_ID = "minimal-text"
    }
}

enum class ProjectWorkflowStep(
    val stepNumber: Int,
    val milestoneNumber: Int,
    val isActiveInMilestone1: Boolean = true,
) {
    SCRIPT(1, 2, true),
    VOICE_AUDIO(2, 4, true),
    SRT(3, 2, true),
    FORMAT_LANGUAGE(4, 1, true),
    TEMPLATE(5, 3, true),
    AUTO_MATCH(6, 5, true),
    SCENES(7, 5, true),
    PREVIEW(8, 5, true),
    WATERMARK(9, 6, true),
    EXPORT(10, 7, true),
}

data class LocalStorageSummary(
    val activeProjectCount: Int = 0,
    val archivedProjectCount: Int = 0,
    val storagePath: String = "",
    val usedBytes: Long = 0L,
    val availableBytes: Long = 0L,
)

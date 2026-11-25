package com.example.core.model

import java.util.UUID

enum class MotionPreset(val id: String, val displayLabel: String) {
    FADE("fade", "Fade"),
    SLIDE_UP("slide-up", "Slide Up"),
    SLIDE_DOWN("slide-down", "Slide Down"),
    SLIDE_LEFT("slide-left", "Slide Left"),
    SLIDE_RIGHT("slide-right", "Slide Right"),
    SCALE_IN("scale-in", "Scale In"),
    REVEAL("reveal", "Reveal"),
    DRAW_LINE("draw-line", "Draw Line"),
    PARALLAX("parallax", "Parallax"),
    WORD_STAGGER("word-stagger", "Word Stagger"),
    NONE("none", "None");

    companion object {
        fun fromId(id: String?): MotionPreset =
            entries.firstOrNull { it.id == id } ?: SLIDE_UP
    }
}

enum class WatermarkPosition(val id: String, val displayLabel: String) {
    TOP_LEFT("top-left", "Top Left"),
    TOP_CENTER("top-center", "Top Center"),
    TOP_RIGHT("top-right", "Top Right"),
    CENTER("center", "Center"),
    BOTTOM_LEFT("bottom-left", "Bottom Left"),
    BOTTOM_CENTER("bottom-center", "Bottom Center"),
    BOTTOM_RIGHT("bottom-right", "Bottom Right");

    companion object {
        fun fromId(id: String?): WatermarkPosition =
            entries.firstOrNull { it.id == id } ?: BOTTOM_RIGHT
    }
}

enum class SceneTransition(val id: String, val displayLabel: String) {
    CROSSFADE("crossfade", "Crossfade"),
    SLIDE_UP("slide-up", "Slide Up"),
    WIPE_RIGHT("wipe-right", "Wipe Right"),
    ZOOM_THROUGH("zoom-through", "Zoom Burst"),
    NONE("none", "Cut");

    companion object {
        fun fromId(id: String?): SceneTransition =
            entries.firstOrNull { it.id == id } ?: CROSSFADE
    }
}

data class SceneItem(
    val id: String = UUID.randomUUID().toString(),
    val orderIndex: Int = 0,
    val headline: String = "",
    val caption: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 3_000L,
    val templateId: String = "minimal-text",
    val backgroundImagePath: String? = null,
    val accentHex: String? = null,
    val motionPreset: MotionPreset = MotionPreset.SLIDE_UP,
    val transitionIn: SceneTransition = SceneTransition.CROSSFADE,
    val badgeLabel: String = "",
    val highlightWord: String = "",
    val reducedMotion: Boolean = false,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(500L)
}

data class AudioAsset(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val localPath: String,
    val mimeType: String = "audio/mp4",
    val durationMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val srtSyncOffsetMs: Long = 0L,
    val isRecorded: Boolean = false,
) {
    val effectiveEndMs: Long
        get() = if (trimEndMs in 1L..durationMs) trimEndMs else durationMs

    val effectiveDurationMs: Long
        get() = (effectiveEndMs - trimStartMs).coerceAtLeast(0L)
}

enum class SrtIssueSeverity {
    WARNING,
    ERROR,
}

data class SrtValidationIssue(
    val blockNumber: Int,
    val lineNumber: Int,
    val severity: SrtIssueSeverity,
    val message: String,
)

data class SrtParseResult(
    val cues: List<SubtitleCue>,
    val issues: List<SrtValidationIssue>,
    val totalDurationMs: Long,
    val usedEncodingFallback: Boolean = false,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == SrtIssueSeverity.ERROR }
    val errorCount: Int get() = issues.count { it.severity == SrtIssueSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == SrtIssueSeverity.WARNING }
}

data class ExportConfig(
    val aspectRatio: AspectRatio,
    val resolution: ExportResolution,
    val fps: Int = 30,
    val includeAudio: Boolean = true,
) {
    fun outputDimensions(): Pair<Int, Int> {
        val shortEdge = when (resolution) {
            ExportResolution.HD_720P -> 720
            ExportResolution.FHD_1080P -> 1080
        }
        val (rawW, rawH) = when (aspectRatio) {
            AspectRatio.PORTRAIT_9_16 -> shortEdge to (shortEdge * 16 / 9)
            AspectRatio.SQUARE_1_1 -> shortEdge to shortEdge
            AspectRatio.PORTRAIT_4_5 -> shortEdge to (shortEdge * 5 / 4)
            AspectRatio.LANDSCAPE_16_9 -> (shortEdge * 16 / 9) to shortEdge
        }
        // H.264 encoders require even dimensions (multiple of 16 safest across Android SoCs)
        val evenW = (rawW / 16) * 16
        val evenH = (rawH / 16) * 16
        return evenW.coerceAtLeast(320) to evenH.coerceAtLeast(320)
    }
}

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(
        val phase: String,
        val progress: Float,
        val currentFrame: Int,
        val totalFrames: Int,
    ) : ExportState

    data class Success(
        val outputPath: String,
        val fileSizeBytes: Long,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val hasAudioTrack: Boolean,
        val galleryUri: String? = null,
    ) : ExportState

    data class Failed(
        val reason: String,
        val isLowStorage: Boolean = false,
    ) : ExportState

    data object Cancelled : ExportState
}

data class AiProxyConfig(
    val proxyBaseUrl: String = "",
    val modelLabel: String = "gemini-2.5-flash (via user backend proxy)",
    val consentGranted: Boolean = false,
) {
    val isConfigured: Boolean
        get() = proxyBaseUrl.trim().startsWith("https://") && proxyBaseUrl.trim().length > 10
}

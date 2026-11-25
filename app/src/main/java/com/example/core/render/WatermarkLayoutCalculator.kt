package com.example.core.render

import com.example.core.model.WatermarkLayer
import com.example.core.model.WatermarkPosition
import com.example.core.model.WatermarkSettings

data class WatermarkBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun intersects(other: WatermarkBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

data class WatermarkLayoutResult(
    val userBox: WatermarkBox?,
    val developerBox: WatermarkBox?,
    val overlaps: Boolean,
)

/**
 * Deterministic layout math shared identically between Preview UI, Watermark Setup Preview,
 * and final MP4 export compositing.
 */
object WatermarkLayoutCalculator {

    fun isVisibleAt(layer: WatermarkLayer, timeMs: Long): Boolean {
        if (!layer.enabled) return false
        if (timeMs < layer.startMs) return false
        val end = layer.endMs
        if (end != null && end > layer.startMs && timeMs > end) return false
        return true
    }

    fun computeBox(
        layer: WatermarkLayer,
        canvasWidth: Float,
        canvasHeight: Float,
        contentWidth: Float,
        contentHeight: Float,
        timeMs: Long,
    ): WatermarkBox? {
        if (!isVisibleAt(layer, timeMs)) return null
        if (canvasWidth <= 0f || canvasHeight <= 0f) return null

        val safeMarginX = canvasWidth * (layer.marginPercent.coerceIn(0f, 20f) / 100f)
        val safeMarginY = canvasHeight * (layer.marginPercent.coerceIn(0f, 20f) / 100f)
        val clampedW = contentWidth.coerceIn(1f, (canvasWidth - safeMarginX * 2f).coerceAtLeast(1f))
        val clampedH = contentHeight.coerceIn(1f, (canvasHeight - safeMarginY * 2f).coerceAtLeast(1f))

        val position = WatermarkPosition.fromId(layer.position)
        val baseLeft = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> safeMarginX
            WatermarkPosition.TOP_CENTER, WatermarkPosition.CENTER, WatermarkPosition.BOTTOM_CENTER ->
                (canvasWidth - clampedW) / 2f
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT ->
                canvasWidth - safeMarginX - clampedW
        }
        val baseTop = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_CENTER, WatermarkPosition.TOP_RIGHT -> safeMarginY
            WatermarkPosition.CENTER -> (canvasHeight - clampedH) / 2f
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_CENTER, WatermarkPosition.BOTTOM_RIGHT ->
                canvasHeight - safeMarginY - clampedH
        }

        val offsetX = canvasWidth * (layer.offsetXPercent.coerceIn(-30f, 30f) / 100f)
        val offsetY = canvasHeight * (layer.offsetYPercent.coerceIn(-30f, 30f) / 100f)

        val finalLeft = (baseLeft + offsetX).coerceIn(safeMarginX * 0.25f, (canvasWidth - clampedW - safeMarginX * 0.25f).coerceAtLeast(0f))
        val finalTop = (baseTop + offsetY).coerceIn(safeMarginY * 0.25f, (canvasHeight - clampedH - safeMarginY * 0.25f).coerceAtLeast(0f))

        return WatermarkBox(
            left = finalLeft,
            top = finalTop,
            right = finalLeft + clampedW,
            bottom = finalTop + clampedH,
        )
    }

    fun evaluateOverlap(
        settings: WatermarkSettings,
        canvasWidth: Float,
        canvasHeight: Float,
        userContentSize: Pair<Float, Float>,
        devContentSize: Pair<Float, Float>,
        timeMs: Long = 0L,
    ): WatermarkLayoutResult {
        val userBox = computeBox(
            layer = settings.user,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            contentWidth = userContentSize.first,
            contentHeight = userContentSize.second,
            timeMs = timeMs,
        )
        val devBox = computeBox(
            layer = settings.developer,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            contentWidth = devContentSize.first,
            contentHeight = devContentSize.second,
            timeMs = timeMs,
        )
        val overlaps = userBox != null && devBox != null && userBox.intersects(devBox)
        return WatermarkLayoutResult(userBox, devBox, overlaps)
    }

    fun suggestNonOverlappingDeveloperPosition(userPosition: String): String = when (WatermarkPosition.fromId(userPosition)) {
        WatermarkPosition.TOP_RIGHT -> WatermarkPosition.BOTTOM_LEFT.id
        WatermarkPosition.TOP_LEFT -> WatermarkPosition.BOTTOM_RIGHT.id
        WatermarkPosition.TOP_CENTER -> WatermarkPosition.BOTTOM_CENTER.id
        WatermarkPosition.BOTTOM_RIGHT -> WatermarkPosition.TOP_LEFT.id
        WatermarkPosition.BOTTOM_LEFT -> WatermarkPosition.TOP_RIGHT.id
        WatermarkPosition.BOTTOM_CENTER -> WatermarkPosition.TOP_CENTER.id
        WatermarkPosition.CENTER -> WatermarkPosition.TOP_RIGHT.id
    }
}

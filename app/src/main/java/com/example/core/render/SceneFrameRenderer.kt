package com.example.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.model.MotionPreset
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.core.model.SubtitleCue
import com.example.core.model.WatermarkLayer
import com.example.core.model.WatermarkSettings
import com.example.data.templates.TemplateCatalogLoader
import com.example.data.templates.TemplateVisualStyle
import com.example.feature.subtitles.SrtEngine
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class RenderFrameInput(
    val project: Project,
    val timeMs: Long,
    val width: Int,
    val height: Int,
    val backgroundBitmaps: Map<String, Bitmap> = emptyMap(),
    val userWatermarkBitmap: Bitmap? = null,
    val overrideScene: SceneItem? = null,
    val overrideSubtitle: SubtitleCue? = null,
)

/**
 * Single deterministic rendering engine shared across:
 * 1) Template gallery live previews (all 20 templates)
 * 2) Scene editor & real-time video preview
 * 3) Watermark setup preview
 * 4) Final MP4 H.264/AAC export
 */
class SceneFrameRenderer(context: Context) {
    private val bengaliTypeface: Typeface =
        ResourcesCompat.getFont(context.applicationContext, R.font.noto_sans_bengali)
            ?: Typeface.DEFAULT

    private val displayTypeface: Typeface =
        ResourcesCompat.getFont(context.applicationContext, R.font.space_grotesk)
            ?: bengaliTypeface

    fun renderToBitmap(input: RenderFrameInput): Bitmap {
        val w = input.width.coerceAtLeast(160)
        val h = input.height.coerceAtLeast(160)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderToCanvas(canvas, input.copy(width = w, height = h))
        return bitmap
    }

    fun renderTemplateSampleBitmap(
        templateId: String,
        sampleHeadline: String,
        sampleCaption: String,
        width: Int = 360,
        height: Int = 240,
    ): Bitmap {
        val sampleScene = SceneItem(
            headline = sampleHeadline,
            caption = sampleCaption,
            startMs = 0L,
            endMs = 3_000L,
            templateId = templateId,
            motionPreset = MotionPreset.NONE,
        )
        val sampleProject = Project(
            name = sampleHeadline,
            templateId = templateId,
            scenes = listOf(sampleScene),
            watermarkSettings = WatermarkSettings(),
        )
        return renderToBitmap(
            RenderFrameInput(
                project = sampleProject,
                timeMs = 1_500L,
                width = width,
                height = height,
                overrideScene = sampleScene,
            ),
        )
    }

    fun renderToCanvas(canvas: Canvas, input: RenderFrameInput) {
        val w = input.width.toFloat()
        val h = input.height.toFloat()
        val project = input.project
        val scene = input.overrideScene ?: resolveSceneAt(project, input.timeMs)
        val style = TemplateCatalogLoader.visualStyleFor(scene.templateId)

        val sceneElapsed = (input.timeMs - scene.startMs).coerceAtLeast(0L)
        val motionProgress = if (scene.reducedMotion || scene.motionPreset == MotionPreset.NONE) {
            1f
        } else {
            (sceneElapsed / 650f).coerceIn(0f, 1f)
        }
        val eased = easeOutCubic(motionProgress)

        // 1. Base template background + motif artwork
        drawTemplateBackdrop(canvas, w, h, style, eased, input.timeMs)

        // 2. Optional scene background photo (center-crop + readability scrim)
        val bgPath = scene.backgroundImagePath
        val bgBitmap = if (!bgPath.isNullOrBlank()) input.backgroundBitmaps[bgPath] else null
        if (bgBitmap != null && !bgBitmap.isRecycled) {
            drawCenterCropBitmap(canvas, bgBitmap, w, h)
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(
                        Color.argb(145, 10, 15, 30),
                        Color.argb(175, 10, 15, 30),
                        Color.argb(205, 10, 15, 30),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w, h, scrimPaint)
        }

        // 3. Foreground template frame + Bengali-safe headline & caption with deterministic motion
        drawSceneContent(canvas, w, h, scene, style, eased)

        // 4. Active SRT Subtitle Overlay
        val syncOffset = project.audioAsset?.srtSyncOffsetMs ?: 0L
        val activeCue = input.overrideSubtitle
            ?: SrtEngine.activeCueAt(project.subtitleCues, input.timeMs, syncOffset)
        if (activeCue != null && activeCue.text.isNotBlank()) {
            drawSubtitleOverlay(canvas, w, h, activeCue.text)
        }

        // 5. Watermarks (User & Optional Developer) — ONLY rendered if enabled == true
        drawWatermarks(
            canvas = canvas,
            w = w,
            h = h,
            settings = project.watermarkSettings,
            timeMs = input.timeMs,
            userBitmap = input.userWatermarkBitmap,
        )
    }

    fun evaluateWatermarkOverlap(
        settings: WatermarkSettings,
        canvasWidth: Float,
        canvasHeight: Float,
        userBitmap: Bitmap?,
        timeMs: Long = 0L,
    ): WatermarkLayoutResult {
        val userSize = measureWatermarkSize(settings.user, canvasWidth, canvasHeight, userBitmap)
        val devSize = measureWatermarkSize(settings.developer, canvasWidth, canvasHeight, null)
        return WatermarkLayoutCalculator.evaluateOverlap(
            settings = settings,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            userContentSize = userSize,
            devContentSize = devSize,
            timeMs = timeMs,
        )
    }

    private fun resolveSceneAt(project: Project, timeMs: Long): SceneItem {
        if (project.scenes.isNotEmpty()) {
            val matched = project.scenes.firstOrNull { timeMs in it.startMs..it.endMs }
            if (matched != null) return matched
            val preceding = project.scenes.lastOrNull { timeMs >= it.startMs }
            if (preceding != null) return preceding
            return project.scenes.first()
        }
        val firstLine = project.script.lines().firstOrNull { it.isNotBlank() }?.trim()
            ?: project.name
        return SceneItem(
            headline = firstLine,
            caption = project.aspectRatio.label,
            startMs = 0L,
            endMs = project.totalDurationMs,
            templateId = project.templateId,
            motionPreset = MotionPreset.FADE,
        )
    }

    private fun drawTemplateBackdrop(
        canvas: Canvas,
        w: Float,
        h: Float,
        style: TemplateVisualStyle,
        eased: Float,
        timeMs: Long,
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w, h, style.bgStartColor, style.bgEndColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val unit = min(w, h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (style.motif) {
            "shattered-glass" -> {
                paint.color = withAlpha(style.accentColor, 46)
                val poly1 = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.45f, 0f)
                    lineTo(w * 0.22f, h * 0.34f)
                    close()
                }
                canvas.drawPath(poly1, paint)
                paint.color = withAlpha(style.secondaryAccentColor, 55)
                val poly2 = Path().apply {
                    moveTo(w, h * 0.12f)
                    lineTo(w, h * 0.65f)
                    lineTo(w * 0.68f, h * 0.42f)
                    close()
                }
                canvas.drawPath(poly2, paint)
                paint.color = withAlpha(Color.WHITE, 28)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.005f
                canvas.drawPath(poly1, paint)
                canvas.drawPath(poly2, paint)
            }

            "detective-board" -> {
                // Corkboard subtle dots & red connector string
                paint.color = withAlpha(style.secondaryAccentColor, 50)
                val step = (unit * 0.09f).coerceAtLeast(18f)
                var x = step * 0.5f
                while (x < w) {
                    var y = step * 0.5f
                    while (y < h) {
                        canvas.drawCircle(x, y, unit * 0.004f, paint)
                        y += step
                    }
                    x += step
                }
                paint.color = style.accentColor
                paint.strokeWidth = unit * 0.007f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(w * 0.14f, h * 0.16f, w * 0.84f, h * 0.24f, paint)
                canvas.drawLine(w * 0.84f, h * 0.24f, w * 0.2f, h * 0.82f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(w * 0.14f, h * 0.16f, unit * 0.018f, paint)
                canvas.drawCircle(w * 0.84f, h * 0.24f, unit * 0.018f, paint)
            }

            "cosmos-story" -> {
                val drift = ((timeMs % 8000L) / 8000f) * unit * 0.04f
                paint.color = withAlpha(Color.WHITE, 190)
                for (i in 0..24) {
                    val sx = ((i * 73 % 97) / 97f) * w
                    val sy = (((i * 41 % 89) / 89f) * h + drift) % h
                    val r = unit * (0.003f + (i % 3) * 0.002f)
                    canvas.drawCircle(sx, sy, r, paint)
                }
                paint.color = withAlpha(style.accentColor, 68)
                canvas.drawCircle(w * 0.82f, h * 0.18f, unit * 0.18f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.006f
                paint.color = withAlpha(style.secondaryAccentColor, 140)
                canvas.drawOval(
                    RectF(w * 0.58f, h * 0.12f, w * 1.04f, h * 0.24f),
                    paint,
                )
            }

            "mint-glass", "soft-gradient" -> {
                paint.color = withAlpha(style.secondaryAccentColor, 72)
                canvas.drawCircle(w * 0.18f, h * 0.2f, unit * 0.26f, paint)
                paint.color = withAlpha(style.accentColor, 58)
                canvas.drawCircle(w * 0.84f, h * 0.78f, unit * 0.28f, paint)
            }

            "blueprint" -> {
                paint.color = withAlpha(style.accentColor, 42)
                paint.strokeWidth = (unit * 0.0025f).coerceAtLeast(1f)
                val grid = (unit * 0.08f).coerceAtLeast(18f)
                var x = 0f
                while (x <= w) {
                    canvas.drawLine(x, 0f, x, h, paint)
                    x += grid
                }
                var y = 0f
                while (y <= h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += grid
                }
                paint.color = withAlpha(style.secondaryAccentColor, 140)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.005f
                canvas.drawRect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f, paint)
            }

            "comic-pop" -> {
                paint.color = withAlpha(Color.WHITE, 60)
                val dotStep = (unit * 0.07f).coerceAtLeast(16f)
                var dx = dotStep * 0.5f
                while (dx < w) {
                    var dy = dotStep * 0.5f
                    while (dy < h) {
                        canvas.drawCircle(dx, dy, unit * 0.008f, paint)
                        dy += dotStep
                    }
                    dx += dotStep
                }
            }

            "paper-cut" -> {
                paint.color = withAlpha(style.accentColor, 200)
                val wave1 = Path().apply {
                    moveTo(0f, h * 0.82f)
                    quadTo(w * 0.35f, h * 0.72f, w * 0.65f, h * 0.84f)
                    quadTo(w * 0.85f, h * 0.92f, w, h * 0.8f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                canvas.drawPath(wave1, paint)
                paint.color = withAlpha(style.secondaryAccentColor, 195)
                val wave2 = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h * 0.14f)
                    quadTo(w * 0.5f, h * 0.24f, 0f, h * 0.12f)
                    close()
                }
                canvas.drawPath(wave2, paint)
            }

            "retro-terminal" -> {
                paint.color = withAlpha(style.accentColor, 24)
                var y = 0f
                val lineGap = (unit * 0.015f).coerceAtLeast(4f)
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += lineGap
                }
            }

            "split-screen" -> {
                paint.color = withAlpha(style.panelColor, 225)
                if (w >= h) {
                    canvas.drawRect(0f, 0f, w * 0.48f, h, paint)
                } else {
                    canvas.drawRect(0f, 0f, w, h * 0.48f, paint)
                }
            }

            "chalkboard" -> {
                paint.style = Paint.Style.STROKE
                paint.color = withAlpha(Color.WHITE, 140)
                paint.strokeWidth = unit * 0.006f
                paint.pathEffect = DashPathEffect(floatArrayOf(unit * 0.03f, unit * 0.015f), 0f)
                canvas.drawRoundRect(
                    RectF(w * 0.06f, h * 0.06f, w * 0.94f, h * 0.94f),
                    unit * 0.03f,
                    unit * 0.03f,
                    paint,
                )
            }

            "3d-object-stage" -> {
                val angle = (eased * 0.35f) + ((timeMs % 6000L) / 6000f) * 6.28f
                val cx = w * 0.5f + cos(angle) * unit * 0.03f
                val cy = h * 0.78f
                paint.color = withAlpha(style.accentColor, 55)
                canvas.drawOval(RectF(cx - unit * 0.34f, cy - unit * 0.05f, cx + unit * 0.34f, cy + unit * 0.05f), paint)
                paint.color = withAlpha(style.secondaryAccentColor, 85)
                val offset = sin(angle) * unit * 0.02f
                canvas.drawRoundRect(
                    RectF(w * 0.14f + offset, h * 0.24f, w * 0.86f + offset, h * 0.68f),
                    unit * 0.045f,
                    unit * 0.045f,
                    paint,
                )
            }

            "documentary" -> {
                paint.color = Color.BLACK
                canvas.drawRect(0f, 0f, w, h * 0.09f, paint)
                canvas.drawRect(0f, h * 0.91f, w, h, paint)
            }

            else -> {
                paint.color = withAlpha(style.accentColor, 26)
                canvas.drawCircle(w * 0.88f, h * 0.14f, unit * 0.22f, paint)
            }
        }
    }

    private fun drawSceneContent(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneItem,
        style: TemplateVisualStyle,
        eased: Float,
    ) {
        val unit = min(w, h)
        val alpha = (if (scene.motionPreset == MotionPreset.NONE) 1f else eased).coerceIn(0f, 1f)

        val dx = when (scene.motionPreset) {
            MotionPreset.SLIDE_LEFT -> (1f - eased) * w * 0.12f
            MotionPreset.SLIDE_RIGHT -> -(1f - eased) * w * 0.12f
            MotionPreset.PARALLAX -> (1f - eased) * w * 0.05f
            else -> 0f
        }
        val dy = when (scene.motionPreset) {
            MotionPreset.SLIDE_UP -> (1f - eased) * h * 0.09f
            MotionPreset.SLIDE_DOWN -> -(1f - eased) * h * 0.09f
            else -> 0f
        }
        val scale = when (scene.motionPreset) {
            MotionPreset.SCALE_IN -> 0.88f + (0.12f * eased)
            else -> 1f
        }

        canvas.save()
        canvas.translate(dx, dy)
        if (scale != 1f) {
            canvas.scale(scale, scale, w * 0.5f, h * 0.45f)
        }

        val cardRect = when (style.motif) {
            "interview-news" -> RectF(w * 0.06f, h * 0.56f, w * 0.94f, h * 0.84f)
            "saas-product", "retro-terminal", "detective-board", "folded-photo-story", "mint-glass", "comic-pop", "magazine-editorial" ->
                RectF(w * 0.08f, h * 0.22f, w * 0.92f, h * 0.72f)
            else -> RectF(w * 0.08f, h * 0.24f, w * 0.92f, h * 0.70f)
        }

        // Draw styled card panel for card-based templates
        if (style.motif != "minimal-text" && style.motif != "kinetic-type" && style.motif != "documentary") {
            val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(style.panelColor, (Color.alpha(style.panelColor) * alpha).toInt())
            }
            val radius = unit * 0.04f
            canvas.drawRoundRect(cardRect, radius, radius, panelPaint)

            if (style.motif == "comic-pop") {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    color = Color.BLACK
                    strokeWidth = unit * 0.012f
                }
                canvas.drawRoundRect(cardRect, radius, radius, borderPaint)
            } else {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    color = withAlpha(style.accentColor, (170 * alpha).toInt())
                    strokeWidth = (unit * 0.005f).coerceAtLeast(1.5f)
                }
                canvas.drawRoundRect(cardRect, radius, radius, borderPaint)
            }

            // Header chrome accents for SaaS Product & Retro Terminal & Interview News
            if (style.motif == "saas-product" || style.motif == "retro-terminal") {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val dotY = cardRect.top + unit * 0.04f
                val dotR = unit * 0.012f
                val colors = listOf(Color.parseColor("#EF4444"), Color.parseColor("#F59E0B"), Color.parseColor("#22C55E"))
                colors.forEachIndexed { idx, col ->
                    dotPaint.color = withAlpha(col, (230 * alpha).toInt())
                    canvas.drawCircle(cardRect.left + unit * (0.05f + idx * 0.036f), dotY, dotR, dotPaint)
                }
            } else if (style.motif == "interview-news") {
                val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(style.accentColor, (255 * alpha).toInt())
                }
                canvas.drawRect(cardRect.left, cardRect.top, cardRect.left + unit * 0.024f, cardRect.bottom, tagPaint)
            } else if (style.motif == "qa-answer") {
                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(style.accentColor, (240 * alpha).toInt())
                }
                canvas.drawRoundRect(
                    RectF(cardRect.left + unit * 0.04f, cardRect.top - unit * 0.045f, cardRect.left + unit * 0.24f, cardRect.top + unit * 0.025f),
                    unit * 0.02f,
                    unit * 0.02f,
                    badgePaint,
                )
            }
        }

        // Accent bar
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(style.accentColor, (245 * alpha).toInt())
        }
        val barWidth = (cardRect.width() * 0.22f) * if (scene.motionPreset == MotionPreset.DRAW_LINE) eased else 1f
        canvas.drawRoundRect(
            RectF(
                cardRect.centerX() - barWidth / 2f,
                cardRect.top + unit * 0.065f,
                cardRect.centerX() + barWidth / 2f,
                cardRect.top + unit * 0.078f,
            ),
            unit * 0.01f,
            unit * 0.01f,
            barPaint,
        )

        // Headline text (word-stagger support!)
        val rawHeadline = scene.headline.ifBlank { "বাংলা-মোশন স্টুডিও" }
        val displayHeadline = if (scene.motionPreset == MotionPreset.WORD_STAGGER && eased < 0.98f) {
            val words = rawHeadline.split(" ")
            val visibleCount = ((words.size * eased).toInt() + 1).coerceIn(1, words.size)
            words.take(visibleCount).joinToString(" ")
        } else {
            rawHeadline
        }

        val textPad = unit * 0.065f
        val textWidth = (cardRect.width() - textPad * 2f).toInt().coerceAtLeast(80)
        val headlineSize = (unit * 0.068f).coerceIn(16f, 76f)
        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(style.headlineColor, (255 * alpha).toInt())
            textSize = headlineSize
            typeface = Typeface.create(bengaliTypeface, Typeface.BOLD)
        }
        val headlineLayout = StaticLayout.Builder
            .obtain(displayHeadline, 0, displayHeadline.length, headlinePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setIncludePad(true)
            .build()

        val headlineTop = cardRect.top + unit * 0.11f
        canvas.save()
        canvas.translate(cardRect.left + textPad, headlineTop)
        headlineLayout.draw(canvas)
        canvas.restore()

        // Caption text
        if (scene.caption.isNotBlank()) {
            val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(style.captionColor, (235 * alpha).toInt())
                textSize = (unit * 0.038f).coerceIn(12f, 44f)
                typeface = bengaliTypeface
            }
            val captionLayout = StaticLayout.Builder
                .obtain(scene.caption, 0, scene.caption.length, captionPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .setIncludePad(true)
                .build()
            val captionTop = (headlineTop + headlineLayout.height + unit * 0.03f)
                .coerceAtMost(cardRect.bottom - captionLayout.height - unit * 0.03f)
            canvas.save()
            canvas.translate(cardRect.left + textPad, captionTop)
            captionLayout.draw(canvas)
            canvas.restore()
        }

        canvas.restore()
    }

    private fun drawSubtitleOverlay(canvas: Canvas, w: Float, h: Float, subtitleText: String) {
        val unit = min(w, h)
        val maxTextW = (w * 0.84f).toInt().coerceAtLeast(80)
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (unit * 0.044f).coerceIn(13f, 52f)
            typeface = Typeface.create(bengaliTypeface, Typeface.BOLD)
        }
        val layout = StaticLayout.Builder
            .obtain(subtitleText, 0, subtitleText.length, subPaint, maxTextW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setIncludePad(true)
            .build()

        val padX = unit * 0.038f
        val padY = unit * 0.022f
        val boxW = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) }?.plus(padX * 2f)
            ?: (w * 0.6f)
        val boxH = layout.height + padY * 2f
        val bottomMargin = h * 0.075f
        val boxTop = (h - bottomMargin - boxH).coerceAtLeast(h * 0.65f)
        val boxLeft = (w - boxW) / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(198, 8, 12, 24)
        }
        canvas.drawRoundRect(
            RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH),
            unit * 0.025f,
            unit * 0.025f,
            bgPaint,
        )

        canvas.save()
        canvas.translate((w - maxTextW) / 2f, boxTop + padY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawWatermarks(
        canvas: Canvas,
        w: Float,
        h: Float,
        settings: WatermarkSettings,
        timeMs: Long,
        userBitmap: Bitmap?,
    ) {
        if (settings.user.enabled) {
            drawSingleWatermark(canvas, w, h, settings.user, timeMs, userBitmap, isDeveloper = false)
        }
        if (settings.developer.enabled) {
            drawSingleWatermark(canvas, w, h, settings.developer, timeMs, null, isDeveloper = true)
        }
    }

    private fun drawSingleWatermark(
        canvas: Canvas,
        w: Float,
        h: Float,
        layer: WatermarkLayer,
        timeMs: Long,
        imageBitmap: Bitmap?,
        isDeveloper: Boolean,
    ) {
        val (cw, ch) = measureWatermarkSize(layer, w, h, imageBitmap)
        if (cw <= 1f || ch <= 1f) return
        val box = WatermarkLayoutCalculator.computeBox(
            layer = layer,
            canvasWidth = w,
            canvasHeight = h,
            contentWidth = cw,
            contentHeight = ch,
            timeMs = timeMs,
        ) ?: return

        val alphaInt = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
        if (!isDeveloper && layer.kind == "image" && imageBitmap != null && !imageBitmap.isRecycled) {
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = alphaInt
            }
            val dst = RectF(box.left, box.top, box.right, box.bottom)
            canvas.drawBitmap(imageBitmap, null, dst, bitmapPaint)
            return
        }

        val label = layer.text?.trim().orEmpty()
        if (label.isEmpty()) return

        val unit = min(w, h)
        val textSizePx = (unit * 0.034f * layer.scale.coerceIn(0.3f, 2.5f)).coerceIn(10f, 72f)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDeveloper) {
                Color.argb((alphaInt * 0.65f).toInt(), 15, 22, 41)
            } else {
                Color.argb((alphaInt * 0.55f).toInt(), 10, 15, 30)
            }
        }
        val radius = unit * 0.018f
        canvas.drawRoundRect(RectF(box.left, box.top, box.right, box.bottom), radius, radius, pillPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alphaInt, 255, 255, 255)
            textSize = textSizePx
            typeface = Typeface.create(if (isDeveloper) displayTypeface else bengaliTypeface, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fm = textPaint.fontMetrics
        val baselineY = box.top + (box.height - (fm.bottom - fm.top)) / 2f - fm.top
        canvas.drawText(label, (box.left + box.right) / 2f, baselineY, textPaint)
    }

    private fun measureWatermarkSize(
        layer: WatermarkLayer,
        canvasWidth: Float,
        canvasHeight: Float,
        imageBitmap: Bitmap?,
    ): Pair<Float, Float> {
        if (!layer.enabled) return 0f to 0f
        val unit = min(canvasWidth, canvasHeight)
        if (layer.kind == "image" && imageBitmap != null && !imageBitmap.isRecycled) {
            val targetMax = unit * 0.18f * layer.scale.coerceIn(0.3f, 2.5f)
            val ratio = imageBitmap.width.toFloat() / imageBitmap.height.coerceAtLeast(1).toFloat()
            return if (ratio >= 1f) {
                targetMax to (targetMax / ratio)
            } else {
                (targetMax * ratio) to targetMax
            }
        }
        val label = layer.text?.trim().orEmpty()
        if (label.isEmpty()) return 0f to 0f
        val textSizePx = (unit * 0.034f * layer.scale.coerceIn(0.3f, 2.5f)).coerceIn(10f, 72f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = Typeface.create(bengaliTypeface, Typeface.BOLD)
        }
        val textW = paint.measureText(label)
        val padX = textSizePx * 0.7f
        val padY = textSizePx * 0.42f
        return (textW + padX * 2f) to (textSizePx + padY * 2f)
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bitmap: Bitmap, dstW: Float, dstH: Float) {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val scale = maxOf(dstW / srcW, dstH / srcH)
        val cropW = (dstW / scale).toInt().coerceIn(1, bitmap.width)
        val cropH = (dstH / scale).toInt().coerceIn(1, bitmap.height)
        val left = ((bitmap.width - cropW) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - cropH) / 2).coerceAtLeast(0)
        val srcRect = Rect(left, top, left + cropW, top + cropH)
        val dstRect = RectF(0f, 0f, dstW, dstH)
        canvas.drawBitmap(bitmap, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - (inv * inv * inv)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        fun decodeBitmapSafely(path: String?, maxDimension: Int = 1280): Result<Bitmap?> {
            if (path.isNullOrBlank()) return Result.success(null)
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    error("Unable to decode image file at $path")
                }
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(path, decodeOpts)
                    ?: error("Image decoder returned null for $path")
            }
        }
    }
}

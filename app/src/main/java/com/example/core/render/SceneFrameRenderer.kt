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
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.model.MotionPreset
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.core.model.SceneTransition
import com.example.core.model.SubtitleCue
import com.example.core.model.WatermarkLayer
import com.example.core.model.WatermarkSettings
import com.example.data.templates.TemplateCatalogLoader
import com.example.data.templates.TemplateVisualStyle
import com.example.feature.subtitles.SrtEngine
import kotlin.math.PI
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
 * Single deterministic motion-graphics rendering engine shared across:
 * 1) Template gallery live previews (all 20 templates)
 * 2) Scene editor & real-time 30fps video preview
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
            motionPreset = MotionPreset.SCALE_IN,
            transitionIn = SceneTransition.NONE,
            badgeLabel = templateId.uppercase(),
            highlightWord = sampleHeadline.split(" ").firstOrNull().orEmpty(),
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

        val resolvedScenes = if (input.overrideScene != null) {
            listOf(input.overrideScene)
        } else {
            resolveEffectiveScenes(project)
        }

        val currentIdx = findSceneIndexAt(resolvedScenes, input.timeMs)
        val currentScene = resolvedScenes[currentIdx]
        val previousScene = if (currentIdx > 0) resolvedScenes[currentIdx - 1] else null

        val sceneElapsed = (input.timeMs - currentScene.startMs).coerceAtLeast(0L)
        val transitionDurationMs = 380L
        val inTransition = previousScene != null &&
            !currentScene.reducedMotion &&
            currentScene.transitionIn != SceneTransition.NONE &&
            sceneElapsed in 0L until transitionDurationMs

        if (inTransition && previousScene != null) {
            val t = easeOutCubic((sceneElapsed.toFloat() / transitionDurationMs.toFloat()).coerceIn(0f, 1f))
            when (currentScene.transitionIn) {
                SceneTransition.CROSSFADE -> {
                    renderSingleSceneLayer(canvas, w, h, previousScene, input.timeMs, input.backgroundBitmaps, 1f - t)
                    renderSingleSceneLayer(canvas, w, h, currentScene, input.timeMs, input.backgroundBitmaps, t)
                }

                SceneTransition.SLIDE_UP -> {
                    canvas.save()
                    canvas.translate(0f, -h * 0.14f * t)
                    renderSingleSceneLayer(canvas, w, h, previousScene, input.timeMs, input.backgroundBitmaps, 1f - t * 0.65f)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(0f, h * (1f - t))
                    renderSingleSceneLayer(canvas, w, h, currentScene, input.timeMs, input.backgroundBitmaps, 1f)
                    canvas.restore()
                }

                SceneTransition.WIPE_RIGHT -> {
                    renderSingleSceneLayer(canvas, w, h, previousScene, input.timeMs, input.backgroundBitmaps, 1f)
                    val revealRight = w * t
                    canvas.save()
                    canvas.clipRect(0f, 0f, revealRight, h)
                    renderSingleSceneLayer(canvas, w, h, currentScene, input.timeMs, input.backgroundBitmaps, 1f)
                    canvas.restore()

                    val style = TemplateCatalogLoader.visualStyleFor(currentScene.templateId)
                    val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = withAlpha(style.accentColor, ((1f - t) * 230).toInt())
                        strokeWidth = min(w, h) * 0.012f
                    }
                    canvas.drawLine(revealRight, 0f, revealRight, h, edgePaint)
                }

                SceneTransition.ZOOM_THROUGH -> {
                    canvas.save()
                    val outScale = 1f - 0.08f * t
                    canvas.scale(outScale, outScale, w * 0.5f, h * 0.5f)
                    renderSingleSceneLayer(canvas, w, h, previousScene, input.timeMs, input.backgroundBitmaps, 1f - t)
                    canvas.restore()

                    canvas.save()
                    val inScale = 1.16f - 0.16f * t
                    canvas.scale(inScale, inScale, w * 0.5f, h * 0.5f)
                    renderSingleSceneLayer(canvas, w, h, currentScene, input.timeMs, input.backgroundBitmaps, t)
                    canvas.restore()
                }

                SceneTransition.NONE -> {
                    renderSingleSceneLayer(canvas, w, h, currentScene, input.timeMs, input.backgroundBitmaps, 1f)
                }
            }
        } else {
            renderSingleSceneLayer(canvas, w, h, currentScene, input.timeMs, input.backgroundBitmaps, 1f)
        }

        // Active SRT Subtitle Overlay with crisp entrance animation
        val syncOffset = project.audioAsset?.srtSyncOffsetMs ?: 0L
        val activeCue = input.overrideSubtitle
            ?: SrtEngine.activeCueAt(project.subtitleCues, input.timeMs, syncOffset)
        if (activeCue != null && activeCue.text.isNotBlank()) {
            val cueElapsed = ((input.timeMs - syncOffset) - activeCue.startMs).coerceAtLeast(0L)
            val subEnter = if (currentScene.reducedMotion) 1f else easeOutCubic((cueElapsed / 200f).coerceIn(0f, 1f))
            val style = TemplateCatalogLoader.visualStyleFor(currentScene.templateId)
            drawSubtitleOverlay(canvas, w, h, activeCue.text, subEnter, style.accentColor)
        }

        // Watermarks (User & Optional Developer) — ONLY rendered when enabled == true
        drawWatermarks(
            canvas = canvas,
            w = w,
            h = h,
            settings = project.watermarkSettings,
            timeMs = input.timeMs,
            userBitmap = input.userWatermarkBitmap,
        )
    }

    private fun renderSingleSceneLayer(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneItem,
        timeMs: Long,
        backgroundBitmaps: Map<String, Bitmap>,
        layerAlpha: Float,
    ) {
        val clampedAlpha = layerAlpha.coerceIn(0f, 1f)
        if (clampedAlpha <= 0.01f) return

        val saveLayerCount = if (clampedAlpha < 0.999f) {
            canvas.saveLayerAlpha(0f, 0f, w, h, (clampedAlpha * 255).toInt())
        } else {
            canvas.save()
        }

        val style = TemplateCatalogLoader.visualStyleFor(scene.templateId)
        val sceneDuration = scene.durationMs.coerceAtLeast(500L)
        val sceneElapsed = (timeMs - scene.startMs).coerceAtLeast(0L)
        val sceneProgress = (sceneElapsed.toFloat() / sceneDuration.toFloat()).coerceIn(0f, 1f)

        val enterLinear = if (scene.reducedMotion || scene.motionPreset == MotionPreset.NONE) {
            1f
        } else {
            (sceneElapsed / 680f).coerceIn(0f, 1f)
        }
        val enterEased = easeOutCubic(enterLinear)
        val springPop = if (scene.reducedMotion || scene.motionPreset == MotionPreset.NONE) 1f else easeOutBack(enterLinear)
        val ambientRad = if (scene.reducedMotion) 0f else ((timeMs % 6000L) / 6000f) * TWO_PI

        // 1. Animated template backdrop + vector motion graphics
        drawTemplateBackdrop(
            canvas = canvas,
            w = w,
            h = h,
            scene = scene,
            style = style,
            enterEased = enterEased,
            springPop = springPop,
            sceneProgress = sceneProgress,
            ambientRad = ambientRad,
            timeMs = timeMs,
        )

        // 2. Optional scene background photo with deterministic Ken Burns pan & zoom
        val bgPath = scene.backgroundImagePath
        val bgBitmap = if (!bgPath.isNullOrBlank()) backgroundBitmaps[bgPath] else null
        if (bgBitmap != null && !bgBitmap.isRecycled) {
            drawKenBurnsBitmap(
                canvas = canvas,
                bitmap = bgBitmap,
                dstW = w,
                dstH = h,
                sceneProgress = if (scene.reducedMotion) 0f else sceneProgress,
                sceneIndex = scene.orderIndex,
            )
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(
                        Color.argb(135, 10, 15, 30),
                        Color.argb(170, 10, 15, 30),
                        Color.argb(210, 10, 15, 30),
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w, h, scrimPaint)
        }

        // 3. Kinetic foreground template card + Bengali headline, keyword highlight, caption, progress
        drawSceneContent(
            canvas = canvas,
            w = w,
            h = h,
            scene = scene,
            style = style,
            enterEased = enterEased,
            springPop = springPop,
            sceneProgress = sceneProgress,
            ambientRad = ambientRad,
        )

        canvas.restoreToCount(saveLayerCount)
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

    private fun resolveEffectiveScenes(project: Project): List<SceneItem> {
        if (project.scenes.isNotEmpty()) {
            return project.scenes.sortedBy { it.orderIndex }
        }
        val derived = SrtEngine.autoMatchScenesFromCues(
            cues = project.subtitleCues,
            script = project.script,
            templateId = project.templateId,
            varyTemplates = true,
        )
        if (derived.isNotEmpty()) return derived

        val firstLine = project.script.lines().firstOrNull { it.isNotBlank() }?.trim()
            ?: project.name
        return listOf(
            SceneItem(
                headline = firstLine,
                caption = project.aspectRatio.label,
                startMs = 0L,
                endMs = project.totalDurationMs,
                templateId = project.templateId,
                motionPreset = MotionPreset.WORD_STAGGER,
                badgeLabel = "SCENE 01",
            ),
        )
    }

    private fun findSceneIndexAt(scenes: List<SceneItem>, timeMs: Long): Int {
        val exact = scenes.indexOfFirst { timeMs in it.startMs..it.endMs }
        if (exact >= 0) return exact
        val preceding = scenes.indexOfLast { timeMs >= it.startMs }
        if (preceding >= 0) return preceding
        return 0
    }

    private fun drawTemplateBackdrop(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneItem,
        style: TemplateVisualStyle,
        enterEased: Float,
        springPop: Float,
        sceneProgress: Float,
        ambientRad: Float,
        timeMs: Long,
    ) {
        val unit = min(w, h)
        val shift = sin(ambientRad) * unit * 0.08f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                shift,
                0f,
                w - shift,
                h,
                style.bgStartColor,
                style.bgEndColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val floatY = sin(ambientRad) * unit * 0.025f
        val floatX = cos(ambientRad) * unit * 0.025f

        when (style.motif) {
            "shattered-glass" -> {
                val expand = 0.65f + 0.35f * enterEased
                paint.color = withAlpha(style.accentColor, 58)
                val poly1 = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.48f * expand + floatX, 0f)
                    lineTo(w * 0.24f + floatX * 0.5f, h * 0.36f * expand + floatY)
                    close()
                }
                canvas.drawPath(poly1, paint)
                paint.color = withAlpha(style.secondaryAccentColor, 65)
                val poly2 = Path().apply {
                    moveTo(w, h * 0.1f + floatY)
                    lineTo(w, h * 0.68f)
                    lineTo(w * (1f - 0.34f * expand) - floatX, h * 0.43f - floatY)
                    close()
                }
                canvas.drawPath(poly2, paint)

                // Glint sweep line
                val glintX = -w * 0.3f + (w * 1.6f * sceneProgress)
                paint.shader = LinearGradient(
                    glintX, 0f, glintX + w * 0.18f, h * 0.5f,
                    intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, 48), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null

                paint.color = withAlpha(Color.WHITE, 46)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.005f
                canvas.drawPath(poly1, paint)
                canvas.drawPath(poly2, paint)
            }

            "detective-board" -> {
                paint.color = withAlpha(style.secondaryAccentColor, 55)
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
                val p1x = w * 0.14f
                val p1y = h * 0.16f
                val p2x = w * 0.84f
                val p2y = h * 0.24f
                val p3x = w * 0.2f
                val p3y = h * 0.82f

                // Progressive string drawing across sceneProgress
                val drawT = (enterEased * 0.65f + sceneProgress * 0.35f).coerceIn(0f, 1f)
                paint.color = style.accentColor
                paint.strokeWidth = unit * 0.0075f
                paint.style = Paint.Style.STROKE
                if (drawT <= 0.5f) {
                    val seg = drawT * 2f
                    canvas.drawLine(p1x, p1y, lerp(p1x, p2x, seg), lerp(p1y, p2y, seg), paint)
                } else {
                    canvas.drawLine(p1x, p1y, p2x, p2y, paint)
                    val seg = (drawT - 0.5f) * 2f
                    canvas.drawLine(p2x, p2y, lerp(p2x, p3x, seg), lerp(p2y, p3y, seg), paint)
                }

                paint.style = Paint.Style.FILL
                val pinR = unit * 0.02f * springPop.coerceIn(0.2f, 1.3f)
                canvas.drawCircle(p1x, p1y, pinR, paint)
                if (drawT > 0.35f) canvas.drawCircle(p2x, p2y, pinR, paint)
                if (drawT > 0.85f) canvas.drawCircle(p3x, p3y, pinR, paint)
            }

            "cosmos-story" -> {
                val driftY = ((timeMs % 8000L) / 8000f) * h
                paint.color = withAlpha(Color.WHITE, 210)
                for (i in 0..32) {
                    val depth = 0.4f + (i % 3) * 0.35f
                    val sx = ((i * 73 % 97) / 97f) * w
                    val sy = ((((i * 41 % 89) / 89f) * h) + driftY * depth) % h
                    val twinkle = (0.55f + 0.45f * sin(ambientRad * 2f + i)).coerceIn(0.2f, 1f)
                    paint.alpha = (twinkle * 220).toInt()
                    val r = unit * (0.003f + (i % 3) * 0.0022f)
                    canvas.drawCircle(sx, sy, r, paint)
                }
                paint.alpha = 255
                val planetX = w * 0.8f + floatX * 0.6f
                val planetY = h * 0.17f + floatY * 0.6f
                paint.shader = RadialGradient(
                    planetX - unit * 0.04f,
                    planetY - unit * 0.04f,
                    unit * 0.22f,
                    withAlpha(style.accentColor, 175),
                    withAlpha(style.secondaryAccentColor, 20),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(planetX, planetY, unit * 0.19f, paint)
                paint.shader = null

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.006f
                paint.color = withAlpha(style.secondaryAccentColor, 175)
                canvas.save()
                canvas.rotate(-14f + sin(ambientRad) * 4f, planetX, planetY)
                canvas.drawOval(
                    RectF(planetX - unit * 0.26f, planetY - unit * 0.06f, planetX + unit * 0.26f, planetY + unit * 0.06f),
                    paint,
                )
                // Orbiting moon dot
                val moonX = planetX + cos(ambientRad * 2f) * unit * 0.26f
                val moonY = planetY + sin(ambientRad * 2f) * unit * 0.06f
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(moonX, moonY, unit * 0.012f, paint)
                canvas.restore()
            }

            "mint-glass", "soft-gradient" -> {
                val orb1X = w * 0.2f + cos(ambientRad) * unit * 0.08f
                val orb1Y = h * 0.22f + sin(ambientRad) * unit * 0.06f
                paint.color = withAlpha(style.secondaryAccentColor, 85)
                canvas.drawCircle(orb1X, orb1Y, unit * 0.28f, paint)

                val orb2X = w * 0.82f - cos(ambientRad) * unit * 0.08f
                val orb2Y = h * 0.78f - sin(ambientRad) * unit * 0.06f
                paint.color = withAlpha(style.accentColor, 72)
                canvas.drawCircle(orb2X, orb2Y, unit * 0.30f, paint)

                // Floating glass particles
                paint.color = withAlpha(Color.WHITE, 46)
                for (i in 0..7) {
                    val px = ((i * 37 + 15) % 100) / 100f * w
                    val py = (h - ((timeMs * 0.03f * (1 + i % 3) + i * 95f) % (h + 60f)))
                    canvas.drawCircle(px, py, unit * (0.01f + (i % 3) * 0.006f), paint)
                }
            }

            "blueprint" -> {
                paint.color = withAlpha(style.accentColor, 44)
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
                // Animated perimeter draw
                paint.color = withAlpha(style.secondaryAccentColor, 165)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = unit * 0.005f
                val margin = w * 0.05f
                val topDraw = lerp(margin, w - margin, enterEased)
                canvas.drawLine(margin, h * 0.05f, topDraw, h * 0.05f, paint)
                canvas.drawLine(w - topDraw, h * 0.95f, w - margin, h * 0.95f, paint)
                canvas.drawRect(margin, h * 0.05f, w - margin, h * 0.95f, paint.apply { alpha = (95 * enterEased).toInt() })

                // Scanning crosshair
                paint.alpha = 140
                val scanY = h * (0.12f + 0.76f * sceneProgress)
                canvas.drawLine(margin, scanY, w - margin, scanY, paint)
            }

            "comic-pop" -> {
                // Rotating starburst rays
                val cx = w * 0.5f
                val cy = h * 0.45f
                val radius = maxOf(w, h) * 0.85f
                paint.color = withAlpha(Color.WHITE, 36)
                val rayCount = 12
                val baseAngle = ambientRad * 18f
                for (i in 0 until rayCount) {
                    val a1 = Math.toRadians((baseAngle + i * (360f / rayCount)).toDouble())
                    val a2 = Math.toRadians((baseAngle + i * (360f / rayCount) + 12f).toDouble())
                    val ray = Path().apply {
                        moveTo(cx, cy)
                        lineTo((cx + cos(a1) * radius).toFloat(), (cy + sin(a1) * radius).toFloat())
                        lineTo((cx + cos(a2) * radius).toFloat(), (cy + sin(a2) * radius).toFloat())
                        close()
                    }
                    canvas.drawPath(ray, paint)
                }

                paint.color = withAlpha(Color.WHITE, 55)
                val dotStep = (unit * 0.075f).coerceAtLeast(18f)
                val pulseDot = unit * (0.007f + 0.002f * sin(ambientRad * 2f))
                var dx = dotStep * 0.5f
                while (dx < w) {
                    var dy = dotStep * 0.5f
                    while (dy < h) {
                        canvas.drawCircle(dx, dy, pulseDot, paint)
                        dy += dotStep
                    }
                    dx += dotStep
                }
            }

            "paper-cut" -> {
                val waveShift = sin(ambientRad) * h * 0.03f
                paint.color = withAlpha(style.accentColor, 205)
                val wave1 = Path().apply {
                    moveTo(0f, h * 0.82f + waveShift)
                    quadTo(w * 0.35f, h * 0.70f - waveShift, w * 0.65f, h * 0.84f + waveShift)
                    quadTo(w * 0.85f, h * 0.92f - waveShift, w, h * 0.79f + waveShift)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                canvas.drawPath(wave1, paint)
                paint.color = withAlpha(style.secondaryAccentColor, 200)
                val wave2 = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h * 0.14f - waveShift)
                    quadTo(w * 0.5f, h * 0.24f + waveShift, 0f, h * 0.12f - waveShift)
                    close()
                }
                canvas.drawPath(wave2, paint)
            }

            "retro-terminal" -> {
                paint.color = withAlpha(style.accentColor, 26)
                val lineGap = (unit * 0.015f).coerceAtLeast(4f)
                var y = 0f
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += lineGap
                }
                // Rolling CRT bright beam
                val beamY = (timeMs % 2600L) / 2600f * h
                paint.shader = LinearGradient(
                    0f, beamY - unit * 0.08f, 0f, beamY + unit * 0.08f,
                    intArrayOf(Color.TRANSPARENT, withAlpha(style.accentColor, 55), Color.TRANSPARENT),
                    null,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, beamY - unit * 0.08f, w, beamY + unit * 0.08f, paint)
                paint.shader = null
            }

            "split-screen" -> {
                val splitSlide = enterEased
                paint.color = withAlpha(style.panelColor, 232)
                if (w >= h) {
                    canvas.drawRect(0f, 0f, w * 0.48f * splitSlide, h, paint)
                } else {
                    canvas.drawRect(0f, 0f, w, h * 0.48f * splitSlide, paint)
                }
            }

            "chalkboard" -> {
                paint.style = Paint.Style.STROKE
                paint.color = withAlpha(Color.WHITE, 148)
                paint.strokeWidth = unit * 0.006f
                paint.pathEffect = DashPathEffect(floatArrayOf(unit * 0.03f, unit * 0.015f), (timeMs * 0.02f) % 60f)
                canvas.drawRoundRect(
                    RectF(w * 0.06f, h * 0.06f, w * 0.94f, h * 0.94f),
                    unit * 0.03f,
                    unit * 0.03f,
                    paint,
                )
            }

            "3d-object-stage" -> {
                val angle = (enterEased * 0.4f) + ambientRad
                val hoverY = sin(angle * 2f) * unit * 0.025f
                val cx = w * 0.5f + cos(angle) * unit * 0.045f
                val cy = h * 0.78f
                val shadowScale = 1f - (hoverY / (unit * 0.1f))
                paint.color = withAlpha(style.accentColor, 72)
                canvas.drawOval(
                    RectF(
                        cx - unit * 0.34f * shadowScale,
                        cy - unit * 0.05f,
                        cx + unit * 0.34f * shadowScale,
                        cy + unit * 0.05f,
                    ),
                    paint,
                )
                paint.color = withAlpha(style.secondaryAccentColor, 95)
                val tiltOffset = sin(angle) * unit * 0.028f
                canvas.drawRoundRect(
                    RectF(w * 0.14f + tiltOffset, h * 0.23f + hoverY, w * 0.86f + tiltOffset, h * 0.69f + hoverY),
                    unit * 0.045f,
                    unit * 0.045f,
                    paint,
                )
                // Orbiting 2.5D orb
                paint.color = withAlpha(style.accentColor, 195)
                canvas.drawCircle(
                    w * 0.5f + cos(angle) * w * 0.38f,
                    h * 0.16f + sin(angle) * unit * 0.03f,
                    unit * 0.022f,
                    paint,
                )
            }

            "documentary" -> {
                val barSlide = enterEased
                paint.color = Color.BLACK
                canvas.drawRect(0f, 0f, w, h * 0.09f * barSlide, paint)
                canvas.drawRect(0f, h - h * 0.09f * barSlide, w, h, paint)

                // Subtle warm cinematic light leak sweep
                val leakX = w * (0.2f + 0.6f * sceneProgress)
                paint.shader = RadialGradient(
                    leakX, h * 0.25f, unit * 0.45f,
                    withAlpha(style.accentColor, 48), Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(leakX, h * 0.25f, unit * 0.45f, paint)
                paint.shader = null
            }

            "folded-photo-story" -> {
                // Two layered polaroid paper backdrops unfolding into view
                paint.color = withAlpha(Color.WHITE, 34)
                canvas.save()
                canvas.rotate(lerp(-10f, -3.5f, enterEased), w * 0.5f, h * 0.5f)
                canvas.drawRoundRect(RectF(w * 0.12f, h * 0.18f, w * 0.88f, h * 0.74f), unit * 0.03f, unit * 0.03f, paint)
                canvas.restore()

                paint.color = withAlpha(style.accentColor, 48)
                canvas.save()
                canvas.rotate(lerp(9f, 2.5f, enterEased), w * 0.5f, h * 0.5f)
                canvas.drawRoundRect(RectF(w * 0.10f, h * 0.20f, w * 0.90f, h * 0.73f), unit * 0.03f, unit * 0.03f, paint)
                canvas.restore()
            }

            "kinetic-type" -> {
                // Drifting background motion rhythm bars & watermark typography
                val eqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(style.accentColor, 72)
                }
                val barCount = 14
                val barGap = w / barCount
                for (i in 0 until barCount) {
                    val barH = unit * (0.04f + 0.06f * kotlin.math.abs(sin(ambientRad * 2f + i * 0.7f)))
                    canvas.drawRoundRect(
                        RectF(i * barGap + barGap * 0.22f, h * 0.13f - barH * 0.5f, (i + 1) * barGap - barGap * 0.22f, h * 0.13f + barH * 0.5f),
                        unit * 0.01f,
                        unit * 0.01f,
                        eqPaint,
                    )
                }
                val bgWordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(Color.WHITE, 14)
                    textSize = unit * 0.18f
                    typeface = Typeface.create(displayTypeface, Typeface.BOLD)
                }
                val drift = -w * 0.25f * sceneProgress
                canvas.save()
                canvas.rotate(-8f, w * 0.5f, h * 0.5f)
                canvas.drawText("MOTION  •  BANGLA  •  STUDIO", drift, h * 0.45f, bgWordPaint)
                canvas.restore()
            }

            "interview-news" -> {
                // Broadcast news grid lines + top LIVE badge + scrolling bottom ticker
                paint.color = withAlpha(style.accentColor, 34)
                paint.strokeWidth = (unit * 0.002f).coerceAtLeast(1f)
                canvas.drawLine(0f, h * 0.14f, w, h * 0.14f, paint)

                // Pulsing LIVE dot at top-left
                val livePulse = 0.45f + 0.55f * kotlin.math.abs(sin(ambientRad * 3f))
                paint.color = withAlpha(Color.parseColor("#EF4444"), (livePulse * 255).toInt())
                canvas.drawCircle(w * 0.09f, h * 0.09f, unit * 0.014f, paint)
                paint.color = withAlpha(Color.WHITE, 220)
                paint.textSize = unit * 0.026f
                paint.typeface = Typeface.create(displayTypeface, Typeface.BOLD)
                canvas.drawText("LIVE STORY", w * 0.125f, h * 0.099f, paint)

                // Moving bottom broadcast stripe
                paint.color = withAlpha(style.panelColor, 210)
                canvas.drawRect(0f, h * 0.875f, w, h * 0.91f, paint)
                paint.color = style.accentColor
                val barLen = w * 0.35f
                val bx = ((timeMs % 2800L) / 2800f) * (w + barLen) - barLen
                canvas.drawRect(bx, h * 0.875f, bx + barLen, h * 0.91f, paint)
            }

            "magazine-editorial" -> {
                val ruleT = enterEased
                paint.color = withAlpha(style.accentColor, 200)
                paint.strokeWidth = unit * 0.008f
                canvas.drawLine(w * 0.08f, h * 0.16f, lerp(w * 0.08f, w * 0.92f, ruleT), h * 0.16f, paint)
                paint.color = withAlpha(style.secondaryAccentColor, 95)
                paint.strokeWidth = unit * 0.003f
                canvas.drawLine(w * 0.08f, h * 0.77f, lerp(w * 0.08f, w * 0.92f, ruleT), h * 0.77f, paint)
            }

            else -> {
                val orbR = unit * (0.20f + 0.03f * sin(ambientRad))
                paint.color = withAlpha(style.accentColor, 36)
                canvas.drawCircle(w * 0.86f + floatX, h * 0.16f + floatY, orbR, paint)
                paint.color = withAlpha(style.secondaryAccentColor, 28)
                canvas.drawCircle(w * 0.14f - floatX, h * 0.80f - floatY, orbR * 0.8f, paint)
            }
        }
    }

    private fun drawSceneContent(
        canvas: Canvas,
        w: Float,
        h: Float,
        scene: SceneItem,
        style: TemplateVisualStyle,
        enterEased: Float,
        springPop: Float,
        sceneProgress: Float,
        ambientRad: Float,
    ) {
        val unit = min(w, h)
        val alpha = (if (scene.motionPreset == MotionPreset.NONE || scene.reducedMotion) 1f else enterEased).coerceIn(0f, 1f)

        val dx = when (scene.motionPreset) {
            MotionPreset.SLIDE_LEFT -> (1f - enterEased) * w * 0.18f
            MotionPreset.SLIDE_RIGHT -> -(1f - enterEased) * w * 0.18f
            MotionPreset.PARALLAX -> (0.5f - sceneProgress) * w * 0.045f
            else -> 0f
        }
        val dy = when (scene.motionPreset) {
            MotionPreset.SLIDE_UP -> (1f - enterEased) * h * 0.12f
            MotionPreset.SLIDE_DOWN -> -(1f - enterEased) * h * 0.12f
            else -> if (!scene.reducedMotion) sin(ambientRad) * unit * 0.007f else 0f
        }
        val scale = when (scene.motionPreset) {
            MotionPreset.SCALE_IN -> 0.82f + (0.18f * springPop)
            MotionPreset.PARALLAX -> 1f + 0.02f * sceneProgress
            else -> 1f
        }

        canvas.save()
        canvas.translate(dx, dy)
        if (scale != 1f) {
            canvas.scale(scale, scale, w * 0.5f, h * 0.45f)
        }

        val cardRect = when (style.motif) {
            "interview-news" -> RectF(w * 0.06f, h * 0.54f, w * 0.94f, h * 0.84f)
            "saas-product", "retro-terminal", "detective-board", "folded-photo-story", "mint-glass", "comic-pop", "magazine-editorial" ->
                RectF(w * 0.08f, h * 0.21f, w * 0.92f, h * 0.72f)
            else -> RectF(w * 0.08f, h * 0.23f, w * 0.92f, h * 0.70f)
        }

        // Animated card surface & chrome
        if (style.motif != "minimal-text" && style.motif != "kinetic-type" && style.motif != "documentary") {
            if (style.motif == "comic-pop") {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(Color.BLACK, (210 * alpha).toInt())
                }
                val offset = unit * 0.02f
                canvas.drawRoundRect(
                    RectF(cardRect.left + offset, cardRect.top + offset, cardRect.right + offset, cardRect.bottom + offset),
                    unit * 0.04f,
                    unit * 0.04f,
                    shadowPaint,
                )
            }

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
                    color = withAlpha(style.accentColor, (185 * alpha).toInt())
                    strokeWidth = (unit * 0.0055f).coerceAtLeast(1.5f)
                }
                canvas.drawRoundRect(cardRect, radius, radius, borderPaint)
            }

            if (style.motif == "saas-product" || style.motif == "retro-terminal") {
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val dotY = cardRect.top + unit * 0.04f
                val dotR = unit * 0.012f
                val colors = listOf(Color.parseColor("#EF4444"), Color.parseColor("#F59E0B"), Color.parseColor("#22C55E"))
                colors.forEachIndexed { idx, col ->
                    dotPaint.color = withAlpha(col, (230 * alpha).toInt())
                    canvas.drawCircle(cardRect.left + unit * (0.05f + idx * 0.036f), dotY, dotR, dotPaint)
                }
                if (style.motif == "saas-product") {
                    // Mini animated analytics bars inside SaaS card footer
                    val barAreaW = cardRect.width() * 0.24f
                    val startX = cardRect.right - barAreaW - unit * 0.04f
                    val baseY = cardRect.top + unit * 0.055f
                    val barW = unit * 0.014f
                    for (b in 0..4) {
                        val hFactor = (0.35f + 0.65f * kotlin.math.abs(sin(ambientRad * 2f + b * 0.8f))) * enterEased
                        dotPaint.color = withAlpha(style.accentColor, (190 * alpha).toInt())
                        canvas.drawRoundRect(
                            RectF(
                                startX + b * barW * 1.4f,
                                baseY - unit * 0.032f * hFactor,
                                startX + b * barW * 1.4f + barW,
                                baseY,
                            ),
                            unit * 0.005f,
                            unit * 0.005f,
                            dotPaint,
                        )
                    }
                }
            } else if (style.motif == "interview-news") {
                val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(style.accentColor, (255 * alpha).toInt())
                }
                canvas.drawRect(cardRect.left, cardRect.top, cardRect.left + unit * 0.024f, cardRect.bottom, tagPaint)
            }
        }

        // Animated Scene Badge Pill (e.g., SCENE 01)
        val badgeText = scene.badgeLabel.trim()
        if (badgeText.isNotEmpty()) {
            val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.bgStartColor
                textSize = (unit * 0.026f).coerceIn(10f, 30f)
                typeface = Typeface.create(displayTypeface, Typeface.BOLD)
            }
            val bw = badgePaint.measureText(badgeText) + unit * 0.05f
            val bh = unit * 0.052f
            val bx = cardRect.centerX() - bw / 2f
            val by = cardRect.top - bh * 0.55f + (1f - enterEased) * unit * 0.02f
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(style.accentColor, (245 * alpha).toInt())
            }
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh * 0.5f, bh * 0.5f, pillPaint)
            val fm = badgePaint.fontMetrics
            val baseline = by + (bh - (fm.bottom - fm.top)) / 2f - fm.top
            badgePaint.alpha = (255 * alpha).toInt()
            canvas.drawText(badgeText, bx + unit * 0.025f, baseline, badgePaint)
        }

        // Animated Accent + Live Scene Progress Track
        val trackW = cardRect.width() * 0.30f
        val trackLeft = cardRect.centerX() - trackW / 2f
        val trackTop = cardRect.top + unit * 0.058f
        val trackBottom = trackTop + unit * 0.011f
        val trackBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(style.accentColor, (70 * alpha).toInt())
        }
        canvas.drawRoundRect(RectF(trackLeft, trackTop, trackLeft + trackW, trackBottom), unit * 0.01f, unit * 0.01f, trackBg)

        val fillFraction = when {
            scene.motionPreset == MotionPreset.DRAW_LINE -> (0.25f + 0.75f * enterEased) * (0.35f + 0.65f * sceneProgress)
            else -> (0.2f + 0.8f * sceneProgress)
        }.coerceIn(0.08f, 1f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(style.accentColor, (250 * alpha).toInt())
        }
        canvas.drawRoundRect(
            RectF(trackLeft, trackTop, trackLeft + trackW * fillFraction, trackBottom),
            unit * 0.01f,
            unit * 0.01f,
            barPaint,
        )

        // Headline text (preserves full Bengali conjunct shaping via StaticLayout + Spannable highlight!)
        val rawHeadline = scene.headline.ifBlank { "বাংলা-মোশন স্টুডিও" }
        val words = rawHeadline.split(Regex("\\s+")).filter { it.isNotBlank() }
        val isWordReveal = (scene.motionPreset == MotionPreset.WORD_STAGGER || scene.motionPreset == MotionPreset.REVEAL) &&
            !scene.reducedMotion

        val visibleWords = if (isWordReveal && enterEased < 0.95f && words.size > 1) {
            val count = ((words.size * enterEased).toInt() + 1).coerceIn(1, words.size)
            words.take(count)
        } else {
            words
        }

        val displayText = visibleWords.joinToString(" ").ifBlank { rawHeadline }
        val spannedHeadline = SpannableStringBuilder(displayText)

        // Highlight target keyword (or active sweeping word) cleanly without breaking Bengali ligatures
        val targetHighlight = scene.highlightWord.trim().takeIf { it.isNotEmpty() && displayText.contains(it) }
            ?: visibleWords.getOrNull((sceneProgress * visibleWords.size).toInt().coerceIn(0, (visibleWords.size - 1).coerceAtLeast(0)))

        if (!scene.reducedMotion && !targetHighlight.isNullOrBlank()) {
            val idx = displayText.indexOf(targetHighlight)
            if (idx >= 0) {
                val end = (idx + targetHighlight.length).coerceAtMost(spannedHeadline.length)
                spannedHeadline.setSpan(
                    BackgroundColorSpan(withAlpha(style.accentColor, (90 * alpha).toInt())),
                    idx,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                spannedHeadline.setSpan(
                    ForegroundColorSpan(withAlpha(style.accentColor, (255 * alpha).toInt())),
                    idx,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        val textPad = unit * 0.06f
        val textWidth = (cardRect.width() - textPad * 2f).toInt().coerceAtLeast(80)
        val headlineSize = (unit * 0.064f).coerceIn(16f, 72f)
        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(style.headlineColor, (255 * alpha).toInt())
            textSize = headlineSize
            typeface = Typeface.create(bengaliTypeface, Typeface.BOLD)
        }
        val headlineLayout = StaticLayout.Builder
            .obtain(spannedHeadline, 0, spannedHeadline.length, headlinePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setIncludePad(true)
            .build()

        val headlineTop = cardRect.top + unit * 0.10f
        canvas.save()
        canvas.translate(cardRect.left + textPad, headlineTop)
        headlineLayout.draw(canvas)
        canvas.restore()

        // Caption text with slight staggered delayed entrance
        if (scene.caption.isNotBlank()) {
            val captionEnter = if (scene.reducedMotion || scene.motionPreset == MotionPreset.NONE) {
                1f
            } else {
                ((enterEased - 0.18f) / 0.82f).coerceIn(0f, 1f)
            }
            val captionAlpha = (alpha * captionEnter * 240).toInt()
            val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(style.captionColor, captionAlpha)
                textSize = (unit * 0.037f).coerceIn(12f, 42f)
                typeface = bengaliTypeface
            }
            val captionLayout = StaticLayout.Builder
                .obtain(scene.caption, 0, scene.caption.length, captionPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .setIncludePad(true)
                .build()
            val captionOffsetY = (1f - captionEnter) * unit * 0.025f
            val captionTop = (headlineTop + headlineLayout.height + unit * 0.028f + captionOffsetY)
                .coerceAtMost(cardRect.bottom - captionLayout.height - unit * 0.025f)
            canvas.save()
            canvas.translate(cardRect.left + textPad, captionTop)
            captionLayout.draw(canvas)
            canvas.restore()
        }

        // Terminal blinking cursor for retro-terminal template
        if (style.motif == "retro-terminal" && ((ambientRad / TWO_PI) * 4f).toInt() % 2 == 0) {
            val curPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.accentColor
            }
            val curTop = cardRect.bottom - unit * 0.055f
            canvas.drawRect(cardRect.centerX() - unit * 0.025f, curTop, cardRect.centerX() + unit * 0.025f, curTop + unit * 0.01f, curPaint)
        }

        canvas.restore()
    }

    private fun drawSubtitleOverlay(
        canvas: Canvas,
        w: Float,
        h: Float,
        subtitleText: String,
        enterEased: Float,
        accentColor: Int,
    ) {
        val unit = min(w, h)
        val maxTextW = (w * 0.84f).toInt().coerceAtLeast(80)
        val alphaInt = (255 * enterEased).toInt().coerceIn(0, 255)
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alphaInt, 255, 255, 255)
            textSize = (unit * 0.043f).coerceIn(13f, 50f)
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
        val bottomMargin = h * 0.072f
        val slideOffset = (1f - enterEased) * unit * 0.025f
        val boxTop = (h - bottomMargin - boxH + slideOffset).coerceAtLeast(h * 0.65f)
        val boxLeft = (w - boxW) / 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((206 * enterEased).toInt(), 8, 12, 24)
        }
        val boxRect = RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH)
        canvas.drawRoundRect(boxRect, unit * 0.024f, unit * 0.024f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = withAlpha(accentColor, (125 * enterEased).toInt())
            strokeWidth = (unit * 0.003f).coerceAtLeast(1f)
        }
        canvas.drawRoundRect(boxRect, unit * 0.024f, unit * 0.024f, borderPaint)

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

    private fun drawKenBurnsBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        dstW: Float,
        dstH: Float,
        sceneProgress: Float,
        sceneIndex: Int,
    ) {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val baseScale = maxOf(dstW / srcW, dstH / srcH)
        val kenBurnsScale = baseScale * (1.0f + 0.10f * sceneProgress)
        val cropW = (dstW / kenBurnsScale).toInt().coerceIn(1, bitmap.width)
        val cropH = (dstH / kenBurnsScale).toInt().coerceIn(1, bitmap.height)
        val panDirection = if (sceneIndex % 2 == 0) 1f else -1f
        val maxPanX = ((bitmap.width - cropW) / 2f).coerceAtLeast(0f)
        val left = ((bitmap.width - cropW) / 2f + panDirection * (sceneProgress - 0.5f) * maxPanX)
            .toInt()
            .coerceIn(0, (bitmap.width - cropW).coerceAtLeast(0))
        val top = ((bitmap.height - cropH) / 2).coerceIn(0, (bitmap.height - cropH).coerceAtLeast(0))
        val srcRect = Rect(left, top, left + cropW, top + cropH)
        val dstRect = RectF(0f, 0f, dstW, dstH)
        canvas.drawBitmap(bitmap, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t.coerceIn(0f, 1f)
        return 1f - (inv * inv * inv)
    }

    private fun easeOutBack(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val c1 = 1.35f
        val c3 = c1 + 1f
        val x = clamped - 1f
        return (1f + c3 * x * x * x + c1 * x * x).coerceIn(0f, 1.08f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        private const val TWO_PI = (2.0 * PI).toFloat()

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

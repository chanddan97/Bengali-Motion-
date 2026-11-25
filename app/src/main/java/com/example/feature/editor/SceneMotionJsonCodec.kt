package com.example.feature.editor

import com.example.core.model.MotionPreset
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.core.model.SceneTransition
import com.example.core.model.SubtitleCue
import com.example.feature.subtitles.SrtEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SceneJsonParseResult(
    val scenes: List<SceneItem>,
    val warnings: List<String>,
    val errors: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty() && scenes.isNotEmpty()
}

/**
 * Generates, validates, exports, and imports structured Scene Motion JSON files
 * derived from Script + SRT subtitles. Pure data configuration — never executes scripts or URLs.
 */
object SceneMotionJsonCodec {
    private const val SCHEMA_VERSION = "1.0.0"
    private val ALLOWED_ROOT_KEYS = setOf(
        "schemaVersion",
        "generator",
        "projectName",
        "aspectRatio",
        "defaultTemplateId",
        "scenes",
    )
    private val ALLOWED_SCENE_KEYS = setOf(
        "id",
        "orderIndex",
        "startMs",
        "endMs",
        "templateId",
        "motionPreset",
        "transitionIn",
        "badgeLabel",
        "headline",
        "caption",
        "highlightWord",
        "reducedMotion",
    )

    fun exportProjectSceneJson(project: Project, scenesOverride: List<SceneItem>? = null): String {
        val scenes = scenesOverride ?: project.scenes
        val root = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("generator", "banglamotion-srt-to-scene-json")
            put("projectName", project.name)
            put("aspectRatio", project.aspectRatio.label)
            put("defaultTemplateId", project.templateId)
            val arr = JSONArray()
            scenes.sortedBy { it.orderIndex }.forEachIndexed { idx, scene ->
                arr.put(
                    JSONObject().apply {
                        put("id", scene.id)
                        put("orderIndex", idx)
                        put("startMs", scene.startMs)
                        put("endMs", scene.endMs)
                        put("templateId", scene.templateId)
                        put("motionPreset", scene.motionPreset.id)
                        put("transitionIn", scene.transitionIn.id)
                        put("badgeLabel", scene.badgeLabel)
                        put("headline", scene.headline)
                        put("caption", scene.caption)
                        put("highlightWord", scene.highlightWord)
                        put("reducedMotion", scene.reducedMotion)
                    },
                )
            }
            put("scenes", arr)
        }
        return root.toString(2)
    }

    fun generateFromSrtAndExportJson(
        project: Project,
        varyTemplates: Boolean = true,
    ): Pair<List<SceneItem>, String> {
        val cues: List<SubtitleCue> = if (project.subtitleCues.isNotEmpty()) {
            project.subtitleCues
        } else {
            SrtEngine.generateCuesFromScript(project.script)
        }
        val scenes = SrtEngine.autoMatchScenesFromCues(
            cues = cues,
            script = project.script,
            templateId = project.templateId,
            varyTemplates = varyTemplates,
        )
        val json = exportProjectSceneJson(project, scenes)
        return scenes to json
    }

    fun parseAndValidateSceneJson(
        rawJson: String,
        allowedTemplateIds: Set<String>,
        fallbackTemplateId: String = Project.DEFAULT_TEMPLATE_ID,
    ): SceneJsonParseResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            return SceneJsonParseResult(
                scenes = emptyList(),
                warnings = emptyList(),
                errors = listOf("Invalid JSON syntax: ${err.message}"),
            )
        }

        root.keys().forEach { key ->
            if (key !in ALLOWED_ROOT_KEYS) {
                errors += "Disallowed root property '$key' (strict schema validation)."
            }
        }

        val scenesArray = root.optJSONArray("scenes")
        if (scenesArray == null || scenesArray.length() == 0) {
            errors += "JSON must contain a non-empty 'scenes' array."
            return SceneJsonParseResult(emptyList(), warnings, errors)
        }

        val parsedScenes = mutableListOf<SceneItem>()
        var cursorMs = 0L

        for (i in 0 until scenesArray.length()) {
            val obj = scenesArray.optJSONObject(i)
            if (obj == null) {
                errors += "scenes[$i] is not a JSON object."
                continue
            }

            obj.keys().forEach { key ->
                if (key !in ALLOWED_SCENE_KEYS) {
                    errors += "scenes[$i] contains disallowed field '$key'."
                }
            }

            val headline = obj.optString("headline", "").trim()
            val caption = obj.optString("caption", "").trim()
            if (headline.isEmpty() && caption.isEmpty()) {
                errors += "scenes[$i] must include 'headline' or 'caption'."
                continue
            }

            // Reject remote URLs or script tags inside text fields
            if (containsSuspiciousScriptOrUrl(headline) || containsSuspiciousScriptOrUrl(caption)) {
                errors += "scenes[$i] rejected: URLs or executable markup are not allowed."
                continue
            }

            val rawStart = obj.optLong("startMs", cursorMs).coerceAtLeast(0L)
            val rawEnd = obj.optLong("endMs", rawStart + 3_000L)
            val safeEnd = maxOf(rawEnd, rawStart + 600L)
            cursorMs = safeEnd

            val rawTpl = obj.optString("templateId", fallbackTemplateId).trim()
            val safeTpl = if (allowedTemplateIds.isEmpty() || rawTpl in allowedTemplateIds) {
                rawTpl.ifBlank { fallbackTemplateId }
            } else {
                warnings += "scenes[$i] unknown templateId '$rawTpl' mapped to '$fallbackTemplateId'."
                fallbackTemplateId
            }

            val rawMotion = obj.optString("motionPreset", MotionPreset.WORD_STAGGER.id)
            val motionPreset = MotionPreset.fromId(rawMotion)

            val rawTransition = obj.optString("transitionIn", SceneTransition.CROSSFADE.id)
            val transitionIn = SceneTransition.fromId(rawTransition)

            parsedScenes += SceneItem(
                id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                orderIndex = i,
                headline = headline.ifBlank { caption },
                caption = if (headline.isBlank()) "" else caption,
                startMs = rawStart,
                endMs = safeEnd,
                templateId = safeTpl,
                motionPreset = motionPreset,
                transitionIn = transitionIn,
                badgeLabel = obj.optString("badgeLabel", "").take(24),
                highlightWord = obj.optString("highlightWord", "").take(32),
                reducedMotion = obj.optBoolean("reducedMotion", false),
            )
        }

        return SceneJsonParseResult(
            scenes = parsedScenes,
            warnings = warnings,
            errors = errors,
        )
    }

    private fun containsSuspiciousScriptOrUrl(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("<script") || lower.contains("javascript:")
    }
}

package com.example.core.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.AudioAsset
import com.example.core.model.ExportResolution
import com.example.core.model.MotionPreset
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.core.model.SubtitleCue
import com.example.core.model.WatermarkLayer
import com.example.core.model.WatermarkSettings
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val languageCode: String,
    val aspectRatioLabel: String,
    val fps: Int,
    val resolutionLabel: String,
    val script: String,
    val audioAssetId: String?,
    val subtitleTrackId: String?,
    val templateId: String?,
    val sceneIdsJson: String,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val watermarkSettingsJson: String,
    @ColumnInfo(defaultValue = "[]") val subtitleCuesJson: String = "[]",
    @ColumnInfo(defaultValue = "[]") val scenesJson: String = "[]",
    @ColumnInfo(defaultValue = "") val audioAssetJson: String = "",
)

object ProjectJsonCodec {
    fun encodeStringList(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val value = array.optString(i, "")
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeSubtitleCues(cues: List<SubtitleCue>): String {
        val array = JSONArray()
        cues.forEach { cue ->
            array.put(
                JSONObject().apply {
                    put("id", cue.id)
                    put("index", cue.index)
                    put("startMs", cue.startMs)
                    put("endMs", cue.endMs)
                    put("text", cue.text)
                },
            )
        }
        return array.toString()
    }

    fun decodeSubtitleCues(raw: String?): List<SubtitleCue> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        SubtitleCue(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            index = obj.optInt("index", i + 1),
                            startMs = obj.optLong("startMs", 0L).coerceAtLeast(0L),
                            endMs = obj.optLong("endMs", 1_000L).coerceAtLeast(0L),
                            text = obj.optString("text", ""),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeScenes(scenes: List<SceneItem>): String {
        val array = JSONArray()
        scenes.forEach { scene ->
            array.put(
                JSONObject().apply {
                    put("id", scene.id)
                    put("orderIndex", scene.orderIndex)
                    put("headline", scene.headline)
                    put("caption", scene.caption)
                    put("startMs", scene.startMs)
                    put("endMs", scene.endMs)
                    put("templateId", scene.templateId)
                    put("backgroundImagePath", scene.backgroundImagePath ?: JSONObject.NULL)
                    put("accentHex", scene.accentHex ?: JSONObject.NULL)
                    put("motionPreset", scene.motionPreset.id)
                    put("reducedMotion", scene.reducedMotion)
                },
            )
        }
        return array.toString()
    }

    fun decodeScenes(raw: String?): List<SceneItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        SceneItem(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            orderIndex = obj.optInt("orderIndex", i),
                            headline = obj.optString("headline", ""),
                            caption = obj.optString("caption", ""),
                            startMs = obj.optLong("startMs", 0L).coerceAtLeast(0L),
                            endMs = obj.optLong("endMs", 3_000L).coerceAtLeast(500L),
                            templateId = obj.optString("templateId").ifBlank { Project.DEFAULT_TEMPLATE_ID },
                            backgroundImagePath = if (obj.isNull("backgroundImagePath")) null else obj.optString("backgroundImagePath").ifBlank { null },
                            accentHex = if (obj.isNull("accentHex")) null else obj.optString("accentHex").ifBlank { null },
                            motionPreset = MotionPreset.fromId(obj.optString("motionPreset")),
                            reducedMotion = obj.optBoolean("reducedMotion", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeAudioAsset(audio: AudioAsset?): String {
        if (audio == null) return ""
        return JSONObject().apply {
            put("id", audio.id)
            put("fileName", audio.fileName)
            put("localPath", audio.localPath)
            put("mimeType", audio.mimeType)
            put("durationMs", audio.durationMs)
            put("trimStartMs", audio.trimStartMs)
            put("trimEndMs", audio.trimEndMs)
            put("srtSyncOffsetMs", audio.srtSyncOffsetMs)
            put("isRecorded", audio.isRecorded)
        }.toString()
    }

    fun decodeAudioAsset(raw: String?): AudioAsset? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val path = obj.optString("localPath", "")
            if (path.isBlank()) return@runCatching null
            AudioAsset(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                fileName = obj.optString("fileName", "audio.m4a"),
                localPath = path,
                mimeType = obj.optString("mimeType", "audio/mp4"),
                durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                trimStartMs = obj.optLong("trimStartMs", 0L).coerceAtLeast(0L),
                trimEndMs = obj.optLong("trimEndMs", 0L).coerceAtLeast(0L),
                srtSyncOffsetMs = obj.optLong("srtSyncOffsetMs", 0L),
                isRecorded = obj.optBoolean("isRecorded", false),
            )
        }.getOrNull()
    }

    fun encodeWatermarkSettings(settings: WatermarkSettings): String {
        val root = JSONObject()
        root.put("user", encodeLayer(settings.user))
        root.put("developer", encodeLayer(settings.developer))
        return root.toString()
    }

    fun decodeWatermarkSettings(raw: String?): WatermarkSettings {
        if (raw.isNullOrBlank()) return WatermarkSettings()
        return runCatching {
            val root = JSONObject(raw)
            val userObj = root.optJSONObject("user")
            val devObj = root.optJSONObject("developer")
            WatermarkSettings(
                user = decodeLayer(userObj, WatermarkSettings.defaultUserLayer()),
                developer = decodeLayer(devObj, WatermarkSettings.defaultDeveloperLayer()),
            )
        }.getOrDefault(WatermarkSettings())
    }

    private fun encodeLayer(layer: WatermarkLayer): JSONObject = JSONObject().apply {
        put("enabled", layer.enabled)
        put("kind", layer.kind)
        put("text", layer.text ?: JSONObject.NULL)
        put("assetId", layer.assetId ?: JSONObject.NULL)
        put("position", layer.position)
        put("scale", layer.scale.toDouble())
        put("opacity", layer.opacity.toDouble())
        put("marginPercent", layer.marginPercent.toDouble())
        put("offsetXPercent", layer.offsetXPercent.toDouble())
        put("offsetYPercent", layer.offsetYPercent.toDouble())
        put("startMs", layer.startMs)
        put("endMs", layer.endMs ?: JSONObject.NULL)
    }

    private fun decodeLayer(obj: JSONObject?, fallback: WatermarkLayer): WatermarkLayer {
        if (obj == null) return fallback
        return WatermarkLayer(
            enabled = obj.optBoolean("enabled", fallback.enabled),
            kind = obj.optString("kind", fallback.kind).ifBlank { fallback.kind },
            text = if (obj.isNull("text")) null else obj.optString("text", fallback.text ?: ""),
            assetId = if (obj.isNull("assetId")) null else obj.optString("assetId").ifBlank { null },
            position = obj.optString("position", fallback.position).ifBlank { fallback.position },
            scale = obj.optDouble("scale", fallback.scale.toDouble()).toFloat().coerceIn(0.3f, 2.5f),
            opacity = obj.optDouble("opacity", fallback.opacity.toDouble()).toFloat().coerceIn(0f, 1f),
            marginPercent = obj.optDouble("marginPercent", fallback.marginPercent.toDouble()).toFloat().coerceIn(0f, 20f),
            offsetXPercent = obj.optDouble("offsetXPercent", fallback.offsetXPercent.toDouble()).toFloat().coerceIn(-30f, 30f),
            offsetYPercent = obj.optDouble("offsetYPercent", fallback.offsetYPercent.toDouble()).toFloat().coerceIn(-30f, 30f),
            startMs = obj.optLong("startMs", fallback.startMs).coerceAtLeast(0L),
            endMs = if (obj.isNull("endMs")) null else obj.optLong("endMs").coerceAtLeast(0L),
        )
    }
}

fun ProjectEntity.toDomain(): Project {
    val decodedScenes = ProjectJsonCodec.decodeScenes(scenesJson)
    val decodedAudio = ProjectJsonCodec.decodeAudioAsset(audioAssetJson)
    return Project(
        id = id,
        name = name,
        language = AppLanguage.fromCode(languageCode),
        aspectRatio = AspectRatio.fromLabel(aspectRatioLabel),
        fps = fps,
        resolution = ExportResolution.fromLabel(resolutionLabel),
        script = script,
        audioAssetId = audioAssetId ?: decodedAudio?.id,
        audioAsset = decodedAudio,
        subtitleTrackId = subtitleTrackId,
        subtitleCues = ProjectJsonCodec.decodeSubtitleCues(subtitleCuesJson),
        templateId = templateId?.ifBlank { Project.DEFAULT_TEMPLATE_ID } ?: Project.DEFAULT_TEMPLATE_ID,
        scenes = decodedScenes,
        sceneIds = if (decodedScenes.isNotEmpty()) decodedScenes.map { it.id } else ProjectJsonCodec.decodeStringList(sceneIdsJson),
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        watermarkSettings = ProjectJsonCodec.decodeWatermarkSettings(watermarkSettingsJson),
    )
}

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    languageCode = language.code,
    aspectRatioLabel = aspectRatio.label,
    fps = fps,
    resolutionLabel = resolution.label,
    script = script,
    audioAssetId = audioAsset?.id ?: audioAssetId,
    subtitleTrackId = subtitleTrackId,
    templateId = templateId,
    sceneIdsJson = ProjectJsonCodec.encodeStringList(scenes.map { it.id }),
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    watermarkSettingsJson = ProjectJsonCodec.encodeWatermarkSettings(watermarkSettings),
    subtitleCuesJson = ProjectJsonCodec.encodeSubtitleCues(subtitleCues),
    scenesJson = ProjectJsonCodec.encodeScenes(scenes),
    audioAssetJson = ProjectJsonCodec.encodeAudioAsset(audioAsset),
)

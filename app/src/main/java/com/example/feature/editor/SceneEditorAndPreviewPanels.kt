package com.example.feature.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.core.model.MotionPreset
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.core.model.SceneTransition
import com.example.core.render.RenderFrameInput
import com.example.core.render.SceneFrameRenderer
import com.example.data.templates.TemplateDefinition
import com.example.feature.audio.StudioAudioEngine
import com.example.feature.subtitles.SrtEngine
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun ScenesStepPanel(
    project: Project,
    templates: List<TemplateDefinition>,
    renderer: SceneFrameRenderer,
    onAutoMatchScenes: () -> Unit,
    onUpdateScenes: (List<SceneItem>) -> Unit,
    onImportSceneImage: (Uri, (Result<String>) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scenes = project.scenes
    var editingScene by remember { mutableStateOf<SceneItem?>(null) }
    var pendingImageSceneId by remember { mutableStateOf<String?>(null) }

    // Zero-permission Android Photo Picker for scene background images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val sceneId = pendingImageSceneId
        pendingImageSceneId = null
        if (uri != null && sceneId != null) {
            onImportSceneImage(uri) { result ->
                result.onSuccess { localPath ->
                    val updated = scenes.map {
                        if (it.id == sceneId) it.copy(backgroundImagePath = localPath) else it
                    }
                    onUpdateScenes(updated)
                    onShowMessage("Scene background image attached.")
                }.onFailure { err ->
                    onShowMessage("Image import failed: ${err.message}")
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Scene Timeline (${scenes.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Total: ${SrtEngine.formatTimecode(project.totalDurationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAutoMatchScenes,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("auto_match_scenes_button"),
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto Match → Motion", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val lastEnd = scenes.maxOfOrNull { it.endMs } ?: 0L
                            val newScene = SceneItem(
                                id = UUID.randomUUID().toString(),
                                orderIndex = scenes.size,
                                headline = "নতুন সিন #${scenes.size + 1}",
                                caption = project.name,
                                startMs = lastEnd,
                                endMs = lastEnd + 3_000L,
                                templateId = project.templateId,
                                motionPreset = MotionPreset.SLIDE_UP,
                            )
                            onUpdateScenes(scenes + newScene)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_scene_button"),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Scene")
                    }
                }
            }
        }

        if (scenes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "No scenes yet. Tap 'Auto Match → Motion' to generate scenes from your SRT subtitles or script, or tap 'Add Scene' manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            scenes.forEachIndexed { index, scene ->
                SceneEditorCard(
                    index = index,
                    totalCount = scenes.size,
                    project = project,
                    scene = scene,
                    renderer = renderer,
                    onEdit = { editingScene = scene },
                    onPickBackground = {
                        pendingImageSceneId = scene.id
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val reordered = scenes.toMutableList()
                            val moved = reordered.removeAt(index)
                            reordered.add(index - 1, moved)
                            onUpdateScenes(recalculateSceneTimings(reordered))
                        }
                    },
                    onMoveDown = {
                        if (index < scenes.lastIndex) {
                            val reordered = scenes.toMutableList()
                            val moved = reordered.removeAt(index)
                            reordered.add(index + 1, moved)
                            onUpdateScenes(recalculateSceneTimings(reordered))
                        }
                    },
                    onDelete = {
                        val remaining = scenes.filterNot { it.id == scene.id }
                        onUpdateScenes(recalculateSceneTimings(remaining))
                    },
                )
            }
        }
    }

    editingScene?.let { target ->
        EditSceneDialog(
            scene = target,
            templates = templates,
            onDismiss = { editingScene = null },
            onSave = { updated ->
                val updatedList = scenes.map { if (it.id == updated.id) updated else it }
                onUpdateScenes(recalculateSceneTimings(updatedList))
                editingScene = null
            },
        )
    }
}

@Composable
private fun SceneEditorCard(
    index: Int,
    totalCount: Int,
    project: Project,
    scene: SceneItem,
    renderer: SceneFrameRenderer,
    onEdit: () -> Unit,
    onPickBackground: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val bgBitmap = remember(scene.backgroundImagePath) {
        SceneFrameRenderer.decodeBitmapSafely(scene.backgroundImagePath, 360).getOrNull()
    }
    val thumb = remember(scene.id, scene.headline, scene.caption, scene.templateId, scene.backgroundImagePath) {
        renderer.renderToBitmap(
            RenderFrameInput(
                project = project,
                timeMs = scene.startMs + 650L,
                width = 240,
                height = 160,
                backgroundBitmaps = if (bgBitmap != null && scene.backgroundImagePath != null) {
                    mapOf(scene.backgroundImagePath to bgBitmap)
                } else {
                    emptyMap()
                },
                overrideScene = scene,
            ),
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scene_card_${index + 1}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = "Scene ${index + 1} preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 86.dp, height = 60.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Scene ${index + 1} · ${scene.templateId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = scene.headline.ifBlank { "(Untitled Scene)" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    Text(
                        text = "${SrtEngine.formatCompactDuration(scene.startMs)} → ${SrtEngine.formatCompactDuration(scene.endMs)} · ${scene.motionPreset.displayLabel} · ${scene.transitionIn.displayLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit scene")
                    }
                    IconButton(onClick = onPickBackground, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.Image, contentDescription = "Pick scene background image")
                    }
                }
                Row {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move scene up")
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalCount - 1, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move scene down")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete scene", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditSceneDialog(
    scene: SceneItem,
    templates: List<TemplateDefinition>,
    onDismiss: () -> Unit,
    onSave: (SceneItem) -> Unit,
) {
    var headline by remember(scene.id) { mutableStateOf(scene.headline) }
    var caption by remember(scene.id) { mutableStateOf(scene.caption) }
    var badgeLabel by remember(scene.id) { mutableStateOf(scene.badgeLabel) }
    var highlightWord by remember(scene.id) { mutableStateOf(scene.highlightWord) }
    var durationSec by remember(scene.id) { mutableStateOf((scene.durationMs / 1000f).coerceIn(1f, 15f)) }
    var selectedTemplate by remember(scene.id) { mutableStateOf(scene.templateId) }
    var selectedMotion by remember(scene.id) { mutableStateOf(scene.motionPreset) }
    var selectedTransition by remember(scene.id) { mutableStateOf(scene.transitionIn) }
    var clearImage by remember(scene.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Motion Scene") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = headline,
                    onValueChange = { headline = it },
                    label = { Text("Headline (Bengali / English)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Supporting Caption") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = badgeLabel,
                        onValueChange = { badgeLabel = it.take(24) },
                        label = { Text("Badge") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = highlightWord,
                        onValueChange = { highlightWord = it.take(32) },
                        label = { Text("Highlight Word") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Duration: ${String.format("%.1f", durationSec)} s", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = durationSec,
                    onValueChange = { durationSec = it },
                    valueRange = 1f..15f,
                )

                Text("Motion Preset", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MotionPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedMotion == preset,
                            onClick = { selectedMotion = preset },
                            label = { Text(preset.displayLabel) },
                        )
                    }
                }

                Text("Scene Transition", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SceneTransition.entries.forEach { tr ->
                        FilterChip(
                            selected = selectedTransition == tr,
                            onClick = { selectedTransition = tr },
                            label = { Text(tr.displayLabel) },
                        )
                    }
                }

                Text("Template (${templates.size})", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    templates.forEach { tpl ->
                        FilterChip(
                            selected = selectedTemplate == tpl.id,
                            onClick = { selectedTemplate = tpl.id },
                            label = { Text(tpl.name) },
                        )
                    }
                }

                if (!scene.backgroundImagePath.isNullOrBlank() && !clearImage) {
                    TextButton(onClick = { clearImage = true }) {
                        Text("Remove Background Photo", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val durMs = (durationSec * 1000L).toLong().coerceAtLeast(1_000L)
                    onSave(
                        scene.copy(
                            headline = headline,
                            caption = caption,
                            badgeLabel = badgeLabel,
                            highlightWord = highlightWord,
                            endMs = scene.startMs + durMs,
                            templateId = selectedTemplate,
                            motionPreset = selectedMotion,
                            transitionIn = selectedTransition,
                            backgroundImagePath = if (clearImage) null else scene.backgroundImagePath,
                        ),
                    )
                },
            ) {
                Text("Save Scene")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun StudioPreviewStepPanel(
    project: Project,
    renderer: SceneFrameRenderer,
    audioEngine: StudioAudioEngine,
    modifier: Modifier = Modifier,
) {
    val totalDurationMs = project.totalDurationMs
    var playheadMs by remember(project.id) { mutableLongStateOf(0L) }
    var isPlaying by remember(project.id) { mutableStateOf(false) }

    val bgBitmaps = remember(project.scenes) {
        buildMap<String, Bitmap> {
            project.scenes.mapNotNull { it.backgroundImagePath }.distinct().forEach { path ->
                SceneFrameRenderer.decodeBitmapSafely(path, 720).getOrNull()?.let { put(path, it) }
            }
        }
    }
    val userWatermarkBitmap = remember(project.watermarkSettings.user.assetId) {
        if (project.watermarkSettings.user.kind == "image") {
            SceneFrameRenderer.decodeBitmapSafely(project.watermarkSettings.user.assetId, 512).getOrNull()
        } else {
            null
        }
    }

    LaunchedEffect(isPlaying, totalDurationMs) {
        if (isPlaying) {
            project.audioAsset?.let { audio ->
                audioEngine.playAudio(audio.localPath, startPositionMs = audio.trimStartMs + playheadMs)
            }
            val stepMs = 33L // ~30fps smooth deterministic Canvas preview matching MP4 export
            while (isPlaying) {
                delay(stepMs)
                val next = playheadMs + stepMs
                if (next >= totalDurationMs) {
                    playheadMs = totalDurationMs
                    isPlaying = false
                    audioEngine.stopPlayback()
                } else {
                    playheadMs = next
                }
            }
        } else {
            audioEngine.stopPlayback()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioEngine.stopPlayback()
        }
    }

    val (previewW, previewH) = remember(project.aspectRatio) {
        when (project.aspectRatio) {
            com.example.core.model.AspectRatio.PORTRAIT_9_16 -> 360 to 640
            com.example.core.model.AspectRatio.SQUARE_1_1 -> 480 to 480
            com.example.core.model.AspectRatio.PORTRAIT_4_5 -> 400 to 500
            com.example.core.model.AspectRatio.LANDSCAPE_16_9 -> 640 to 360
        }
    }

    val frameBitmap = remember(
        project,
        playheadMs,
        previewW,
        previewH,
        bgBitmaps,
        userWatermarkBitmap,
    ) {
        renderer.renderToBitmap(
            RenderFrameInput(
                project = project,
                timeMs = playheadMs,
                width = previewW,
                height = previewH,
                backgroundBitmaps = bgBitmaps,
                userWatermarkBitmap = userWatermarkBitmap,
            ),
        )
    }

    val activeCue = remember(project.subtitleCues, playheadMs, project.audioAsset?.srtSyncOffsetMs) {
        SrtEngine.activeCueAt(project.subtitleCues, playheadMs, project.audioAsset?.srtSyncOffsetMs ?: 0L)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Deterministic Preview (${project.aspectRatio.label})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Matches MP4 Export",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Box(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .aspectRatio(project.aspectRatio.ratioValue)
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("studio_preview_canvas"),
                ) {
                    Image(
                        bitmap = frameBitmap.asImageBitmap(),
                        contentDescription = "Live video frame preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Slider(
                    value = playheadMs.toFloat().coerceIn(0f, totalDurationMs.toFloat()),
                    onValueChange = { value ->
                        isPlaying = false
                        playheadMs = value.toLong()
                    },
                    valueRange = 0f..totalDurationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${SrtEngine.formatTimecode(playheadMs)} / ${SrtEngine.formatTimecode(totalDurationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                isPlaying = false
                                playheadMs = 0L
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Outlined.Replay, contentDescription = "Restart preview", modifier = Modifier.size(18.dp))
                        }

                        Button(
                            onClick = {
                                if (!isPlaying && playheadMs >= totalDurationMs) {
                                    playheadMs = 0L
                                }
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("preview_play_pause_button"),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPlaying) "Pause" else "Play Preview", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (activeCue != null) {
                    Text(
                        text = "Active Subtitle #${activeCue.index}: ${activeCue.text.replace("\n", " ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun recalculateSceneTimings(scenes: List<SceneItem>): List<SceneItem> {
    var cursor = 0L
    return scenes.mapIndexed { idx, scene ->
        val dur = scene.durationMs
        val updated = scene.copy(orderIndex = idx, startMs = cursor, endMs = cursor + dur)
        cursor += dur
        updated
    }
}

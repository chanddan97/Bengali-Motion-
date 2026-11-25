package com.example.feature.projects

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.i18n.LocalStudioStrings
import com.example.core.model.AiProxyConfig
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.AudioAsset
import com.example.core.model.ExportConfig
import com.example.core.model.ExportResolution
import com.example.core.model.ExportState
import com.example.core.model.Project
import com.example.core.model.ProjectWorkflowStep
import com.example.core.model.SceneItem
import com.example.core.model.SubtitleCue
import com.example.core.model.WatermarkSettings
import com.example.core.render.SceneFrameRenderer
import com.example.data.templates.TemplateCatalogResult
import com.example.feature.audio.AudioStepPanel
import com.example.feature.audio.StudioAudioEngine
import com.example.feature.editor.ScenesStepPanel
import com.example.feature.editor.StudioPreviewStepPanel
import com.example.feature.export.ExportStepPanel
import com.example.feature.script.ScriptStepPanel
import com.example.feature.subtitles.SrtStepPanel
import com.example.feature.templates.TemplatesGalleryScreen
import com.example.feature.watermark.WatermarkStepPanel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectWorkspaceScreen(
    project: Project?,
    templateCatalog: TemplateCatalogResult,
    favoriteTemplateIds: Set<String>,
    recentTemplateIds: List<String>,
    renderer: SceneFrameRenderer,
    audioEngine: StudioAudioEngine,
    exportState: ExportState,
    aiProxyConfig: AiProxyConfig,
    aiState: AiAssistUiState,
    onBack: () -> Unit,
    onRenameProject: (String, String) -> Unit,
    onUpdateFormatAndLanguage: (String, AspectRatio, AppLanguage, ExportResolution) -> Unit,
    onUpdateScript: (String, String) -> Unit,
    onGenerateSrtFromScript: (Project) -> Unit,
    onUpdateSubtitles: (String, List<SubtitleCue>) -> Unit,
    onApplyTemplate: (String, String) -> Unit,
    onToggleFavoriteTemplate: (String) -> Unit,
    onImportAudioUri: (String, Uri) -> Unit,
    onUpdateAudioAsset: (String, AudioAsset?) -> Unit,
    onAutoMatchScenes: (Project, Boolean) -> Unit,
    onImportSceneJson: (String, String, String, (Boolean) -> Unit) -> Unit,
    onUpdateScenes: (String, List<SceneItem>) -> Unit,
    onImportImage: (String, String, Uri, (Result<String>) -> Unit) -> Unit,
    onUpdateWatermarkSettings: (String, WatermarkSettings) -> Unit,
    onSaveWatermarkDefault: (WatermarkSettings) -> Unit,
    onStartExport: (Project, ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onSaveExportToGallery: (String, String) -> Unit,
    onUpdateAiConsent: (Boolean) -> Unit,
    onRequestAiAssist: (Project) -> Unit,
    onDismissAiState: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStudioStrings.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var activeStep by rememberSaveable { mutableStateOf(ProjectWorkflowStep.SCRIPT) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = project?.name ?: strings.projectOverviewTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (project != null) {
                        Text(
                            text = "${project.aspectRatio.label} · ${project.resolution.label} · ${project.templateId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("workspace_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                }
            },
            actions = {
                if (project != null) {
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workspace_rename_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DriveFileRenameOutline,
                            contentDescription = strings.actionRename,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )

        if (project == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(strings.projectNotFoundTitle, style = MaterialTheme.typography.titleLarge)
                    Text(
                        strings.projectNotFoundBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text(strings.backToProjectsAction)
                    }
                }
            }
            return
        }

        // 10-Step Horizontal Studio Step Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("workspace_step_bar"),
            ) {
                items(ProjectWorkflowStep.entries) { step ->
                    val selected = activeStep == step
                    FilterChip(
                        selected = selected,
                        onClick = { activeStep = step },
                        label = {
                            Text(
                                text = strings.stepTitle(step),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workflow_step_${step.stepNumber}"),
                    )
                }
            }
        }

        // Step 5 (Template Gallery) uses its own LazyVerticalGrid cleanly
        if (activeStep == ProjectWorkflowStep.TEMPLATE) {
            TemplatesGalleryScreen(
                catalog = templateCatalog,
                renderer = renderer,
                favoriteTemplateIds = favoriteTemplateIds,
                recentTemplateIds = recentTemplateIds,
                selectedTemplateId = project.templateId,
                onToggleFavorite = onToggleFavoriteTemplate,
                onApplyTemplate = { templateId -> onApplyTemplate(project.id, templateId) },
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp)
                        .testTag("workspace_scroll_list"),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = strings.autosavedBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val steps = ProjectWorkflowStep.entries
                                val idx = steps.indexOf(activeStep)
                                if (idx > 0) {
                                    OutlinedButton(
                                        onClick = { activeStep = steps[idx - 1] },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Text("Prev")
                                    }
                                }
                                if (idx < steps.lastIndex) {
                                    Button(
                                        onClick = { activeStep = steps[idx + 1] },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Text("Next →")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        when (activeStep) {
                            ProjectWorkflowStep.SCRIPT -> ScriptStepPanel(
                                project = project,
                                aiProxyConfig = aiProxyConfig,
                                aiState = aiState,
                                onScriptChanged = { onUpdateScript(project.id, it) },
                                onGenerateSrtFromScript = {
                                    onGenerateSrtFromScript(project)
                                    activeStep = ProjectWorkflowStep.SRT
                                },
                                onAutoMatchScenes = {
                                    onAutoMatchScenes(project, true)
                                    activeStep = ProjectWorkflowStep.AUTO_MATCH
                                },
                                onUpdateAiConsent = onUpdateAiConsent,
                                onRequestAiAssist = { onRequestAiAssist(project) },
                                onApplyAiScript = { onUpdateScript(project.id, it) },
                                onApplyAiScenes = { onUpdateScenes(project.id, it) },
                                onDismissAiState = onDismissAiState,
                            )

                            ProjectWorkflowStep.VOICE_AUDIO -> AudioStepPanel(
                                project = project,
                                audioEngine = audioEngine,
                                onImportAudioUri = { uri -> onImportAudioUri(project.id, uri) },
                                onAudioAssetUpdated = { asset -> onUpdateAudioAsset(project.id, asset) },
                                onShowMessage = onShowMessage,
                            )

                            ProjectWorkflowStep.SRT -> SrtStepPanel(
                                project = project,
                                onUpdateSubtitles = { cues -> onUpdateSubtitles(project.id, cues) },
                                onShowMessage = onShowMessage,
                            )

                            ProjectWorkflowStep.FORMAT_LANGUAGE -> FormatAndLanguageStepCard(
                                project = project,
                                onUpdateFormatAndLanguage = { ratio, lang, res ->
                                    onUpdateFormatAndLanguage(project.id, ratio, lang, res)
                                },
                            )

                            ProjectWorkflowStep.AUTO_MATCH -> AutoMatchStepCard(
                                project = project,
                                allowedTemplateIds = templateCatalog.templates.map { it.id }.toSet(),
                                onRunAutoMatch = { vary ->
                                    onAutoMatchScenes(project, vary)
                                },
                                onImportSceneJson = { rawJson, onDone ->
                                    onImportSceneJson(project.id, project.templateId, rawJson, onDone)
                                },
                                onGoToScenes = { activeStep = ProjectWorkflowStep.SCENES },
                                onGoToPreview = { activeStep = ProjectWorkflowStep.PREVIEW },
                                onShowMessage = onShowMessage,
                            )

                            ProjectWorkflowStep.SCENES -> ScenesStepPanel(
                                project = project,
                                templates = templateCatalog.templates,
                                renderer = renderer,
                                onAutoMatchScenes = { onAutoMatchScenes(project, true) },
                                onUpdateScenes = { updated -> onUpdateScenes(project.id, updated) },
                                onImportSceneImage = { uri, cb -> onImportImage(project.id, "scenes", uri, cb) },
                                onShowMessage = onShowMessage,
                            )

                            ProjectWorkflowStep.PREVIEW -> StudioPreviewStepPanel(
                                project = project,
                                renderer = renderer,
                                audioEngine = audioEngine,
                            )

                            ProjectWorkflowStep.WATERMARK -> WatermarkStepPanel(
                                project = project,
                                renderer = renderer,
                                onUpdateWatermarkSettings = { wm -> onUpdateWatermarkSettings(project.id, wm) },
                                onSaveAsGlobalDefault = onSaveWatermarkDefault,
                                onImportWatermarkImage = { uri, cb -> onImportImage(project.id, "watermarks", uri, cb) },
                                onShowMessage = onShowMessage,
                            )

                            ProjectWorkflowStep.EXPORT -> ExportStepPanel(
                                project = project,
                                exportState = exportState,
                                onUpdateWatermarkSettings = { wm -> onUpdateWatermarkSettings(project.id, wm) },
                                onStartExport = { cfg -> onStartExport(project, cfg) },
                                onCancelExport = onCancelExport,
                                onSaveToGallery = onSaveExportToGallery,
                                onShowMessage = onShowMessage,
                            )

                            ProjectWorkflowStep.TEMPLATE -> Unit
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showRenameDialog && project != null) {
        RenameProjectDialog(
            project = project,
            onDismiss = { showRenameDialog = false },
            onConfirm = { updatedName ->
                onRenameProject(project.id, updatedName)
                showRenameDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatAndLanguageStepCard(
    project: Project,
    onUpdateFormatAndLanguage: (AspectRatio, AppLanguage, ExportResolution) -> Unit,
) {
    val strings = LocalStudioStrings.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = strings.formatAndLanguageSectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = strings.formatAndLanguageSectionHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(strings.aspectRatioLabel, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AspectRatio.entries.forEach { ratio ->
                    AspectRatioOptionCard(
                        aspectRatio = ratio,
                        selected = project.aspectRatio == ratio,
                        onClick = { onUpdateFormatAndLanguage(ratio, project.language, project.resolution) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("workspace_ratio_${ratio.label.replace(":", "_")}"),
                    )
                }
            }

            Text(strings.projectLanguageLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = project.language == lang,
                        onClick = { onUpdateFormatAndLanguage(project.aspectRatio, lang, project.resolution) },
                        label = { Text(lang.displayLabel) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workspace_lang_${lang.code}"),
                    )
                }
            }

            Text(strings.resolutionLabel, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportResolution.entries.forEach { res ->
                    FilterChip(
                        selected = project.resolution == res,
                        onClick = { onUpdateFormatAndLanguage(project.aspectRatio, project.language, res) },
                        label = { Text("${res.label} · ${project.fps} ${strings.fpsSuffix}") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workspace_res_${res.label}"),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoMatchStepCard(
    project: Project,
    allowedTemplateIds: Set<String>,
    onRunAutoMatch: (Boolean) -> Unit,
    onImportSceneJson: (String, (Boolean) -> Unit) -> Unit,
    onGoToScenes: () -> Unit,
    onGoToPreview: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var varyTemplates by remember(project.id) { mutableStateOf(true) }
    var showJsonPasteDialog by remember { mutableStateOf(false) }

    val activeJsonText = remember(project.scenes, project.templateId, project.subtitleCues, project.script, varyTemplates) {
        if (project.scenes.isNotEmpty()) {
            com.example.feature.editor.SceneMotionJsonCodec.exportProjectSceneJson(project)
        } else {
            com.example.feature.editor.SceneMotionJsonCodec.generateFromSrtAndExportJson(
                project = project,
                varyTemplates = varyTemplates,
            ).second
        }
    }

    val importJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Cannot read selected JSON file.")
            }.onSuccess { text ->
                onImportSceneJson(text) {}
            }.onFailure { err ->
                onShowMessage("JSON import failed: ${err.message}")
            }
        }
    }

    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(activeJsonText.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot write JSON destination.")
            }.onSuccess {
                onShowMessage("Exported Scene Motion JSON.")
            }.onFailure { err ->
                onShowMessage("JSON export failed: ${err.message}")
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SRT → Motion Graphics JSON (Offline Studio)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Converts your Script + SRT subtitles into a structured Motion Graphics JSON timeline with word-stagger, keyword highlights, kinetic transitions, and animated template motifs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Inputs: ${project.subtitleCues.size} SRT cue(s) · ${project.script.lines().count { it.isNotBlank() }} script line(s) · ${project.scenes.size} active scene(s)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rotate Dynamic Motion Templates",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "Applies varied templates & transitions per SRT cue instead of one static look.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = varyTemplates,
                    onCheckedChange = { varyTemplates = it },
                    modifier = Modifier.testTag("vary_templates_switch"),
                )
            }

            Button(
                onClick = { onRunAutoMatch(varyTemplates) },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("run_auto_match_button"),
            ) {
                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Generate Motion JSON & Scenes from SRT", fontWeight = FontWeight.Bold)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("import_scene_json_button"),
                ) {
                    Text("Import .json")
                }
                OutlinedButton(
                    onClick = { showJsonPasteDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("paste_scene_json_button"),
                ) {
                    Text("Paste / Edit JSON")
                }
                OutlinedButton(
                    onClick = {
                        val safeName = project.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").ifBlank { "scenes" }
                        exportJsonLauncher.launch("${safeName}_motion.json")
                    },
                    enabled = activeJsonText.isNotBlank(),
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("export_scene_json_button"),
                ) {
                    Text("Export .json")
                }
                if (project.scenes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onGoToScenes,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Scenes (${project.scenes.size})")
                    }
                    Button(
                        onClick = onGoToPreview,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_match_preview_button"),
                    ) {
                        Text("Play Preview →")
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (project.scenes.isNotEmpty()) "Active Scene Motion JSON" else "Preview of SRT → Scene Motion JSON (tap Generate to apply)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = activeJsonText.lines().take(26).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showJsonPasteDialog) {
        var draftJson by remember(activeJsonText) { mutableStateOf(activeJsonText) }
        var errorLine by remember { mutableStateOf<String?>(null) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showJsonPasteDialog = false },
            title = { Text("Paste / Edit Scene Motion JSON") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = draftJson,
                        onValueChange = { draftJson = it; errorLine = null },
                        minLines = 8,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth().testTag("paste_scene_json_input"),
                    )
                    errorLine?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val check = com.example.feature.editor.SceneMotionJsonCodec.parseAndValidateSceneJson(
                            rawJson = draftJson,
                            allowedTemplateIds = allowedTemplateIds,
                            fallbackTemplateId = project.templateId,
                        )
                        if (!check.isValid) {
                            errorLine = check.errors.firstOrNull() ?: "Invalid Scene JSON."
                        } else {
                            onImportSceneJson(draftJson) { ok ->
                                if (ok) showJsonPasteDialog = false
                            }
                        }
                    },
                ) {
                    Text("Validate & Apply")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showJsonPasteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

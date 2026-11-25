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
    onAutoMatchScenes: (Project) -> Unit,
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
                                    onAutoMatchScenes(project)
                                    activeStep = ProjectWorkflowStep.SCENES
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
                                onRunAutoMatch = {
                                    onAutoMatchScenes(project)
                                    activeStep = ProjectWorkflowStep.SCENES
                                },
                                onGoToScenes = { activeStep = ProjectWorkflowStep.SCENES },
                            )

                            ProjectWorkflowStep.SCENES -> ScenesStepPanel(
                                project = project,
                                templates = templateCatalog.templates,
                                renderer = renderer,
                                onAutoMatchScenes = { onAutoMatchScenes(project) },
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

@Composable
private fun AutoMatchStepCard(
    project: Project,
    onRunAutoMatch: () -> Unit,
    onGoToScenes: () -> Unit,
) {
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
                text = "Auto Match → Motion (100% Offline)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Automatically splits your project into timed motion scenes using your SRT subtitles (or Script lines if SRT is empty) and applies your active template (${project.templateId}).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Ready inputs: ${project.subtitleCues.size} SRT cue(s) · ${project.script.lines().count { it.isNotBlank() }} script line(s) · ${project.scenes.size} existing scene(s)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRunAutoMatch,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("run_auto_match_button"),
                ) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Generate Scenes Now", fontWeight = FontWeight.Bold)
                }
                if (project.scenes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onGoToScenes,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Edit (${project.scenes.size})")
                    }
                }
            }
        }
    }
}

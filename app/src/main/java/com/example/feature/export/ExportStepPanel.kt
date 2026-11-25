package com.example.feature.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.core.model.ExportConfig
import com.example.core.model.ExportResolution
import com.example.core.model.ExportState
import com.example.core.model.Project
import com.example.core.model.WatermarkSettings
import com.example.feature.subtitles.SrtEngine
import java.io.File
import java.util.Locale

@Composable
fun ExportStepPanel(
    project: Project,
    exportState: ExportState,
    onUpdateWatermarkSettings: (WatermarkSettings) -> Unit,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onSaveToGallery: (String, String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedRes by remember(project.id, project.resolution) { mutableStateOf(project.resolution) }
    var selectedFps by remember(project.id, project.fps) { mutableIntStateOf(project.fps.coerceIn(24, 30)) }
    var includeAudio by remember(project.id) { mutableStateOf(project.audioAsset != null) }

    val config = ExportConfig(
        aspectRatio = project.aspectRatio,
        resolution = selectedRes,
        fps = selectedFps,
        includeAudio = includeAudio && project.audioAsset != null,
    )
    val (outW, outH) = config.outputDimensions()

    val saveAsMp4Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        val success = exportState as? ExportState.Success ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                val src = File(success.outputPath)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Cannot write MP4 to chosen destination.")
            }.onSuccess {
                onShowMessage("Saved MP4 copy to selected folder.")
            }.onFailure { err ->
                onShowMessage("Save failed: ${err.message}")
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Export Configuration Card
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
                Text(
                    text = "Real On-Device MP4 Export (H.264 / AAC)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Output: ${outW}×${outH} (${project.aspectRatio.label}) · ${selectedFps} fps · Duration: ${SrtEngine.formatTimecode(project.totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportResolution.entries.forEach { res ->
                        FilterChip(
                            selected = selectedRes == res,
                            onClick = { selectedRes = res },
                            label = { Text(res.label) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                    listOf(24, 30).forEach { fpsOption ->
                        FilterChip(
                            selected = selectedFps == fpsOption,
                            onClick = { selectedFps = fpsOption },
                            label = { Text("$fpsOption fps") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }

                if (project.audioAsset != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Mux Audio: ${project.audioAsset.fileName}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = includeAudio, onCheckedChange = { includeAudio = it })
                    }
                }

                // Watermark Toggles Summary right on Export screen (per PRD Section 7)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Watermarks in Final Render",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "User Watermark (${if (project.watermarkSettings.user.enabled) "ON" else "OFF"})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Switch(
                                checked = project.watermarkSettings.user.enabled,
                                onCheckedChange = { on ->
                                    val fallback = if (on && project.watermarkSettings.user.text.isNullOrBlank()) project.name else project.watermarkSettings.user.text
                                    onUpdateWatermarkSettings(
                                        project.watermarkSettings.copy(
                                            user = project.watermarkSettings.user.copy(enabled = on, text = fallback),
                                        ),
                                    )
                                },
                                modifier = Modifier.testTag("export_user_watermark_toggle"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Developer Watermark (${if (project.watermarkSettings.developer.enabled) "ON" else "OFF"})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Switch(
                                checked = project.watermarkSettings.developer.enabled,
                                onCheckedChange = { on ->
                                    onUpdateWatermarkSettings(
                                        project.watermarkSettings.copy(
                                            developer = project.watermarkSettings.developer.copy(enabled = on),
                                        ),
                                    )
                                },
                                modifier = Modifier.testTag("export_dev_watermark_toggle"),
                            )
                        }
                    }
                }

                val isRunning = exportState is ExportState.Running
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onStartExport(config) },
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("start_mp4_export_button"),
                    ) {
                        Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "Encoding MP4..." else "Export Real MP4",
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (isRunning) {
                        OutlinedButton(
                            onClick = onCancelExport,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_mp4_export_button"),
                        ) {
                            Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // Export State Card (Progress / Success / Error / Cancelled)
        when (exportState) {
            ExportState.Idle -> Unit

            is ExportState.Running -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("export_progress_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = exportState.phase,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(exportState.progress * 100).toInt()}% · Frame ${exportState.currentFrame} / ${exportState.totalFrames}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is ExportState.Success -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth().testTag("export_success_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text("Verified MP4 Ready!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Resolution: ${exportState.width}×${exportState.height} · Duration: ${SrtEngine.formatTimecode(exportState.durationMs)} · Size: ${formatMb(exportState.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "Audio track: ${if (exportState.hasAudioTrack) "AAC muxed" else "Video-only"} · Local file: ${exportState.outputPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onSaveToGallery(exportState.outputPath, project.name) },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save to Movies")
                            }

                            OutlinedButton(
                                onClick = {
                                    val safe = project.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").ifBlank { "banglamotion" }
                                    saveAsMp4Launcher.launch("$safe.mp4")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save As")
                            }

                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val file = File(exportState.outputPath)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file,
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share MP4"))
                                    }.onFailure { err ->
                                        onShowMessage("Share failed: ${err.message}")
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share MP4", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            is ExportState.Failed -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().testTag("export_error_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = if (exportState.isLowStorage) "Low Storage Warning" else "Export Failed (Project Safe)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Text(
                            text = exportState.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Button(onClick = { onStartExport(config) }) {
                            Text("Retry Export")
                        }
                    }
                }
            }

            ExportState.Cancelled -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Export cancelled safely. Your project data was preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.2f MB", kb / 1024.0)
}

package com.example.feature.subtitles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.Project
import com.example.core.model.SrtIssueSeverity
import com.example.core.model.SrtParseResult
import com.example.core.model.SubtitleCue
import java.nio.charset.StandardCharsets
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SrtStepPanel(
    project: Project,
    onUpdateSubtitles: (List<SubtitleCue>) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cues = project.subtitleCues
    val issues = remember(cues, project.audioAsset?.durationMs) {
        SrtEngine.validateCues(cues, project.audioAsset?.durationMs?.takeIf { it > 0L })
    }

    var pendingImportResult by remember { mutableStateOf<SrtParseResult?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showSearchReplaceDialog by remember { mutableStateOf(false) }
    var editingCue by remember { mutableStateOf<SubtitleCue?>(null) }

    val importSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Cannot read selected SRT file.")
                val (decoded, usedFallback) = SrtEngine.decodeUtf8Safely(bytes)
                SrtEngine.parse(decoded, project.audioAsset?.durationMs, usedFallback)
            }.onSuccess { result ->
                pendingImportResult = result
            }.onFailure { err ->
                onShowMessage("SRT import failed: ${err.message}")
            }
        }
    }

    val exportSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val exportedText = SrtEngine.exportSrt(cues)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportedText.toByteArray(StandardCharsets.UTF_8))
                } ?: error("Could not write to selected SRT destination.")
            }.onSuccess {
                onShowMessage("Exported UTF-8 SRT (${cues.size} cues).")
            }.onFailure { err ->
                onShowMessage("SRT export failed: ${err.message}")
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Toolbar: Import, Paste, Add, Offset, Search/Replace, Export
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
                        text = "UTF-8 SRT Subtitles (${cues.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = SrtEngine.formatTimecode(cues.maxOfOrNull { it.endMs } ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { importSrtLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("srt_import_button"),
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import .srt", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showPasteDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("srt_paste_button"),
                    ) {
                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste SRT")
                    }

                    OutlinedButton(
                        onClick = {
                            val lastEnd = cues.maxOfOrNull { it.endMs } ?: 0L
                            val newCue = SubtitleCue(
                                id = UUID.randomUUID().toString(),
                                index = cues.size + 1,
                                startMs = if (cues.isEmpty()) 0L else lastEnd + 100L,
                                endMs = if (cues.isEmpty()) 2_500L else lastEnd + 2_600L,
                                text = "নতুন বাংলা সাবটাইটেল",
                            )
                            onUpdateSubtitles(cues + newCue)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("srt_add_cue_button"),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Cue")
                    }

                    OutlinedButton(
                        onClick = { showSearchReplaceDialog = true },
                        enabled = cues.isNotEmpty(),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Outlined.FindReplace, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Replace")
                    }

                    OutlinedButton(
                        onClick = {
                            val safeName = project.name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").ifBlank { "subtitles" }
                            exportSrtLauncher.launch("$safeName.srt")
                        },
                        enabled = cues.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("srt_export_button"),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .srt")
                    }
                }

                // Global Offset Controls
                if (cues.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Shift All:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        listOf(-500L to "-0.5s", -100L to "-0.1s", 100L to "+0.1s", 500L to "+0.5s").forEach { (delta, label) ->
                            OutlinedButton(
                                onClick = {
                                    onUpdateSubtitles(SrtEngine.shiftAll(cues, delta))
                                    onShowMessage("Shifted all cues by $label")
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Validation Report
        if (issues.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text("SRT Validation (${issues.size} notice${if (issues.size == 1) "" else "s"})", style = MaterialTheme.typography.labelLarge)
                    }
                    issues.take(4).forEach { issue ->
                        Text(
                            text = "• [${issue.severity}] ${issue.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        // Cue List
        if (cues.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "No subtitles yet. Import a UTF-8 .srt file, paste SRT text, add cues manually, or split from your script offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            cues.forEachIndexed { idx, cue ->
                SubtitleCueCard(
                    cue = cue,
                    canMergeNext = idx < cues.lastIndex,
                    onEdit = { editingCue = cue },
                    onSplit = { onUpdateSubtitles(SrtEngine.splitCue(cues, cue.id)) },
                    onMergeNext = { onUpdateSubtitles(SrtEngine.mergeWithNext(cues, cue.id)) },
                    onDuplicate = { onUpdateSubtitles(SrtEngine.duplicateCue(cues, cue.id)) },
                    onDelete = { onUpdateSubtitles(SrtEngine.renumber(cues.filterNot { it.id == cue.id })) },
                )
            }
        }
    }

    // Import Summary & Overwrite Confirmation Dialog
    pendingImportResult?.let { parsed ->
        AlertDialog(
            onDismissRequest = { pendingImportResult = null },
            title = { Text("SRT Import Summary") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Parsed cues: ${parsed.cues.size}")
                    Text("Total duration: ${SrtEngine.formatTimecode(parsed.totalDurationMs)}")
                    Text("Errors: ${parsed.errorCount} · Warnings: ${parsed.warningCount}")
                    if (parsed.usedEncodingFallback) {
                        Text(
                            "Warning: Non-UTF-8 bytes detected; fallback decoding used.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (cues.isNotEmpty()) {
                        Text(
                            "Confirming will replace your existing ${cues.size} subtitle cue(s).",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    parsed.issues.take(5).forEach { issue ->
                        Text(
                            text = "• Block #${issue.blockNumber} (L${issue.lineNumber}): ${issue.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (issue.severity == SrtIssueSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSubtitles(parsed.cues)
                        onShowMessage("Imported ${parsed.cues.size} SRT cues.")
                        pendingImportResult = null
                    },
                    enabled = parsed.cues.isNotEmpty(),
                ) {
                    Text(if (cues.isEmpty()) "Import" else "Replace Existing")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportResult = null }) { Text("Cancel") }
            },
        )
    }

    if (showPasteDialog) {
        PasteSrtDialog(
            mediaDurationMs = project.audioAsset?.durationMs,
            onDismiss = { showPasteDialog = false },
            onParsed = { result ->
                showPasteDialog = false
                pendingImportResult = result
            },
        )
    }

    if (showSearchReplaceDialog) {
        SearchReplaceSrtDialog(
            onDismiss = { showSearchReplaceDialog = false },
            onReplace = { query, replacement ->
                val (updated, count) = SrtEngine.searchAndReplace(cues, query, replacement)
                onUpdateSubtitles(updated)
                onShowMessage("Replaced $count occurrence(s).")
                showSearchReplaceDialog = false
            },
        )
    }

    editingCue?.let { target ->
        EditSubtitleCueDialog(
            cue = target,
            onDismiss = { editingCue = null },
            onSave = { updated ->
                val list = cues.map { if (it.id == updated.id) updated else it }
                onUpdateSubtitles(SrtEngine.renumber(list))
                editingCue = null
            },
        )
    }
}

@Composable
private fun SubtitleCueCard(
    cue: SubtitleCue,
    canMergeNext: Boolean,
    onEdit: () -> Unit,
    onSplit: () -> Unit,
    onMergeNext: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("srt_cue_card_${cue.index}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "#${cue.index}  ${SrtEngine.formatTimecode(cue.startMs)} → ${SrtEngine.formatTimecode(cue.endMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit cue", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onSplit, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.CallSplit, contentDescription = "Split cue", modifier = Modifier.size(18.dp))
                    }
                    if (canMergeNext) {
                        IconButton(onClick = onMergeNext, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Outlined.CallMerge, contentDescription = "Merge with next cue", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate cue", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete cue", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = cue.text.ifBlank { "(Empty cue)" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PasteSrtDialog(
    mediaDurationMs: Long?,
    onDismiss: () -> Unit,
    onParsed: (SrtParseResult) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste UTF-8 SRT") },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                minLines = 6,
                maxLines = 10,
                placeholder = {
                    Text("1\n00:00:01,000 --> 00:00:03,500\nআজকের গল্পে তোমাকে স্বাগতম।")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("paste_srt_input"),
            )
        },
        confirmButton = {
            Button(
                onClick = { onParsed(SrtEngine.parse(raw, mediaDurationMs)) },
                enabled = raw.isNotBlank(),
            ) {
                Text("Parse & Validate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SearchReplaceSrtDialog(
    onDismiss: () -> Unit,
    onReplace: (String, String) -> Unit,
) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search & Replace Subtitles") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    label = { Text("Find text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    label = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onReplace(find, replace) }, enabled = find.isNotEmpty()) {
                Text("Replace All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditSubtitleCueDialog(
    cue: SubtitleCue,
    onDismiss: () -> Unit,
    onSave: (SubtitleCue) -> Unit,
) {
    var startText by remember(cue.id) { mutableStateOf(SrtEngine.formatTimecode(cue.startMs)) }
    var endText by remember(cue.id) { mutableStateOf(SrtEngine.formatTimecode(cue.endMs)) }
    var bodyText by remember(cue.id) { mutableStateOf(cue.text) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Cue #${cue.index}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; errorMsg = null },
                    label = { Text("Start (HH:MM:SS,mmm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it; errorMsg = null },
                    label = { Text("End (HH:MM:SS,mmm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { bodyText = it },
                    label = { Text("Subtitle Text (Bengali / English)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startMs = SrtEngine.parseSingleTimecode(startText)
                    val endMs = SrtEngine.parseSingleTimecode(endText)
                    when {
                        startMs == null || endMs == null ->
                            errorMsg = "Use valid HH:MM:SS,mmm format (comma milliseconds)."
                        endMs <= startMs ->
                            errorMsg = "End time must be strictly after start time."
                        else -> onSave(cue.copy(startMs = startMs, endMs = endMs, text = bodyText))
                    }
                },
            ) {
                Text("Save Cue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

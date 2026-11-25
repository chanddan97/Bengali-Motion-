package com.example.feature.watermark

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
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.core.model.Project
import com.example.core.model.WatermarkPosition
import com.example.core.model.WatermarkSettings
import com.example.core.render.RenderFrameInput
import com.example.core.render.SceneFrameRenderer
import com.example.core.render.WatermarkLayoutCalculator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkStepPanel(
    project: Project,
    renderer: SceneFrameRenderer,
    onUpdateWatermarkSettings: (WatermarkSettings) -> Unit,
    onSaveAsGlobalDefault: (WatermarkSettings) -> Unit,
    onImportWatermarkImage: (Uri, (Result<String>) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wm = project.watermarkSettings
    var imageDecodeError by remember { mutableStateOf<String?>(null) }

    val userLogoResult = remember(wm.user.assetId, wm.user.kind) {
        if (wm.user.kind == "image" && !wm.user.assetId.isNullOrBlank()) {
            SceneFrameRenderer.decodeBitmapSafely(wm.user.assetId, 512)
        } else {
            Result.success(null)
        }
    }
    val userLogoBitmap = userLogoResult.getOrNull()

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            onImportWatermarkImage(uri) { result ->
                result.onSuccess { localPath ->
                    imageDecodeError = null
                    onUpdateWatermarkSettings(
                        wm.copy(
                            user = wm.user.copy(
                                enabled = true,
                                kind = "image",
                                assetId = localPath,
                            ),
                        ),
                    )
                    onShowMessage("Transparent PNG/WebP watermark imported.")
                }.onFailure { err ->
                    imageDecodeError = err.message ?: "Could not decode image watermark."
                    onShowMessage("Watermark image error: $imageDecodeError")
                }
            }
        }
    }

    val (previewW, previewH) = remember(project.aspectRatio) {
        when (project.aspectRatio) {
            com.example.core.model.AspectRatio.PORTRAIT_9_16 -> 320 to 568
            com.example.core.model.AspectRatio.SQUARE_1_1 -> 420 to 420
            com.example.core.model.AspectRatio.PORTRAIT_4_5 -> 360 to 450
            com.example.core.model.AspectRatio.LANDSCAPE_16_9 -> 560 to 315
        }
    }

    val overlapResult = remember(wm, previewW, previewH, userLogoBitmap) {
        renderer.evaluateWatermarkOverlap(
            settings = wm,
            canvasWidth = previewW.toFloat(),
            canvasHeight = previewH.toFloat(),
            userBitmap = userLogoBitmap,
            timeMs = 1_000L,
        )
    }

    val previewBitmap = remember(project, wm, userLogoBitmap, previewW, previewH) {
        renderer.renderToBitmap(
            RenderFrameInput(
                project = project.copy(watermarkSettings = wm),
                timeMs = 1_000L,
                width = previewW,
                height = previewH,
                userWatermarkBitmap = userLogoBitmap,
            ),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Live Parity Preview Card
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
                        text = "Watermark Preview (${project.aspectRatio.label})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Exact Export Coordinates",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Box(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .aspectRatio(project.aspectRatio.ratioValue)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("watermark_preview_canvas"),
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Live watermark placement preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Overlap warning + one-tap reposition action
                if (overlapResult.overlaps) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("watermark_overlap_warning"),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = "User & Developer watermarks overlap.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Button(
                                onClick = {
                                    val suggested = WatermarkLayoutCalculator.suggestNonOverlappingDeveloperPosition(wm.user.position)
                                    onUpdateWatermarkSettings(
                                        wm.copy(developer = wm.developer.copy(position = suggested, offsetXPercent = 0f, offsetYPercent = 0f)),
                                    )
                                },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("watermark_auto_reposition_button"),
                            ) {
                                Text("Reposition")
                            }
                        }
                    }
                }
            }
        }

        // Section 1: User Watermark (Option B — OFF by default)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "User Watermark (Your Brand)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "OFF by default. Add your channel name or transparent PNG/WebP logo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = wm.user.enabled,
                        onCheckedChange = { enabled ->
                            val defaultText = if (enabled && wm.user.text.isNullOrBlank() && wm.user.kind == "text") {
                                project.name
                            } else {
                                wm.user.text
                            }
                            onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(enabled = enabled, text = defaultText)))
                        },
                        modifier = Modifier.testTag("user_watermark_switch"),
                    )
                }

                if (wm.user.enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = wm.user.kind == "text",
                            onClick = { onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(kind = "text"))) },
                            label = { Text("Text") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                        FilterChip(
                            selected = wm.user.kind == "image",
                            onClick = { onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(kind = "image"))) },
                            label = { Text("PNG / WebP Logo") },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }

                    if (wm.user.kind == "text") {
                        OutlinedTextField(
                            value = wm.user.text.orEmpty(),
                            onValueChange = { txt ->
                                onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(text = txt)))
                            },
                            label = { Text("Channel / Brand Text (Bengali or English)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_watermark_text_input"),
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                logoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pick_watermark_logo_button"),
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (userLogoBitmap != null) "Change Transparent PNG/WebP Logo" else "Pick Transparent PNG/WebP Logo")
                        }
                        if (userLogoResult.isFailure || !imageDecodeError.isNullOrBlank()) {
                            Text(
                                text = imageDecodeError ?: "Unable to decode selected logo image.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Text("Position Preset", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        WatermarkPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = wm.user.position == pos.id,
                                onClick = {
                                    onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(position = pos.id)))
                                },
                                label = { Text(pos.displayLabel) },
                            )
                        }
                    }

                    Text("Size / Scale: ${String.format("%.2fx", wm.user.scale)}", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = wm.user.scale.coerceIn(0.4f, 2.0f),
                        onValueChange = { v -> onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(scale = v))) },
                        valueRange = 0.4f..2.0f,
                    )

                    Text("Opacity: ${(wm.user.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = wm.user.opacity.coerceIn(0.1f, 1.0f),
                        onValueChange = { v -> onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(opacity = v))) },
                        valueRange = 0.1f..1.0f,
                    )

                    Text("Safe Margin: ${wm.user.marginPercent.toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = wm.user.marginPercent.coerceIn(0f, 15f),
                        onValueChange = { v -> onUpdateWatermarkSettings(wm.copy(user = wm.user.copy(marginPercent = v))) },
                        valueRange = 0f..15f,
                    )
                }
            }
        }

        // Section 2: Optional Developer Watermark (Option C — OFF by default)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer Watermark (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Always OFF by default. Independently toggleable; never forced on exports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = wm.developer.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateWatermarkSettings(wm.copy(developer = wm.developer.copy(enabled = enabled)))
                        },
                        modifier = Modifier.testTag("developer_watermark_switch"),
                    )
                }

                if (wm.developer.enabled) {
                    Text(
                        text = "Label: ${wm.developer.text ?: "Made with BanglaMotion"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        WatermarkPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = wm.developer.position == pos.id,
                                onClick = {
                                    onUpdateWatermarkSettings(wm.copy(developer = wm.developer.copy(position = pos.id)))
                                },
                                label = { Text(pos.displayLabel) },
                            )
                        }
                    }
                    Text("Size: ${String.format("%.2fx", wm.developer.scale)}", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = wm.developer.scale.coerceIn(0.4f, 1.5f),
                        onValueChange = { v -> onUpdateWatermarkSettings(wm.copy(developer = wm.developer.copy(scale = v))) },
                        valueRange = 0.4f..1.5f,
                    )
                    Text("Opacity: ${(wm.developer.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = wm.developer.opacity.coerceIn(0.15f, 1.0f),
                        onValueChange = { v -> onUpdateWatermarkSettings(wm.copy(developer = wm.developer.copy(opacity = v))) },
                        valueRange = 0.15f..1.0f,
                    )
                }
            }
        }

        // Save global default button
        OutlinedButton(
            onClick = { onSaveAsGlobalDefault(wm) },
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("save_default_watermark_button"),
        ) {
            Icon(Icons.Outlined.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Use Current Watermark Settings as Default for New Projects", fontWeight = FontWeight.Medium)
        }
    }
}

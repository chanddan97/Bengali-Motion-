package com.example.feature.script

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.AiProxyConfig
import com.example.core.model.AppLanguage
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.feature.projects.AiAssistUiState

@Composable
fun ScriptStepPanel(
    project: Project,
    aiProxyConfig: AiProxyConfig,
    aiState: AiAssistUiState,
    onScriptChanged: (String) -> Unit,
    onGenerateSrtFromScript: () -> Unit,
    onAutoMatchScenes: () -> Unit,
    onUpdateAiConsent: (Boolean) -> Unit,
    onRequestAiAssist: () -> Unit,
    onApplyAiScript: (String) -> Unit,
    onApplyAiScenes: (List<SceneItem>) -> Unit,
    onDismissAiState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var localScript by remember(project.id) { mutableStateOf(project.script) }
    LaunchedEffect(project.script) {
        if (project.script != localScript) {
            localScript = project.script
        }
    }

    val wordCount = remember(localScript) {
        localScript.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.size ?: 0
    }
    val lineCount = remember(localScript) {
        localScript.lines().count { it.isNotBlank() }
    }
    val estSeconds = (wordCount * 0.42f).toInt().coerceAtLeast(if (wordCount > 0) 3 else 0)

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
                        text = when (project.language) {
                            AppLanguage.BENGALI -> "বাংলা ও ইংরেজি স্ক্রিপ্ট"
                            AppLanguage.ENGLISH -> "Bengali & English Script"
                            AppLanguage.BENGALI_ENGLISH -> "স্ক্রিপ্ট · Script Editor"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$wordCount words · $lineCount lines · ~${estSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = localScript,
                    onValueChange = { updated ->
                        localScript = updated
                        onScriptChanged(updated)
                    },
                    placeholder = {
                        Text(
                            "আজকের গল্পে তোমাকে স্বাগতম।\nচলো, বাংলা-মোশন দিয়ে একটি অসাধারণ ভিডিও বানাই।",
                        )
                    },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .testTag("script_editor_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onGenerateSrtFromScript,
                        enabled = localScript.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("generate_srt_from_script_button"),
                    ) {
                        Icon(Icons.Outlined.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Split to SRT", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onAutoMatchScenes,
                        enabled = localScript.isNotBlank() || project.subtitleCues.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("script_auto_match_button"),
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto Match")
                    }
                }
            }
        }

        // Milestone 8: Optional AI Assistant (Zero embedded keys, explicit consent & backend proxy required)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("optional_ai_card"),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = if (aiProxyConfig.isConfigured) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Optional AI Script & Scene Helper (Milestone 8)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (!aiProxyConfig.isConfigured) {
                    Text(
                        text = "Disabled: No HTTPS backend proxy URL is configured in Settings. No API keys are embedded in this APK. All core script, SRT, template, watermark, and MP4 export tools work 100% offline without AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_assist_disabled_button"),
                    ) {
                        Text("AI Unavailable (Configure Proxy in Settings)")
                    }
                } else {
                    Text(
                        text = "Proxy: ${aiProxyConfig.proxyBaseUrl} · Network & provider quotas apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = aiProxyConfig.consentGranted,
                            onCheckedChange = onUpdateAiConsent,
                            modifier = Modifier.testTag("ai_consent_checkbox"),
                        )
                        Text(
                            text = "I consent to sending this script text to my configured HTTPS proxy for JSON suggestions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Button(
                        onClick = onRequestAiAssist,
                        enabled = aiProxyConfig.consentGranted && localScript.isNotBlank() && aiState !is AiAssistUiState.Loading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_request_suggestions_button"),
                    ) {
                        if (aiState is AiAssistUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Requesting...")
                        } else {
                            Text("Request Strict JSON Suggestions")
                        }
                    }

                    when (aiState) {
                        is AiAssistUiState.Error -> {
                            Text(
                                text = aiState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        is AiAssistUiState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                        Text("Schema-validated AI suggestion ready", style = MaterialTheme.typography.labelLarge)
                                    }
                                    aiState.result.hookTitle?.let { hook ->
                                        Text("Hook: $hook", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    aiState.result.rewrittenScript?.let { rewritten ->
                                        Text(rewritten.take(240), style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick = {
                                            onApplyAiScript(rewritten)
                                            onDismissAiState()
                                        }) {
                                            Text("Apply Suggested Script")
                                        }
                                    }
                                    if (aiState.result.sceneDrafts.isNotEmpty()) {
                                        OutlinedButton(onClick = {
                                            var t = 0L
                                            val newScenes = aiState.result.sceneDrafts.mapIndexed { idx, draft ->
                                                val item = SceneItem(
                                                    orderIndex = idx,
                                                    headline = draft.headline,
                                                    caption = draft.caption,
                                                    startMs = t,
                                                    endMs = t + 3_000L,
                                                    templateId = draft.suggestedTemplateId,
                                                )
                                                t += 3_000L
                                                item
                                            }
                                            onApplyAiScenes(newScenes)
                                            onDismissAiState()
                                        }) {
                                            Text("Apply ${aiState.result.sceneDrafts.size} Suggested Scenes")
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

package com.example.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalStudioStrings
import com.example.core.model.AiProxyConfig
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.ExportResolution
import com.example.core.model.LocalStorageSummary
import com.example.core.storage.StudioPreferences
import com.example.feature.projects.AspectRatioOptionCard
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: StudioPreferences,
    storageSummary: LocalStorageSummary,
    onSelectAppLanguage: (AppLanguage) -> Unit,
    onSelectDefaultAspectRatio: (AspectRatio) -> Unit,
    onSelectDefaultResolution: (ExportResolution) -> Unit,
    onSaveAiProxyConfig: (AiProxyConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStudioStrings.current
    var proxyUrlInput by remember(preferences.aiProxyConfig.proxyBaseUrl) {
        mutableStateOf(preferences.aiProxyConfig.proxyBaseUrl)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .testTag("settings_screen"),
        ) {
            item {
                Text(
                    text = strings.settingsScreenTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Interface Language Selector
            item {
                SettingsSectionCard(
                    icon = Icons.Outlined.Translate,
                    title = strings.uiLanguageSectionTitle,
                    subtitle = strings.uiLanguageSectionHint,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = preferences.appLanguage == lang,
                                onClick = { onSelectAppLanguage(lang) },
                                label = {
                                    Text(
                                        text = lang.displayLabel,
                                        fontWeight = if (preferences.appLanguage == lang) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_lang_${lang.code}"),
                            )
                        }
                    }
                }
            }

            // Default New Project Format
            item {
                SettingsSectionCard(
                    icon = Icons.Outlined.AspectRatio,
                    title = strings.defaultProjectFormatTitle,
                    subtitle = strings.defaultProjectFormatHint,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AspectRatio.entries.forEach { ratio ->
                            AspectRatioOptionCard(
                                aspectRatio = ratio,
                                selected = preferences.defaultAspectRatio == ratio,
                                onClick = { onSelectDefaultAspectRatio(ratio) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_ratio_${ratio.label.replace(":", "_")}"),
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportResolution.entries.forEach { res ->
                            FilterChip(
                                selected = preferences.defaultResolution == res,
                                onClick = { onSelectDefaultResolution(res) },
                                label = { Text("${res.label} · 30 ${strings.fpsSuffix}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_res_${res.label}"),
                            )
                        }
                    }
                }
            }

            // Optional Milestone 8 AI Backend Proxy Settings (Zero Embedded API Keys)
            item {
                SettingsSectionCard(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "Optional AI Backend Proxy (Milestone 8)",
                    subtitle = "Leave blank to keep AI completely OFF. Never put production API keys in an APK; connect only to your own HTTPS backend proxy if desired.",
                ) {
                    OutlinedTextField(
                        value = proxyUrlInput,
                        onValueChange = { proxyUrlInput = it },
                        label = { Text("HTTPS Proxy Base URL (Optional)") },
                        placeholder = { Text("https://your-proxy.example.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_proxy_url_input"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onSaveAiProxyConfig(
                                    preferences.aiProxyConfig.copy(proxyBaseUrl = proxyUrlInput.trim()),
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_ai_proxy_button"),
                        ) {
                            Text("Save Proxy")
                        }
                        if (proxyUrlInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    proxyUrlInput = ""
                                    onSaveAiProxyConfig(AiProxyConfig())
                                },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text("Clear / Disable")
                            }
                        }
                    }
                }
            }

            // Local Storage & Offline Telemetry
            item {
                SettingsSectionCard(
                    icon = Icons.Outlined.FolderCopy,
                    title = strings.localStorageSectionTitle,
                    subtitle = null,
                ) {
                    StorageInfoRow(
                        label = strings.localProjectsSummaryLabel,
                        value = "${storageSummary.activeProjectCount} ${strings.filterActive} · ${storageSummary.archivedProjectCount} ${strings.filterArchived}",
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    StorageInfoRow(
                        label = strings.localDiskUsedLabel,
                        value = formatByteSize(storageSummary.usedBytes),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    StorageInfoRow(
                        label = strings.localDiskFreeLabel,
                        value = formatByteSize(storageSummary.availableBytes),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = strings.localStoragePathLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = storageSummary.storagePath.ifBlank { "App-private storage" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Privacy & Security Guarantees
            item {
                SettingsSectionCard(
                    icon = Icons.Outlined.Security,
                    title = strings.privacyGuaranteeTitle,
                    subtitle = null,
                ) {
                    Text(
                        text = strings.privacyGuaranteeBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Full Build Summary
            item {
                SettingsSectionCard(
                    icon = Icons.Outlined.Tune,
                    title = "BanglaMotion Studio · Full Suite (M1–M8)",
                    subtitle = null,
                ) {
                    Text(
                        text = "Active modules: Projects CRUD (Room), Script & UTF-8 SRT Engine, 20 JSON-Validated Templates (10 Observed + 10 Proposed), Audio Import/Record/Playback, Auto Match & Scene Editor, Shared Deterministic Preview, User + Optional Developer Watermarks (OFF by default), Real MediaCodec H.264/AAC MP4 Export, and Optional HTTPS AI Proxy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    content: @Composable () -> Unit,
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun StorageInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

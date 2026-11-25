package com.example.feature.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalStudioStrings
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.ExportResolution
import com.example.core.model.Project
import com.example.core.storage.ProjectNameValidator
import com.example.core.storage.ProjectValidationError

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateProjectDialog(
    defaultLanguage: AppLanguage,
    defaultAspectRatio: AspectRatio,
    defaultResolution: ExportResolution,
    onDismiss: () -> Unit,
    onConfirm: (String, AppLanguage, AspectRatio, ExportResolution) -> Unit,
) {
    val strings = LocalStudioStrings.current
    var name by rememberSaveable { mutableStateOf("") }
    var selectedAspectRatio by rememberSaveable { mutableStateOf(defaultAspectRatio) }
    var selectedLanguage by rememberSaveable { mutableStateOf(defaultLanguage) }
    var selectedResolution by rememberSaveable { mutableStateOf(defaultResolution) }
    var showError by rememberSaveable { mutableStateOf(false) }

    val validationError = ProjectNameValidator.validate(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = strings.createProjectTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (showError) showError = false
                    },
                    label = { Text(strings.projectNameLabel) },
                    placeholder = { Text(strings.projectNamePlaceholder) },
                    singleLine = true,
                    isError = showError && validationError != null,
                    supportingText = {
                        if (showError && validationError != null) {
                            Text(
                                text = when (validationError) {
                                    ProjectValidationError.EmptyName -> strings.projectNameRequiredError
                                    ProjectValidationError.NameTooLong -> strings.projectNameTooLongError
                                },
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_project_name_input"),
                )

                Text(
                    text = strings.aspectRatioLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AspectRatio.entries.forEach { ratio ->
                        val isSelected = selectedAspectRatio == ratio
                        AspectRatioOptionCard(
                            aspectRatio = ratio,
                            selected = isSelected,
                            onClick = { selectedAspectRatio = ratio },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_ratio_${ratio.label.replace(":", "_")}"),
                        )
                    }
                }

                Text(
                    text = strings.projectLanguageLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang.displayLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("create_lang_${lang.code}"),
                        )
                    }
                }

                Text(
                    text = strings.resolutionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportResolution.entries.forEach { res ->
                        FilterChip(
                            selected = selectedResolution == res,
                            onClick = { selectedResolution = res },
                            label = { Text("${res.label} · 30 ${strings.fpsSuffix}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("create_res_${res.label}"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validationError == null) {
                        onConfirm(
                            ProjectNameValidator.sanitize(name),
                            selectedLanguage,
                            selectedAspectRatio,
                            selectedResolution,
                        )
                    } else {
                        showError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_create_project_button"),
            ) {
                Text(strings.createAction, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_create_project_button"),
            ) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
fun AspectRatioOptionCard(
    aspectRatio: AspectRatio,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val frameColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val (w, h) = when (aspectRatio) {
                    AspectRatio.PORTRAIT_9_16 -> 12.dp to 22.dp
                    AspectRatio.SQUARE_1_1 -> 18.dp to 18.dp
                    AspectRatio.PORTRAIT_4_5 -> 16.dp to 20.dp
                    AspectRatio.LANDSCAPE_16_9 -> 22.dp to 13.dp
                }
                Box(
                    modifier = Modifier
                        .width(w)
                        .height(h)
                        .border(1.8.dp, frameColor, RoundedCornerShape(3.dp))
                        .background(frameColor.copy(alpha = 0.14f), RoundedCornerShape(3.dp)),
                )
            }
            Text(
                text = aspectRatio.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun RenameProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val strings = LocalStudioStrings.current
    var name by rememberSaveable(project.id) { mutableStateOf(project.name) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val validationError = ProjectNameValidator.validate(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = strings.renameProjectTitle,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (showError) showError = false
                },
                label = { Text(strings.projectNameLabel) },
                singleLine = true,
                isError = showError && validationError != null,
                supportingText = {
                    if (showError && validationError != null) {
                        Text(
                            text = when (validationError) {
                                ProjectValidationError.EmptyName -> strings.projectNameRequiredError
                                ProjectValidationError.NameTooLong -> strings.projectNameTooLongError
                            },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_project_input"),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validationError == null) {
                        onConfirm(ProjectNameValidator.sanitize(name))
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_rename_project_button"),
            ) {
                Text(strings.saveAction, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
fun DeleteProjectConfirmDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val strings = LocalStudioStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = strings.deleteProjectTitle,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = strings.deleteProjectMessage(project.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_delete_project_button"),
            ) {
                Text(strings.deleteAction, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(strings.cancelAction)
            }
        },
    )
}

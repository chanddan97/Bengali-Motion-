package com.example.feature.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.OfflinePin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.i18n.LocalStudioStrings
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.ExportResolution
import com.example.core.model.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(
    uiState: ProjectsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onFilterTabSelected: (ProjectFilterTab) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onCreateProject: (String, AppLanguage, AspectRatio, ExportResolution) -> Unit,
    onOpenProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String, String) -> Unit,
    onSetArchived: (String, Boolean) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStudioStrings.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        val columnCount = if (maxWidth >= 700.dp) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .align(Alignment.TopCenter)
                .testTag("projects_grid"),
        ) {
            item(span = { GridItemSpan(columnCount) }) {
                StudioHeaderBanner(
                    currentLanguage = uiState.preferences.appLanguage,
                    onLanguageSelected = onLanguageSelected,
                )
            }

            item(span = { GridItemSpan(columnCount) }) {
                ProjectsSearchAndFilterBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    selectedTab = uiState.filterTab,
                    activeCount = uiState.activeCount,
                    archivedCount = uiState.archivedCount,
                    onFilterTabSelected = onFilterTabSelected,
                )
            }

            if (!uiState.isLoading && uiState.visibleProjects.isEmpty()) {
                item(span = { GridItemSpan(columnCount) }) {
                    ProjectsEmptyState(
                        filterTab = uiState.filterTab,
                        hasSearchQuery = uiState.searchQuery.isNotBlank(),
                        onCreateFirstProject = { showCreateDialog = true },
                    )
                }
            } else {
                items(
                    items = uiState.visibleProjects,
                    key = { it.id },
                ) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpenProject(project.id) },
                        onRename = { projectToRename = project },
                        onDuplicate = { onDuplicateProject(project.id, strings.duplicateSuffix) },
                        onToggleArchive = { onSetArchived(project.id, !project.isArchived) },
                        onDelete = { projectToDelete = project },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.cd_create_project),
                )
            },
            text = {
                Text(
                    text = strings.newProjectCta,
                    fontWeight = FontWeight.Bold,
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_project_fab"),
        )
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            defaultLanguage = uiState.preferences.appLanguage,
            defaultAspectRatio = uiState.preferences.defaultAspectRatio,
            defaultResolution = uiState.preferences.defaultResolution,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, lang, ratio, res ->
                showCreateDialog = false
                onCreateProject(name, lang, ratio, res)
            },
        )
    }

    projectToRename?.let { target ->
        RenameProjectDialog(
            project = target,
            onDismiss = { projectToRename = null },
            onConfirm = { updatedName ->
                onRenameProject(target.id, updatedName)
                projectToRename = null
            },
        )
    }

    projectToDelete?.let { target ->
        DeleteProjectConfirmDialog(
            project = target,
            onDismiss = { projectToDelete = null },
            onConfirm = {
                onDeleteProject(target.id)
                projectToDelete = null
            },
        )
    }
}

@Composable
private fun StudioHeaderBanner(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    val strings = LocalStudioStrings.current
    var showLanguageMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_banglamotion_mark),
                        contentDescription = stringResource(R.string.cd_app_logo),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp),
                            ),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = strings.appSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showLanguageMenu = true }
                            .minimumInteractiveComponentSize()
                            .testTag("header_language_switcher"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = stringResource(R.string.cd_language_switcher),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = currentLanguage.shortBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = lang.displayLabel,
                                        fontWeight = if (lang == currentLanguage) FontWeight.Bold else FontWeight.Normal,
                                        color = if (lang == currentLanguage) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                onClick = {
                                    onLanguageSelected(lang)
                                    showLanguageMenu = false
                                },
                                modifier = Modifier.testTag("lang_option_${lang.code}"),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            imageVector = Icons.Outlined.OfflinePin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = strings.offlineReadyBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = "20 Templates · Local MP4",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectsSearchAndFilterBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedTab: ProjectFilterTab,
    activeCount: Int,
    archivedCount: Int,
    onFilterTabSelected: (ProjectFilterTab) -> Unit,
) {
    val strings = LocalStudioStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text(strings.searchProjectsHint) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.cd_search_projects),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChanged("") },
                        modifier = Modifier.testTag("clear_search_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_clear_search),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("projects_search_input"),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedTab == ProjectFilterTab.ACTIVE,
                onClick = { onFilterTabSelected(ProjectFilterTab.ACTIVE) },
                label = { Text(strings.activeCountLabel(activeCount)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("filter_active_chip"),
            )
            FilterChip(
                selected = selectedTab == ProjectFilterTab.ARCHIVED,
                onClick = { onFilterTabSelected(ProjectFilterTab.ARCHIVED) },
                label = { Text(strings.archivedCountLabel(archivedCount)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("filter_archived_chip"),
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = LocalStudioStrings.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AspectRatioThumbBadge(aspectRatio = project.aspectRatio)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetaTag(text = project.language.shortBadge, highlight = true)
                    MetaTag(text = project.resolution.label)
                    MetaTag(text = "${project.fps} ${strings.fpsSuffix}")
                }

                Text(
                    text = "${strings.updatedPrefix} ${formatTimestamp(project.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("project_menu_${project.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_project_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                        text = { Text(strings.actionOpen) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        },
                        modifier = Modifier.testTag("menu_open_${project.id}"),
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                        text = { Text(strings.actionRename) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                        modifier = Modifier.testTag("menu_rename_${project.id}"),
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        text = { Text(strings.actionDuplicate) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        },
                        modifier = Modifier.testTag("menu_duplicate_${project.id}"),
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = if (project.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                contentDescription = null,
                            )
                        },
                        text = {
                            Text(if (project.isArchived) strings.actionRestore else strings.actionArchive)
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleArchive()
                        },
                        modifier = Modifier.testTag("menu_archive_${project.id}"),
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        text = {
                            Text(
                                text = strings.actionDelete,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("menu_delete_${project.id}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AspectRatioThumbBadge(aspectRatio: AspectRatio) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(56.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val (w, h) = when (aspectRatio) {
                AspectRatio.PORTRAIT_9_16 -> 12.dp to 21.dp
                AspectRatio.SQUARE_1_1 -> 17.dp to 17.dp
                AspectRatio.PORTRAIT_4_5 -> 15.dp to 19.dp
                AspectRatio.LANDSCAPE_16_9 -> 22.dp to 13.dp
            }
            Box(
                modifier = Modifier
                    .width(w)
                    .height(h)
                    .border(
                        1.8.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(3.dp),
                    )
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        RoundedCornerShape(3.dp),
                    ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = aspectRatio.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaTag(
    text: String,
    highlight: Boolean = false,
) {
    Surface(
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ProjectsEmptyState(
    filterTab: ProjectFilterTab,
    hasSearchQuery: Boolean,
    onCreateFirstProject: () -> Unit,
) {
    val strings = LocalStudioStrings.current
    val (title, body) = when {
        hasSearchQuery -> strings.emptySearchTitle to strings.emptySearchBody
        filterTab == ProjectFilterTab.ARCHIVED -> strings.emptyArchivedTitle to strings.emptyArchivedBody
        else -> strings.emptyProjectsTitle to strings.emptyProjectsBody
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("projects_empty_state"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MovieCreation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasSearchQuery && filterTab == ProjectFilterTab.ACTIVE) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCreateFirstProject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("empty_create_project_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.createFirstProjectCta, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return formatter.format(Date(epochMs))
}

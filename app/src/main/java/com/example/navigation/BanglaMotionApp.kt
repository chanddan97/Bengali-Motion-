package com.example.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.i18n.LocalStudioStrings
import com.example.core.i18n.stringsFor
import com.example.feature.projects.ProjectWorkspaceScreen
import com.example.feature.projects.ProjectsScreen
import com.example.feature.projects.StudioFeedbackEvent
import com.example.feature.projects.StudioViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.templates.TemplatesGalleryScreen

object StudioRoutes {
    const val PROJECTS = "projects"
    const val TEMPLATES = "templates"
    const val SETTINGS = "settings"
    const val PROJECT_WORKSPACE_PATTERN = "project/{projectId}"

    fun projectWorkspace(projectId: String): String = "project/$projectId"
}

private data class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val label: () -> String,
)

@Composable
fun BanglaMotionApp(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val strings = remember(uiState.preferences.appLanguage) {
        stringsFor(uiState.preferences.appLanguage)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topDestinations = remember(strings) {
        listOf(
            TopLevelDestination(
                route = StudioRoutes.PROJECTS,
                selectedIcon = Icons.Filled.Folder,
                unselectedIcon = Icons.Outlined.Folder,
                testTag = "nav_projects",
                label = { strings.navProjects },
            ),
            TopLevelDestination(
                route = StudioRoutes.TEMPLATES,
                selectedIcon = Icons.Filled.DashboardCustomize,
                unselectedIcon = Icons.Outlined.DashboardCustomize,
                testTag = "nav_templates",
                label = { strings.navTemplates },
            ),
            TopLevelDestination(
                route = StudioRoutes.SETTINGS,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings",
                label = { strings.navSettings },
            ),
        )
    }

    val isTopLevel = currentRoute in setOf(
        StudioRoutes.PROJECTS,
        StudioRoutes.TEMPLATES,
        StudioRoutes.SETTINGS,
    )

    LaunchedEffect(viewModel, strings) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is StudioFeedbackEvent.ProjectCreated -> strings.projectCreatedMessage(event.name)
                StudioFeedbackEvent.ProjectRenamed -> strings.projectRenamedMessage
                StudioFeedbackEvent.ProjectDuplicated -> strings.projectDuplicatedMessage
                StudioFeedbackEvent.ProjectArchived -> strings.projectArchivedMessage
                StudioFeedbackEvent.ProjectRestored -> strings.projectRestoredMessage
                StudioFeedbackEvent.ProjectDeleted -> strings.projectDeletedMessage
                StudioFeedbackEvent.FormatUpdated -> strings.formatUpdatedMessage
                is StudioFeedbackEvent.CustomMessage -> event.message
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    CompositionLocalProvider(LocalStudioStrings provides strings) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            val navigateTopLevel: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (isTopLevel && !useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier.testTag("studio_bottom_nav"),
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label(),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    modifier = Modifier.testTag(dest.testTag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (isTopLevel && useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.testTag("studio_nav_rail"),
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label(),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.label(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    modifier = Modifier.testTag(dest.testTag),
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = StudioRoutes.PROJECTS,
                        modifier = Modifier.weight(1f),
                    ) {
                        composable(StudioRoutes.PROJECTS) {
                            ProjectsScreen(
                                uiState = uiState,
                                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                                onFilterTabSelected = viewModel::onFilterTabSelected,
                                onLanguageSelected = viewModel::setAppLanguage,
                                onCreateProject = { name, lang, ratio, res ->
                                    viewModel.createProject(name, lang, ratio, res) { created ->
                                        navController.navigate(StudioRoutes.projectWorkspace(created.id))
                                    }
                                },
                                onOpenProject = { projectId ->
                                    viewModel.resetExportState()
                                    navController.navigate(StudioRoutes.projectWorkspace(projectId))
                                },
                                onRenameProject = viewModel::renameProject,
                                onDuplicateProject = viewModel::duplicateProject,
                                onSetArchived = viewModel::setArchived,
                                onDeleteProject = viewModel::deleteProject,
                            )
                        }

                        composable(StudioRoutes.TEMPLATES) {
                            TemplatesGalleryScreen(
                                catalog = viewModel.templateCatalog,
                                renderer = viewModel.sceneFrameRenderer,
                                favoriteTemplateIds = uiState.preferences.favoriteTemplateIds,
                                recentTemplateIds = uiState.preferences.recentTemplateIds,
                                onToggleFavorite = viewModel::toggleFavoriteTemplate,
                            )
                        }

                        composable(StudioRoutes.SETTINGS) {
                            SettingsScreen(
                                preferences = uiState.preferences,
                                storageSummary = uiState.storageSummary,
                                onSelectAppLanguage = viewModel::setAppLanguage,
                                onSelectDefaultAspectRatio = viewModel::setDefaultAspectRatio,
                                onSelectDefaultResolution = viewModel::setDefaultResolution,
                                onSaveAiProxyConfig = viewModel::updateAiProxyConfig,
                            )
                        }

                        composable(
                            route = StudioRoutes.PROJECT_WORKSPACE_PATTERN,
                            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
                        ) { entry ->
                            val projectId = entry.arguments?.getString("projectId").orEmpty()
                            val projectFlow = remember(projectId) { viewModel.observeProject(projectId) }
                            val project by projectFlow.collectAsStateWithLifecycle(initialValue = null)

                            ProjectWorkspaceScreen(
                                project = project,
                                templateCatalog = viewModel.templateCatalog,
                                favoriteTemplateIds = uiState.preferences.favoriteTemplateIds,
                                recentTemplateIds = uiState.preferences.recentTemplateIds,
                                renderer = viewModel.sceneFrameRenderer,
                                audioEngine = viewModel.audioEngine,
                                exportState = exportState,
                                aiProxyConfig = uiState.preferences.aiProxyConfig,
                                aiState = aiState,
                                onBack = {
                                    viewModel.audioEngine.stopPlayback()
                                    navController.popBackStack()
                                },
                                onRenameProject = viewModel::renameProject,
                                onUpdateFormatAndLanguage = { id, ratio, lang, res ->
                                    viewModel.updateProjectFormatAndLanguage(id, ratio, lang, res)
                                },
                                onUpdateScript = viewModel::updateScript,
                                onGenerateSrtFromScript = viewModel::generateCuesFromScript,
                                onUpdateSubtitles = viewModel::updateSubtitles,
                                onApplyTemplate = { id, tplId -> viewModel.applyTemplateToProject(id, tplId, true) },
                                onToggleFavoriteTemplate = viewModel::toggleFavoriteTemplate,
                                onImportAudioUri = viewModel::importAudioUri,
                                onUpdateAudioAsset = viewModel::saveAudioAsset,
                                onAutoMatchScenes = { proj, varyTemplates -> viewModel.autoMatchScenes(proj, varyTemplates) },
                                onImportSceneJson = viewModel::importSceneMotionJson,
                                onUpdateScenes = viewModel::updateScenes,
                                onImportImage = viewModel::importImageIntoProject,
                                onUpdateWatermarkSettings = viewModel::updateWatermarkSettings,
                                onSaveWatermarkDefault = viewModel::saveWatermarkAsGlobalDefault,
                                onStartExport = viewModel::startMp4Export,
                                onCancelExport = viewModel::cancelMp4Export,
                                onSaveExportToGallery = viewModel::saveExportToMoviesGallery,
                                onUpdateAiConsent = { granted ->
                                    viewModel.updateAiProxyConfig(
                                        uiState.preferences.aiProxyConfig.copy(consentGranted = granted),
                                    )
                                },
                                onRequestAiAssist = viewModel::requestOptionalAiAssist,
                                onDismissAiState = viewModel::clearAiAssistState,
                                onShowMessage = viewModel::showMessage,
                            )
                        }
                    }
                }
            }
        }
    }
}

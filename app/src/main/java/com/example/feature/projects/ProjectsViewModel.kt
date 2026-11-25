package com.example.feature.projects

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.provider.AiAssistResult
import com.example.ai.provider.OptionalAiClient
import com.example.core.di.StudioAppContainer
import com.example.core.model.AiProxyConfig
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.AudioAsset
import com.example.core.model.ExportConfig
import com.example.core.model.ExportResolution
import com.example.core.model.ExportState
import com.example.core.model.LocalStorageSummary
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.core.model.SubtitleCue
import com.example.core.model.WatermarkSettings
import com.example.core.render.SceneFrameRenderer
import com.example.core.storage.AppPreferencesRepository
import com.example.core.storage.LocalAssetStorage
import com.example.core.storage.ProjectRepository
import com.example.core.storage.StudioPreferences
import com.example.data.templates.TemplateCatalogResult
import com.example.feature.audio.StudioAudioEngine
import com.example.feature.export.Mp4VideoExporter
import com.example.feature.subtitles.SrtEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ProjectFilterTab {
    ACTIVE,
    ARCHIVED,
}

sealed interface StudioFeedbackEvent {
    data class ProjectCreated(val name: String) : StudioFeedbackEvent
    data object ProjectRenamed : StudioFeedbackEvent
    data object ProjectDuplicated : StudioFeedbackEvent
    data object ProjectArchived : StudioFeedbackEvent
    data object ProjectRestored : StudioFeedbackEvent
    data object ProjectDeleted : StudioFeedbackEvent
    data object FormatUpdated : StudioFeedbackEvent
    data class CustomMessage(val message: String) : StudioFeedbackEvent
}

data class ProjectsUiState(
    val isLoading: Boolean = true,
    val allProjects: List<Project> = emptyList(),
    val visibleProjects: List<Project> = emptyList(),
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
    val filterTab: ProjectFilterTab = ProjectFilterTab.ACTIVE,
    val searchQuery: String = "",
    val preferences: StudioPreferences = StudioPreferences(),
    val storageSummary: LocalStorageSummary = LocalStorageSummary(),
)

sealed interface AiAssistUiState {
    data object Idle : AiAssistUiState
    data object Loading : AiAssistUiState
    data class Success(val result: AiAssistResult) : AiAssistUiState
    data class Error(val message: String) : AiAssistUiState
}

class StudioViewModel(
    private val appContext: Context,
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val localAssetStorage: LocalAssetStorage,
    val templateCatalog: TemplateCatalogResult,
    val sceneFrameRenderer: SceneFrameRenderer,
    val audioEngine: StudioAudioEngine,
    private val mp4VideoExporter: Mp4VideoExporter,
    private val optionalAiClient: OptionalAiClient,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val filterTab = MutableStateFlow(ProjectFilterTab.ACTIVE)
    private val storageSummary = MutableStateFlow(LocalStorageSummary())

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()
    private var exportJob: Job? = null

    private val _aiState = MutableStateFlow<AiAssistUiState>(AiAssistUiState.Idle)
    val aiState: StateFlow<AiAssistUiState> = _aiState.asStateFlow()

    private val _events = MutableSharedFlow<StudioFeedbackEvent>(extraBufferCapacity = 12)
    val events: SharedFlow<StudioFeedbackEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ProjectsUiState> = combine(
        projectRepository.observeAllProjects(),
        preferencesRepository.preferencesFlow,
        searchQuery,
        filterTab,
        storageSummary,
    ) { projects, prefs, query, tab, storage ->
        val activeProjects = projects.filter { !it.isArchived }
        val archivedProjects = projects.filter { it.isArchived }
        val baseList = when (tab) {
            ProjectFilterTab.ACTIVE -> activeProjects
            ProjectFilterTab.ARCHIVED -> archivedProjects
        }
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isEmpty()) {
            baseList
        } else {
            baseList.filter { project ->
                project.name.contains(normalizedQuery, ignoreCase = true) ||
                    project.aspectRatio.label.contains(normalizedQuery, ignoreCase = true) ||
                    project.language.displayLabel.contains(normalizedQuery, ignoreCase = true)
            }
        }
        ProjectsUiState(
            isLoading = false,
            allProjects = projects,
            visibleProjects = filtered,
            activeCount = activeProjects.size,
            archivedCount = archivedProjects.size,
            filterTab = tab,
            searchQuery = query,
            preferences = prefs,
            storageSummary = storage.copy(
                activeProjectCount = activeProjects.size,
                archivedProjectCount = archivedProjects.size,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectsUiState(),
    )

    init {
        refreshStorageSummary()
    }

    fun observeProject(projectId: String): Flow<Project?> =
        projectRepository.observeProjectById(projectId)

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onFilterTabSelected(tab: ProjectFilterTab) {
        filterTab.value = tab
    }

    fun showMessage(message: String) {
        _events.tryEmit(StudioFeedbackEvent.CustomMessage(message))
    }

    fun refreshStorageSummary() {
        viewModelScope.launch {
            val current = uiState.value
            storageSummary.value = projectRepository.readStorageSummary(
                activeCount = current.activeCount,
                archivedCount = current.archivedCount,
            )
        }
    }

    // --- Milestone 1: Project CRUD ---
    fun createProject(
        name: String,
        language: AppLanguage,
        aspectRatio: AspectRatio,
        resolution: ExportResolution,
        onCreated: (Project) -> Unit = {},
    ) {
        viewModelScope.launch {
            val defaultWatermark = uiState.value.preferences.defaultWatermarkSettings
            val result = projectRepository.createProject(
                name = name,
                language = language,
                aspectRatio = aspectRatio,
                resolution = resolution,
                initialWatermarkSettings = defaultWatermark,
            )
            result.onSuccess { created ->
                filterTab.value = ProjectFilterTab.ACTIVE
                _events.tryEmit(StudioFeedbackEvent.ProjectCreated(created.name))
                refreshStorageSummary()
                onCreated(created)
            }
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            projectRepository.renameProject(projectId, newName).onSuccess {
                _events.tryEmit(StudioFeedbackEvent.ProjectRenamed)
            }
        }
    }

    fun duplicateProject(projectId: String, copySuffix: String) {
        viewModelScope.launch {
            projectRepository.duplicateProject(projectId, copySuffix).onSuccess {
                _events.tryEmit(StudioFeedbackEvent.ProjectDuplicated)
                refreshStorageSummary()
            }
        }
    }

    fun setArchived(projectId: String, archived: Boolean) {
        viewModelScope.launch {
            projectRepository.setArchived(projectId, archived).onSuccess {
                _events.tryEmit(
                    if (archived) StudioFeedbackEvent.ProjectArchived
                    else StudioFeedbackEvent.ProjectRestored,
                )
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId).onSuccess {
                _events.tryEmit(StudioFeedbackEvent.ProjectDeleted)
                refreshStorageSummary()
            }
        }
    }

    fun updateProjectFormatAndLanguage(
        projectId: String,
        aspectRatio: AspectRatio,
        language: AppLanguage,
        resolution: ExportResolution,
        fps: Int = Project.DEFAULT_FPS,
    ) {
        viewModelScope.launch {
            projectRepository.updateProjectFormatAndLanguage(
                projectId = projectId,
                aspectRatio = aspectRatio,
                language = language,
                resolution = resolution,
                fps = fps,
            ).onSuccess {
                _events.tryEmit(StudioFeedbackEvent.FormatUpdated)
            }
        }
    }

    // --- Milestone 2: Script & Offline SRT ---
    fun updateScript(projectId: String, script: String) {
        viewModelScope.launch {
            projectRepository.updateScript(projectId, script)
        }
    }

    fun updateSubtitles(projectId: String, cues: List<SubtitleCue>) {
        viewModelScope.launch {
            projectRepository.updateSubtitles(projectId, cues)
        }
    }

    fun generateCuesFromScript(project: Project) {
        val generated = SrtEngine.generateCuesFromScript(project.script)
        if (generated.isEmpty()) {
            showMessage("Write a script first to generate offline SRT cues.")
            return
        }
        viewModelScope.launch {
            projectRepository.updateSubtitles(project.id, generated)
            showMessage("Generated ${generated.size} offline SRT cues from script.")
        }
    }

    // --- Milestone 3: Templates ---
    fun applyTemplateToProject(projectId: String, templateId: String, applyToAllScenes: Boolean = true) {
        viewModelScope.launch {
            projectRepository.updateTemplate(projectId, templateId, applyToAllScenes)
            preferencesRepository.recordRecentTemplate(templateId)
            showMessage("Template applied: $templateId")
        }
    }

    fun toggleFavoriteTemplate(templateId: String) {
        viewModelScope.launch {
            preferencesRepository.toggleFavoriteTemplate(templateId)
        }
    }

    // --- Milestone 4: Audio ---
    fun importAudioUri(projectId: String, uri: Uri) {
        viewModelScope.launch {
            val result = audioEngine.importAudioFromUri(projectId, uri)
            result.onSuccess { asset ->
                projectRepository.updateAudio(projectId, asset)
                refreshStorageSummary()
                showMessage("Audio imported: ${asset.fileName}")
            }.onFailure { err ->
                showMessage("Audio import failed: ${err.message}")
            }
        }
    }

    fun saveAudioAsset(projectId: String, audioAsset: AudioAsset?) {
        viewModelScope.launch {
            projectRepository.updateAudio(projectId, audioAsset)
            refreshStorageSummary()
        }
    }

    // --- Milestone 5: Auto Match, Scene Motion JSON & Scene Editor ---
    fun autoMatchScenes(project: Project, varyTemplates: Boolean = true) {
        val effectiveCues = if (project.subtitleCues.isNotEmpty()) {
            project.subtitleCues
        } else {
            SrtEngine.generateCuesFromScript(project.script)
        }
        if (effectiveCues.isEmpty()) {
            showMessage("Add SRT subtitles or script lines before generating Motion JSON scenes.")
            return
        }
        val matched = SrtEngine.autoMatchScenesFromCues(
            cues = effectiveCues,
            script = project.script,
            templateId = project.templateId,
            varyTemplates = varyTemplates,
        )
        viewModelScope.launch {
            if (project.subtitleCues.isEmpty()) {
                projectRepository.updateSubtitles(project.id, effectiveCues)
            }
            projectRepository.updateScenes(project.id, matched)
            showMessage("Generated ${matched.size} animated scenes from SRT + Motion JSON.")
        }
    }

    fun importSceneMotionJson(
        projectId: String,
        fallbackTemplateId: String,
        rawJson: String,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val allowedIds = templateCatalog.templates.map { it.id }.toSet()
        val parsed = com.example.feature.editor.SceneMotionJsonCodec.parseAndValidateSceneJson(
            rawJson = rawJson,
            allowedTemplateIds = allowedIds,
            fallbackTemplateId = fallbackTemplateId,
        )
        if (!parsed.isValid) {
            showMessage("Scene JSON invalid: ${parsed.errors.firstOrNull() ?: "Unknown error"}")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            projectRepository.updateScenes(projectId, parsed.scenes)
            val warnSuffix = if (parsed.warnings.isNotEmpty()) " (${parsed.warnings.size} warning)" else ""
            showMessage("Applied ${parsed.scenes.size} motion scenes from JSON$warnSuffix.")
            onComplete(true)
        }
    }

    fun updateScenes(projectId: String, scenes: List<SceneItem>) {
        viewModelScope.launch {
            projectRepository.updateScenes(projectId, scenes)
        }
    }

    fun importImageIntoProject(
        projectId: String,
        subFolder: String,
        uri: Uri,
        onResult: (Result<String>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(localAssetStorage.ensureProjectDirectory(projectId), subFolder).apply { mkdirs() }
                    val file = File(dir, "img_${UUID.randomUUID()}.png")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("Cannot read selected image.")
                    // Verify bitmap decodes cleanly (preserves PNG/WebP transparency)
                    val verified = SceneFrameRenderer.decodeBitmapSafely(file.absolutePath, 1280)
                    val bmp = verified.getOrThrow() ?: error("Selected image could not be decoded.")
                    bmp.recycle()
                    file.absolutePath
                }
            }
            refreshStorageSummary()
            onResult(result)
        }
    }

    // --- Milestone 6: Watermarks ---
    fun updateWatermarkSettings(projectId: String, settings: WatermarkSettings) {
        viewModelScope.launch {
            projectRepository.updateWatermarkSettings(projectId, settings)
        }
    }

    fun saveWatermarkAsGlobalDefault(settings: WatermarkSettings) {
        viewModelScope.launch {
            preferencesRepository.setDefaultWatermarkSettings(settings)
            showMessage("Saved as default watermark preset for new projects.")
        }
    }

    // --- Milestone 7: Real MP4 Export ---
    fun startMp4Export(project: Project, config: ExportConfig) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _exportState.value = ExportState.Running("Starting encoder...", 0f, 0, 1)
            val result = mp4VideoExporter.exportProjectToMp4(project, config) { running ->
                _exportState.value = running
            }
            _exportState.value = result
            refreshStorageSummary()
        }
    }

    fun cancelMp4Export() {
        exportJob?.cancel()
        exportJob = null
        _exportState.value = ExportState.Cancelled
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    fun saveExportToMoviesGallery(outputPath: String, projectName: String) {
        viewModelScope.launch {
            val safeName = projectName.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(32).ifBlank { "BanglaMotion" }
            val result = mp4VideoExporter.saveToDeviceMoviesGallery(outputPath, safeName)
            result.onSuccess { uri ->
                val current = _exportState.value
                if (current is ExportState.Success) {
                    _exportState.value = current.copy(galleryUri = uri)
                }
                showMessage("Saved to Movies/BanglaMotion gallery.")
            }.onFailure { err ->
                showMessage("Gallery save failed: ${err.message}")
            }
        }
    }

    // --- Milestone 8: Optional AI Proxy ---
    fun updateAiProxyConfig(config: AiProxyConfig) {
        viewModelScope.launch {
            preferencesRepository.setAiProxyConfig(config)
            showMessage("Optional AI proxy settings saved.")
        }
    }

    fun requestOptionalAiAssist(project: Project) {
        viewModelScope.launch {
            _aiState.value = AiAssistUiState.Loading
            val allowedIds = templateCatalog.templates.map { it.id }.toSet()
            val result = optionalAiClient.requestScriptAndSceneSuggestions(
                config = uiState.value.preferences.aiProxyConfig,
                script = project.script,
                language = project.language,
                allowedTemplateIds = allowedIds,
            )
            _aiState.value = result.fold(
                onSuccess = { AiAssistUiState.Success(it) },
                onFailure = { AiAssistUiState.Error(it.message ?: "AI request failed.") },
            )
        }
    }

    fun clearAiAssistState() {
        _aiState.value = AiAssistUiState.Idle
    }

    // --- Global Preferences ---
    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesRepository.setAppLanguage(language) }
    }

    fun setDefaultAspectRatio(aspectRatio: AspectRatio) {
        viewModelScope.launch { preferencesRepository.setDefaultAspectRatio(aspectRatio) }
    }

    fun setDefaultResolution(resolution: ExportResolution) {
        viewModelScope.launch { preferencesRepository.setDefaultResolution(resolution) }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopPlayback()
        audioEngine.cancelRecording()
        exportJob?.cancel()
    }

    companion object {
        fun factory(container: StudioAppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(StudioViewModel::class.java)) {
                        return StudioViewModel(
                            appContext = container.appContext,
                            projectRepository = container.projectRepository,
                            preferencesRepository = container.preferencesRepository,
                            localAssetStorage = container.localAssetStorage,
                            templateCatalog = container.templateCatalog,
                            sceneFrameRenderer = container.sceneFrameRenderer,
                            audioEngine = container.audioEngine,
                            mp4VideoExporter = container.mp4VideoExporter,
                            optionalAiClient = container.optionalAiClient,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

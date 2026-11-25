package com.example.core.storage

import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.AudioAsset
import com.example.core.model.ExportResolution
import com.example.core.model.LocalStorageSummary
import com.example.core.model.Project
import com.example.core.model.SceneItem
import com.example.core.model.SubtitleCue
import com.example.core.model.WatermarkSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface ProjectValidationError {
    data object EmptyName : ProjectValidationError
    data object NameTooLong : ProjectValidationError
}

object ProjectNameValidator {
    fun sanitize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun validate(raw: String): ProjectValidationError? {
        val cleaned = sanitize(raw)
        return when {
            cleaned.isEmpty() -> ProjectValidationError.EmptyName
            cleaned.length > Project.MAX_NAME_LENGTH -> ProjectValidationError.NameTooLong
            else -> null
        }
    }
}

interface ProjectRepository {
    fun observeAllProjects(): Flow<List<Project>>
    fun observeProjectById(projectId: String): Flow<Project?>
    suspend fun getProjectById(projectId: String): Project?
    suspend fun createProject(
        name: String,
        language: AppLanguage,
        aspectRatio: AspectRatio,
        resolution: ExportResolution,
        fps: Int = Project.DEFAULT_FPS,
        initialWatermarkSettings: WatermarkSettings = WatermarkSettings(),
    ): Result<Project>

    suspend fun renameProject(projectId: String, newName: String): Result<Unit>
    suspend fun updateProjectFormatAndLanguage(
        projectId: String,
        aspectRatio: AspectRatio,
        language: AppLanguage,
        resolution: ExportResolution,
        fps: Int = Project.DEFAULT_FPS,
    ): Result<Unit>

    suspend fun updateScript(projectId: String, script: String): Result<Unit>
    suspend fun updateSubtitles(projectId: String, cues: List<SubtitleCue>): Result<Unit>
    suspend fun updateTemplate(projectId: String, templateId: String, applyToAllScenes: Boolean = false): Result<Unit>
    suspend fun updateScenes(projectId: String, scenes: List<SceneItem>): Result<Unit>
    suspend fun updateAudio(projectId: String, audioAsset: AudioAsset?): Result<Unit>
    suspend fun updateWatermarkSettings(projectId: String, settings: WatermarkSettings): Result<Unit>
    suspend fun duplicateProject(projectId: String, copySuffix: String): Result<Project>
    suspend fun setArchived(projectId: String, archived: Boolean): Result<Unit>
    suspend fun deleteProject(projectId: String): Result<Unit>
    suspend fun readStorageSummary(activeCount: Int, archivedCount: Int): LocalStorageSummary
}

class RoomProjectRepository(
    private val projectDao: ProjectDao,
    private val localAssetStorage: LocalAssetStorage,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProjectRepository {

    override fun observeAllProjects(): Flow<List<Project>> =
        projectDao.observeAllProjects().map { list -> list.map { it.toDomain() } }

    override fun observeProjectById(projectId: String): Flow<Project?> =
        projectDao.observeProjectById(projectId).map { it?.toDomain() }

    override suspend fun getProjectById(projectId: String): Project? =
        projectDao.getProjectById(projectId)?.toDomain()

    override suspend fun createProject(
        name: String,
        language: AppLanguage,
        aspectRatio: AspectRatio,
        resolution: ExportResolution,
        fps: Int,
        initialWatermarkSettings: WatermarkSettings,
    ): Result<Project> {
        val cleanedName = ProjectNameValidator.sanitize(name)
        val validationError = ProjectNameValidator.validate(cleanedName)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError.toString()))
        }

        val now = clock()
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = cleanedName,
            language = language,
            aspectRatio = aspectRatio,
            fps = fps.coerceIn(24, 60),
            resolution = resolution,
            createdAt = now,
            updatedAt = now,
            watermarkSettings = initialWatermarkSettings,
        )
        projectDao.upsertProject(project.toEntity())
        withContext(Dispatchers.IO) {
            localAssetStorage.ensureProjectDirectory(project.id)
        }
        return Result.success(project)
    }

    override suspend fun renameProject(projectId: String, newName: String): Result<Unit> {
        val cleanedName = ProjectNameValidator.sanitize(newName)
        val validationError = ProjectNameValidator.validate(cleanedName)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError.toString()))
        }
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        projectDao.updateProjectName(existing.id, cleanedName, clock())
        return Result.success(Unit)
    }

    override suspend fun updateProjectFormatAndLanguage(
        projectId: String,
        aspectRatio: AspectRatio,
        language: AppLanguage,
        resolution: ExportResolution,
        fps: Int,
    ): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        projectDao.updateProjectFormat(
            projectId = existing.id,
            aspectRatioLabel = aspectRatio.label,
            languageCode = language.code,
            resolutionLabel = resolution.label,
            fps = fps.coerceIn(24, 60),
            updatedAt = clock(),
        )
        return Result.success(Unit)
    }

    override suspend fun updateScript(projectId: String, script: String): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        projectDao.updateScript(existing.id, script, clock())
        return Result.success(Unit)
    }

    override suspend fun updateSubtitles(projectId: String, cues: List<SubtitleCue>): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        val renumbered = cues.mapIndexed { idx, cue -> cue.copy(index = idx + 1) }
        val trackId = if (renumbered.isNotEmpty()) existing.subtitleTrackId ?: UUID.randomUUID().toString() else null
        projectDao.updateSubtitles(
            projectId = existing.id,
            cuesJson = ProjectJsonCodec.encodeSubtitleCues(renumbered),
            trackId = trackId,
            updatedAt = clock(),
        )
        return Result.success(Unit)
    }

    override suspend fun updateTemplate(
        projectId: String,
        templateId: String,
        applyToAllScenes: Boolean,
    ): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)?.toDomain()
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        val now = clock()
        projectDao.updateTemplate(existing.id, templateId, now)
        if (applyToAllScenes && existing.scenes.isNotEmpty()) {
            val updatedScenes = existing.scenes.map { it.copy(templateId = templateId) }
            projectDao.updateScenes(
                projectId = existing.id,
                scenesJson = ProjectJsonCodec.encodeScenes(updatedScenes),
                sceneIdsJson = ProjectJsonCodec.encodeStringList(updatedScenes.map { it.id }),
                updatedAt = now,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun updateScenes(projectId: String, scenes: List<SceneItem>): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        val ordered = scenes.mapIndexed { index, item -> item.copy(orderIndex = index) }
        projectDao.updateScenes(
            projectId = existing.id,
            scenesJson = ProjectJsonCodec.encodeScenes(ordered),
            sceneIdsJson = ProjectJsonCodec.encodeStringList(ordered.map { it.id }),
            updatedAt = clock(),
        )
        return Result.success(Unit)
    }

    override suspend fun updateAudio(projectId: String, audioAsset: AudioAsset?): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        projectDao.updateAudio(
            projectId = existing.id,
            audioAssetId = audioAsset?.id,
            audioAssetJson = ProjectJsonCodec.encodeAudioAsset(audioAsset),
            updatedAt = clock(),
        )
        return Result.success(Unit)
    }

    override suspend fun updateWatermarkSettings(
        projectId: String,
        settings: WatermarkSettings,
    ): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        projectDao.updateWatermarkSettings(
            projectId = existing.id,
            watermarkJson = ProjectJsonCodec.encodeWatermarkSettings(settings),
            updatedAt = clock(),
        )
        return Result.success(Unit)
    }

    override suspend fun duplicateProject(projectId: String, copySuffix: String): Result<Project> {
        val existing = projectDao.getProjectById(projectId)?.toDomain()
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))

        val baseMaxLength = (Project.MAX_NAME_LENGTH - copySuffix.length).coerceAtLeast(1)
        val trimmedBase = existing.name.take(baseMaxLength)
        val now = clock()
        val copy = existing.copy(
            id = UUID.randomUUID().toString(),
            name = trimmedBase + copySuffix,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        projectDao.upsertProject(copy.toEntity())
        withContext(Dispatchers.IO) {
            localAssetStorage.ensureProjectDirectory(copy.id)
        }
        return Result.success(copy)
    }

    override suspend fun setArchived(projectId: String, archived: Boolean): Result<Unit> {
        val existing = projectDao.getProjectById(projectId)
            ?: return Result.failure(NoSuchElementException("Project not found: $projectId"))
        projectDao.setArchived(existing.id, archived, clock())
        return Result.success(Unit)
    }

    override suspend fun deleteProject(projectId: String): Result<Unit> {
        projectDao.deleteProjectById(projectId)
        withContext(Dispatchers.IO) {
            localAssetStorage.deleteProjectDirectory(projectId)
        }
        return Result.success(Unit)
    }

    override suspend fun readStorageSummary(
        activeCount: Int,
        archivedCount: Int,
    ): LocalStorageSummary = withContext(Dispatchers.IO) {
        LocalStorageSummary(
            activeProjectCount = activeCount,
            archivedProjectCount = archivedCount,
            storagePath = localAssetStorage.getRootPath(),
            usedBytes = localAssetStorage.calculateUsedBytes(),
            availableBytes = localAssetStorage.calculateAvailableBytes(),
        )
    }
}

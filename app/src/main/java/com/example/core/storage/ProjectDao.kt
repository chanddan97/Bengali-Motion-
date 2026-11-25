package com.example.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    fun observeProjectById(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateProjectName(projectId: String, name: String, updatedAt: Long)

    @Query(
        "UPDATE projects SET aspectRatioLabel = :aspectRatioLabel, languageCode = :languageCode, " +
            "resolutionLabel = :resolutionLabel, fps = :fps, updatedAt = :updatedAt WHERE id = :projectId"
    )
    suspend fun updateProjectFormat(
        projectId: String,
        aspectRatioLabel: String,
        languageCode: String,
        resolutionLabel: String,
        fps: Int,
        updatedAt: Long,
    )

    @Query("UPDATE projects SET script = :script, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateScript(projectId: String, script: String, updatedAt: Long)

    @Query("UPDATE projects SET subtitleCuesJson = :cuesJson, subtitleTrackId = :trackId, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateSubtitles(projectId: String, cuesJson: String, trackId: String?, updatedAt: Long)

    @Query("UPDATE projects SET templateId = :templateId, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateTemplate(projectId: String, templateId: String, updatedAt: Long)

    @Query("UPDATE projects SET scenesJson = :scenesJson, sceneIdsJson = :sceneIdsJson, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateScenes(projectId: String, scenesJson: String, sceneIdsJson: String, updatedAt: Long)

    @Query("UPDATE projects SET audioAssetId = :audioAssetId, audioAssetJson = :audioAssetJson, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateAudio(projectId: String, audioAssetId: String?, audioAssetJson: String, updatedAt: Long)

    @Query("UPDATE projects SET watermarkSettingsJson = :watermarkJson, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateWatermarkSettings(projectId: String, watermarkJson: String, updatedAt: Long)

    @Query("UPDATE projects SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun setArchived(projectId: String, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)
}

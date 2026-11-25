package com.example.core.di

import android.content.Context
import com.example.ai.provider.OptionalAiClient
import com.example.core.render.SceneFrameRenderer
import com.example.core.storage.AppPreferencesRepository
import com.example.core.storage.BanglaMotionDatabase
import com.example.core.storage.DataStoreAppPreferencesRepository
import com.example.core.storage.LocalAssetStorage
import com.example.core.storage.ProjectRepository
import com.example.core.storage.RoomProjectRepository
import com.example.data.templates.TemplateCatalogLoader
import com.example.data.templates.TemplateCatalogResult
import com.example.feature.audio.StudioAudioEngine
import com.example.feature.export.Mp4VideoExporter

class StudioAppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: BanglaMotionDatabase by lazy {
        BanglaMotionDatabase.getInstance(appContext)
    }

    val localAssetStorage: LocalAssetStorage by lazy {
        LocalAssetStorage(appContext)
    }

    val projectRepository: ProjectRepository by lazy {
        RoomProjectRepository(
            projectDao = database.projectDao(),
            localAssetStorage = localAssetStorage,
        )
    }

    val preferencesRepository: AppPreferencesRepository by lazy {
        DataStoreAppPreferencesRepository(appContext)
    }

    val templateCatalog: TemplateCatalogResult by lazy {
        TemplateCatalogLoader.loadFromAssets(appContext)
    }

    val sceneFrameRenderer: SceneFrameRenderer by lazy {
        SceneFrameRenderer(appContext)
    }

    val audioEngine: StudioAudioEngine by lazy {
        StudioAudioEngine(appContext, localAssetStorage)
    }

    val mp4VideoExporter: Mp4VideoExporter by lazy {
        Mp4VideoExporter(appContext, sceneFrameRenderer, localAssetStorage)
    }

    val optionalAiClient: OptionalAiClient by lazy {
        OptionalAiClient()
    }
}

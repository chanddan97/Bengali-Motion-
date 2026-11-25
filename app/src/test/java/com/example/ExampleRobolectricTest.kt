package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.ExportResolution
import com.example.core.model.WatermarkSettings
import com.example.core.render.RenderFrameInput
import com.example.core.render.SceneFrameRenderer
import com.example.core.storage.BanglaMotionDatabase
import com.example.core.storage.DataStoreAppPreferencesRepository
import com.example.core.storage.LocalAssetStorage
import com.example.core.storage.RoomProjectRepository
import com.example.data.templates.TemplateCatalogLoader
import com.example.data.templates.TemplateOrigin
import com.example.feature.subtitles.SrtEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: BanglaMotionDatabase
    private lateinit var assetStorage: LocalAssetStorage
    private lateinit var repository: RoomProjectRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BanglaMotionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        assetStorage = LocalAssetStorage(context)
        repository = RoomProjectRepository(
            projectDao = database.projectDao(),
            localAssetStorage = assetStorage,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun appName_isBanglaMotion() {
        assertEquals("BanglaMotion", context.getString(R.string.app_name))
    }

    @Test
    fun templateCatalogAsset_loadsAll20ValidTemplatesWithExactOriginSeparation() {
        val catalog = TemplateCatalogLoader.loadFromAssets(context)
        assertTrue("Catalog validation errors: ${catalog.validationErrors}", catalog.isValid)
        assertEquals("1.0.0", catalog.schemaVersion)
        assertEquals(20, catalog.templates.size)
        assertEquals(10, catalog.observedCount)
        assertEquals(10, catalog.proposedCount)

        val expectedObservedIds = setOf(
            "minimal-text", "saas-product", "shattered-glass", "detective-board",
            "folded-photo-story", "interview-news", "cosmos-story", "mint-glass",
            "documentary", "qa-answer",
        )
        val actualObservedIds = catalog.templates
            .filter { it.origin == TemplateOrigin.OBSERVED_LABEL_ORIGINAL_DESIGN }
            .map { it.id }
            .toSet()
        assertEquals(expectedObservedIds, actualObservedIds)

        val expectedProposedIds = setOf(
            "paper-cut", "comic-pop", "kinetic-type", "retro-terminal", "soft-gradient",
            "blueprint", "magazine-editorial", "3d-object-stage", "split-screen", "chalkboard",
        )
        val actualProposedIds = catalog.templates
            .filter { it.origin == TemplateOrigin.PROPOSED_ORIGINAL }
            .map { it.id }
            .toSet()
        assertEquals(expectedProposedIds, actualProposedIds)
    }

    @Test
    fun templateCatalogValidator_rejectsUnsafeAdditionalProperties() {
        val unsafeJson = """{"schemaVersion":"1.0.0","catalogVersion":"1.0.0","evalScript":"alert(1)","templates":[]}"""
        val result = TemplateCatalogLoader.parseAndValidate(unsafeJson)
        assertFalse(result.isValid)
        assertTrue(result.validationErrors.any { it.contains("Disallowed root property") })
    }

    @Test
    fun sceneFrameRenderer_rendersAll20TemplatesAndWatermarksCleanly() {
        val catalog = TemplateCatalogLoader.loadFromAssets(context)
        val renderer = SceneFrameRenderer(context)

        catalog.templates.forEach { template ->
            val bitmap = renderer.renderTemplateSampleBitmap(
                templateId = template.id,
                sampleHeadline = "বাংলা শিরোনাম",
                sampleCaption = template.name,
                width = 240,
                height = 160,
            )
            assertEquals(240, bitmap.width)
            assertEquals(160, bitmap.height)
            bitmap.recycle()
        }
    }

    @Test
    fun fullProjectWorkflow_persistsScriptSrtScenesTemplateAndWatermarksInRoom() = runTest {
        val created = repository.createProject(
            name = "সকালের বাংলা গল্প",
            language = AppLanguage.BENGALI,
            aspectRatio = AspectRatio.PORTRAIT_9_16,
            resolution = ExportResolution.FHD_1080P,
        ).getOrThrow()

        // Default watermarks must be OFF
        assertFalse(created.watermarkSettings.user.enabled)
        assertFalse(created.watermarkSettings.developer.enabled)

        // Script -> SRT -> Auto Match Scenes -> Template -> Watermark
        val bengaliScript = "আজকের গল্পে স্বাগতম।\nচলো শুরু করা যাক।"
        repository.updateScript(created.id, bengaliScript)
        val cues = SrtEngine.generateCuesFromScript(bengaliScript)
        repository.updateSubtitles(created.id, cues)
        repository.updateTemplate(created.id, "detective-board")
        val scenes = SrtEngine.autoMatchScenesFromCues(cues, bengaliScript, "detective-board")
        repository.updateScenes(created.id, scenes)

        val customWm = WatermarkSettings(
            user = WatermarkSettings.defaultUserLayer().copy(enabled = true, text = "@MyBanglaChannel"),
            developer = WatermarkSettings.defaultDeveloperLayer().copy(enabled = false),
        )
        repository.updateWatermarkSettings(created.id, customWm)

        val loaded = repository.observeProjectById(created.id).first()
        assertNotNull(loaded)
        assertEquals(bengaliScript, loaded?.script)
        assertEquals(2, loaded?.subtitleCues?.size)
        assertEquals("detective-board", loaded?.templateId)
        assertEquals(2, loaded?.scenes?.size)
        assertTrue(loaded?.watermarkSettings?.user?.enabled == true)
        assertFalse(loaded?.watermarkSettings?.developer?.enabled == true)

        // Render frame with shared renderer
        val renderer = SceneFrameRenderer(context)
        val bmp = renderer.renderToBitmap(
            RenderFrameInput(
                project = loaded!!,
                timeMs = 500L,
                width = 360,
                height = 640,
            ),
        )
        assertEquals(360, bmp.width)
        assertEquals(640, bmp.height)
        bmp.recycle()

        // Cleanup
        val dir = File(assetStorage.getRootPath(), created.id)
        assertTrue(dir.exists())
        repository.deleteProject(created.id)
        assertNull(repository.observeProjectById(created.id).first())
        assertFalse(dir.exists())
    }

    @Test
    fun preferencesRepository_persistsLanguageFavoritesAndWatermarkDefaults() = runTest {
        val tempFile = File.createTempFile("studio_prefs_full_test", ".preferences_pb", context.cacheDir)
        tempFile.deleteOnExit()
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFile },
        )
        val prefsRepo = DataStoreAppPreferencesRepository(testDataStore)

        prefsRepo.setAppLanguage(AppLanguage.BENGALI)
        prefsRepo.toggleFavoriteTemplate("cosmos-story")
        prefsRepo.recordRecentTemplate("mint-glass")
        prefsRepo.setDefaultWatermarkSettings(
            WatermarkSettings(
                user = WatermarkSettings.defaultUserLayer().copy(enabled = true, text = "MyStudio"),
                developer = WatermarkSettings.defaultDeveloperLayer().copy(enabled = false),
            ),
        )

        val saved = prefsRepo.preferencesFlow.first()
        assertEquals(AppLanguage.BENGALI, saved.appLanguage)
        assertTrue("cosmos-story" in saved.favoriteTemplateIds)
        assertEquals(listOf("mint-glass"), saved.recentTemplateIds)
        assertTrue(saved.defaultWatermarkSettings.user.enabled)
        assertFalse(saved.defaultWatermarkSettings.developer.enabled)
    }

    @Test
    fun optionalAiClient_validatesSchemaAndRejectsUnknownTemplateIds() {
        val client = com.example.ai.provider.OptionalAiClient()
        val allowed = setOf("minimal-text", "cosmos-story")
        val raw = """
            {
              "hookTitle": "বাংলা হুক",
              "scenes": [
                {"headline": "প্রথম সিন", "caption": "বিবরণ", "templateId": "cosmos-story"},
                {"headline": "দ্বিতীয় সিন", "caption": "অন্য", "templateId": "malicious-eval-id"}
              ]
            }
        """.trimIndent()
        val parsed = client.parseAndValidateResponse(raw, allowed)
        assertEquals("বাংলা হুক", parsed.hookTitle)
        assertEquals(2, parsed.sceneDrafts.size)
        assertEquals("cosmos-story", parsed.sceneDrafts[0].suggestedTemplateId)
        assertEquals("minimal-text", parsed.sceneDrafts[1].suggestedTemplateId)
    }
}

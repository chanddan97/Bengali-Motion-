package com.example.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.AiProxyConfig
import com.example.core.model.AppLanguage
import com.example.core.model.AspectRatio
import com.example.core.model.ExportResolution
import com.example.core.model.WatermarkSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "banglamotion_studio_prefs",
)

data class StudioPreferences(
    val appLanguage: AppLanguage = AppLanguage.BENGALI_ENGLISH,
    val defaultAspectRatio: AspectRatio = AspectRatio.PORTRAIT_9_16,
    val defaultResolution: ExportResolution = ExportResolution.FHD_1080P,
    val favoriteTemplateIds: Set<String> = emptySet(),
    val recentTemplateIds: List<String> = emptyList(),
    val defaultWatermarkSettings: WatermarkSettings = WatermarkSettings(),
    val aiProxyConfig: AiProxyConfig = AiProxyConfig(),
)

interface AppPreferencesRepository {
    val preferencesFlow: Flow<StudioPreferences>
    suspend fun setAppLanguage(language: AppLanguage)
    suspend fun setDefaultAspectRatio(aspectRatio: AspectRatio)
    suspend fun setDefaultResolution(resolution: ExportResolution)
    suspend fun toggleFavoriteTemplate(templateId: String)
    suspend fun recordRecentTemplate(templateId: String)
    suspend fun setDefaultWatermarkSettings(settings: WatermarkSettings)
    suspend fun setAiProxyConfig(config: AiProxyConfig)
}

class DataStoreAppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : AppPreferencesRepository {

    constructor(context: Context) : this(context.applicationContext.studioPreferencesDataStore)

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val DEFAULT_RESOLUTION = stringPreferencesKey("default_resolution")
        val FAVORITE_TEMPLATES = stringSetPreferencesKey("favorite_templates")
        val RECENT_TEMPLATES = stringPreferencesKey("recent_templates")
        val DEFAULT_WATERMARK_JSON = stringPreferencesKey("default_watermark_json")
        val AI_PROXY_URL = stringPreferencesKey("ai_proxy_url")
        val AI_CONSENT_GRANTED = booleanPreferencesKey("ai_consent_granted")
    }

    override val preferencesFlow: Flow<StudioPreferences> = dataStore.data.map { prefs ->
        val recentRaw = prefs[Keys.RECENT_TEMPLATES].orEmpty()
        val recentList = if (recentRaw.isBlank()) emptyList() else recentRaw.split(",").filter { it.isNotBlank() }
        StudioPreferences(
            appLanguage = AppLanguage.fromCode(prefs[Keys.APP_LANGUAGE]),
            defaultAspectRatio = AspectRatio.fromLabel(prefs[Keys.DEFAULT_ASPECT_RATIO]),
            defaultResolution = ExportResolution.fromLabel(prefs[Keys.DEFAULT_RESOLUTION]),
            favoriteTemplateIds = prefs[Keys.FAVORITE_TEMPLATES].orEmpty(),
            recentTemplateIds = recentList,
            defaultWatermarkSettings = ProjectJsonCodec.decodeWatermarkSettings(prefs[Keys.DEFAULT_WATERMARK_JSON]),
            aiProxyConfig = AiProxyConfig(
                proxyBaseUrl = prefs[Keys.AI_PROXY_URL].orEmpty(),
                consentGranted = prefs[Keys.AI_CONSENT_GRANTED] ?: false,
            ),
        )
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = language.code }
    }

    override suspend fun setDefaultAspectRatio(aspectRatio: AspectRatio) {
        dataStore.edit { prefs -> prefs[Keys.DEFAULT_ASPECT_RATIO] = aspectRatio.label }
    }

    override suspend fun setDefaultResolution(resolution: ExportResolution) {
        dataStore.edit { prefs -> prefs[Keys.DEFAULT_RESOLUTION] = resolution.label }
    }

    override suspend fun toggleFavoriteTemplate(templateId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITE_TEMPLATES].orEmpty().toMutableSet()
            if (!current.add(templateId)) current.remove(templateId)
            prefs[Keys.FAVORITE_TEMPLATES] = current
        }
    }

    override suspend fun recordRecentTemplate(templateId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_TEMPLATES].orEmpty()
                .split(",")
                .filter { it.isNotBlank() && it != templateId }
            val updated = (listOf(templateId) + current).take(6)
            prefs[Keys.RECENT_TEMPLATES] = updated.joinToString(",")
        }
    }

    override suspend fun setDefaultWatermarkSettings(settings: WatermarkSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_WATERMARK_JSON] = ProjectJsonCodec.encodeWatermarkSettings(settings)
        }
    }

    override suspend fun setAiProxyConfig(config: AiProxyConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.AI_PROXY_URL] = config.proxyBaseUrl.trim()
            prefs[Keys.AI_CONSENT_GRANTED] = config.consentGranted
        }
    }
}

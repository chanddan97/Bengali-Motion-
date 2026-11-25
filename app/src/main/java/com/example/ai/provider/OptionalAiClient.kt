package com.example.ai.provider

import com.example.core.model.AiProxyConfig
import com.example.core.model.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiSceneDraft(
    val headline: String,
    val caption: String,
    val suggestedTemplateId: String,
)

data class AiAssistResult(
    val rewrittenScript: String?,
    val hookTitle: String?,
    val sceneDrafts: List<AiSceneDraft>,
)

/**
 * Optional, isolated AI client that NEVER embeds any API key in the APK.
 * Calls only a user-configured HTTPS backend proxy when explicit user consent is granted,
 * and strictly validates the returned JSON schema before allowing it to touch project state.
 */
class OptionalAiClient {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_RETRIES = 2
    }

    suspend fun requestScriptAndSceneSuggestions(
        config: AiProxyConfig,
        script: String,
        language: AppLanguage,
        allowedTemplateIds: Set<String>,
    ): Result<AiAssistResult> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(
                IllegalStateException("AI backend proxy is not configured. Core studio features remain 100% available offline."),
            )
        }
        if (!config.consentGranted) {
            return@withContext Result.failure(
                IllegalStateException("Explicit user consent is required before sending script text over the network."),
            )
        }
        if (script.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a script before requesting AI suggestions."))
        }

        val endpoint = config.proxyBaseUrl.trim().trimEnd('/') + "/v1/banglamotion/suggest"
        val payload = JSONObject().apply {
            put("language", language.code)
            put("script", script.take(4_000))
            put("allowedTemplateIds", JSONArray(allowedTemplateIds.toList()))
            put("responseFormat", "strict_json")
        }.toString()

        var lastError: Throwable? = null
        repeat(MAX_RETRIES) {
            val attempt = runCatching {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload)
                    }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) {
                        error("AI proxy returned HTTP $status: ${body.take(180)}")
                    }
                    parseAndValidateResponse(body, allowedTemplateIds)
                } finally {
                    connection.disconnect()
                }
            }
            if (attempt.isSuccess) return@withContext attempt
            lastError = attempt.exceptionOrNull()
        }
        Result.failure(lastError ?: IllegalStateException("AI proxy request failed."))
    }

    fun parseAndValidateResponse(
        rawJson: String,
        allowedTemplateIds: Set<String>,
    ): AiAssistResult {
        val root = JSONObject(rawJson)
        val rewritten = root.optString("rewrittenScript", "").trim().ifBlank { null }
        val hookTitle = root.optString("hookTitle", "").trim().ifBlank { null }
        val scenesArray = root.optJSONArray("scenes")
        val drafts = buildList {
            if (scenesArray != null) {
                for (i in 0 until scenesArray.length().coerceAtMost(30)) {
                    val obj = scenesArray.optJSONObject(i) ?: continue
                    val headline = obj.optString("headline", "").trim()
                    if (headline.isBlank()) continue
                    val caption = obj.optString("caption", "").trim()
                    val candidateTemplate = obj.optString("templateId", "minimal-text").trim()
                    val safeTemplate = if (candidateTemplate in allowedTemplateIds) candidateTemplate else "minimal-text"
                    add(
                        AiSceneDraft(
                            headline = headline.take(160),
                            caption = caption.take(220),
                            suggestedTemplateId = safeTemplate,
                        ),
                    )
                }
            }
        }
        require(rewritten != null || hookTitle != null || drafts.isNotEmpty()) {
            "AI response did not match required JSON schema (missing rewrittenScript, hookTitle, or scenes)."
        }
        return AiAssistResult(rewritten, hookTitle, drafts)
    }
}

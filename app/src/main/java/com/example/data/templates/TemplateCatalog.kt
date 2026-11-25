package com.example.data.templates

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

enum class TemplateOrigin(val jsonValue: String, val badgeBn: String, val badgeEn: String) {
    OBSERVED_LABEL_ORIGINAL_DESIGN(
        jsonValue = "observed-label-original-design",
        badgeBn = "পর্যবেক্ষিত লেবেল · মৌলিক ডিজাইন",
        badgeEn = "Observed Label · Original Design",
    ),
    PROPOSED_ORIGINAL(
        jsonValue = "proposed-original",
        badgeBn = "প্রস্তাবিত মৌলিক ডিজাইন",
        badgeEn = "Proposed Original",
    );

    companion object {
        fun fromJson(value: String): TemplateOrigin? =
            entries.firstOrNull { it.jsonValue == value }
    }
}

data class TemplateLayerSpec(
    val id: String,
    val type: String,
    val role: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val opacity: Float,
    val fontFamily: String = "Noto Sans Bengali",
    val fontWeight: String = "bold",
    val maxLines: Int = 3,
    val alignment: String = "center",
)

data class TemplateMotionSpec(
    val type: String,
    val durationMs: Int,
    val delayMs: Int,
)

data class TemplateAccessibilitySpec(
    val minimumContrastNote: String,
    val bengaliTextSafe: Boolean,
    val reducedMotionSupported: Boolean,
)

data class TemplateDefinition(
    val id: String,
    val name: String,
    val origin: TemplateOrigin,
    val categories: List<String>,
    val description: String,
    val aspectRatios: List<String>,
    val layers: List<TemplateLayerSpec>,
    val motion: List<TemplateMotionSpec>,
    val accessibility: TemplateAccessibilitySpec,
    val capabilityNote: String? = null,
)

data class TemplateCatalogResult(
    val schemaVersion: String,
    val catalogVersion: String,
    val templates: List<TemplateDefinition>,
    val validationErrors: List<String>,
) {
    val isValid: Boolean get() = validationErrors.isEmpty() && templates.isNotEmpty()
    val observedCount: Int get() = templates.count { it.origin == TemplateOrigin.OBSERVED_LABEL_ORIGINAL_DESIGN }
    val proposedCount: Int get() = templates.count { it.origin == TemplateOrigin.PROPOSED_ORIGINAL }
}

data class TemplateVisualStyle(
    val bgStartColor: Int,
    val bgEndColor: Int,
    val panelColor: Int,
    val accentColor: Int,
    val secondaryAccentColor: Int,
    val headlineColor: Int,
    val captionColor: Int,
    val motif: String,
    val capabilityNote: String? = null,
)

object TemplateCatalogLoader {
    private val ID_REGEX = Regex("^[a-z0-9-]+$")
    private val ALLOWED_CATEGORIES = setOf("text", "3d", "news", "product", "brand", "story", "education", "editorial", "utility")
    private val ALLOWED_ASPECT_RATIOS = setOf("9:16", "1:1", "4:5", "16:9")
    private val ALLOWED_LAYER_TYPES = setOf("text", "shape", "image", "video", "line", "badge")
    private val ALLOWED_MOTION_TYPES = setOf(
        "fade", "slide-up", "slide-down", "slide-left", "slide-right",
        "scale-in", "reveal", "draw-line", "parallax", "word-stagger", "none",
    )
    private val ALLOWED_ROOT_KEYS = setOf("schemaVersion", "catalogVersion", "templates")
    private val ALLOWED_TEMPLATE_KEYS = setOf(
        "id", "name", "origin", "category", "description",
        "aspectRatios", "layers", "motion", "accessibility",
    )

    fun loadFromAssets(context: Context): TemplateCatalogResult {
        val raw = runCatching {
            context.assets.open("template-catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { err ->
            return TemplateCatalogResult("", "", emptyList(), listOf("Failed to read template-catalog.json: ${err.message}"))
        }
        return parseAndValidate(raw)
    }

    fun parseAndValidate(rawJson: String): TemplateCatalogResult {
        val errors = mutableListOf<String>()
        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            return TemplateCatalogResult("", "", emptyList(), listOf("Invalid JSON syntax: ${err.message}"))
        }

        // Strict additionalProperties: false at root
        root.keys().forEach { key ->
            if (key !in ALLOWED_ROOT_KEYS) {
                errors += "Disallowed root property: '$key'"
            }
        }

        val schemaVersion = root.optString("schemaVersion", "")
        val catalogVersion = root.optString("catalogVersion", "")
        if (schemaVersion.isBlank()) errors += "Missing required 'schemaVersion'"
        if (catalogVersion.isBlank()) errors += "Missing required 'catalogVersion'"

        val templatesArray = root.optJSONArray("templates")
        if (templatesArray == null || templatesArray.length() == 0) {
            errors += "Missing or empty 'templates' array"
            return TemplateCatalogResult(schemaVersion, catalogVersion, emptyList(), errors)
        }

        val seenIds = mutableSetOf<String>()
        val templates = mutableListOf<TemplateDefinition>()

        for (i in 0 until templatesArray.length()) {
            val obj = templatesArray.optJSONObject(i)
            if (obj == null) {
                errors += "Template at index $i is not a JSON object"
                continue
            }
            obj.keys().forEach { key ->
                if (key !in ALLOWED_TEMPLATE_KEYS) {
                    errors += "Template[$i] contains disallowed property '$key'"
                }
            }

            val id = obj.optString("id", "")
            if (!ID_REGEX.matches(id)) {
                errors += "Template[$i] has invalid id '$id'"
                continue
            }
            if (!seenIds.add(id)) {
                errors += "Duplicate template id '$id'"
                continue
            }

            val name = obj.optString("name", "")
            if (name.isBlank()) {
                errors += "Template '$id' has empty name"
                continue
            }

            val origin = TemplateOrigin.fromJson(obj.optString("origin", ""))
            if (origin == null) {
                errors += "Template '$id' has invalid origin '${obj.optString("origin")}'"
                continue
            }

            val catArray = obj.optJSONArray("category")
            val categories = buildList {
                if (catArray != null) {
                    for (c in 0 until catArray.length()) {
                        val value = catArray.optString(c)
                        if (value in ALLOWED_CATEGORIES) add(value)
                        else errors += "Template '$id' has invalid category '$value'"
                    }
                }
            }
            if (categories.isEmpty()) errors += "Template '$id' must have at least one valid category"

            val arArray = obj.optJSONArray("aspectRatios")
            val aspectRatios = buildList {
                if (arArray != null) {
                    for (a in 0 until arArray.length()) {
                        val value = arArray.optString(a)
                        if (value in ALLOWED_ASPECT_RATIOS) add(value)
                        else errors += "Template '$id' has invalid aspectRatio '$value'"
                    }
                }
            }
            if (aspectRatios.isEmpty()) errors += "Template '$id' must have at least one valid aspectRatio"

            val layersArray = obj.optJSONArray("layers")
            val layers = buildList {
                if (layersArray != null) {
                    for (l in 0 until layersArray.length()) {
                        val layerObj = layersArray.optJSONObject(l) ?: continue
                        val layerType = layerObj.optString("type", "")
                        if (layerType !in ALLOWED_LAYER_TYPES) {
                            errors += "Template '$id' layer[$l] has disallowed type '$layerType'"
                            continue
                        }
                        val styleObj = layerObj.optJSONObject("textStyle")
                        add(
                            TemplateLayerSpec(
                                id = layerObj.optString("id", "layer-$l"),
                                type = layerType,
                                role = layerObj.optString("role", "content"),
                                x = layerObj.optDouble("x", 0.0).toFloat().coerceIn(0f, 1f),
                                y = layerObj.optDouble("y", 0.0).toFloat().coerceIn(0f, 1f),
                                width = layerObj.optDouble("width", 1.0).toFloat().coerceIn(0.01f, 1f),
                                height = layerObj.optDouble("height", 1.0).toFloat().coerceIn(0.01f, 1f),
                                opacity = layerObj.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                                fontFamily = styleObj?.optString("fontFamily", "Noto Sans Bengali") ?: "Noto Sans Bengali",
                                fontWeight = styleObj?.optString("fontWeight", "bold") ?: "bold",
                                maxLines = styleObj?.optInt("maxLines", 3)?.coerceIn(1, 8) ?: 3,
                                alignment = styleObj?.optString("alignment", "center") ?: "center",
                            ),
                        )
                    }
                }
            }

            val motionArray = obj.optJSONArray("motion")
            val motions = buildList {
                if (motionArray != null) {
                    for (m in 0 until motionArray.length()) {
                        val motionObj = motionArray.optJSONObject(m) ?: continue
                        val motionType = motionObj.optString("type", "")
                        if (motionType !in ALLOWED_MOTION_TYPES) {
                            errors += "Template '$id' motion[$m] has disallowed type '$motionType'"
                            continue
                        }
                        add(
                            TemplateMotionSpec(
                                type = motionType,
                                durationMs = motionObj.optInt("durationMs", 350).coerceIn(0, 10_000),
                                delayMs = motionObj.optInt("delayMs", 0).coerceIn(0, 30_000),
                            ),
                        )
                    }
                }
            }

            val accObj = obj.optJSONObject("accessibility")
            if (accObj == null) {
                errors += "Template '$id' missing accessibility metadata"
                continue
            }
            val accessibility = TemplateAccessibilitySpec(
                minimumContrastNote = accObj.optString("minimumContrastNote", ""),
                bengaliTextSafe = accObj.optBoolean("bengaliTextSafe", true),
                reducedMotionSupported = accObj.optBoolean("reducedMotionSupported", true),
            )

            templates += TemplateDefinition(
                id = id,
                name = name,
                origin = origin,
                categories = categories,
                description = obj.optString("description", ""),
                aspectRatios = aspectRatios,
                layers = layers,
                motion = motions,
                accessibility = accessibility,
                capabilityNote = visualStyleFor(id).capabilityNote,
            )
        }

        return TemplateCatalogResult(
            schemaVersion = schemaVersion,
            catalogVersion = catalogVersion,
            templates = templates,
            validationErrors = errors,
        )
    }

    fun visualStyleFor(templateId: String): TemplateVisualStyle = when (templateId) {
        // 10 Reference-Observed Labels (Original Proposed Designs)
        "minimal-text" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#0D1321"),
            bgEndColor = Color.parseColor("#182238"),
            panelColor = Color.parseColor("#1D2A44"),
            accentColor = Color.parseColor("#FFB703"),
            secondaryAccentColor = Color.parseColor("#64748B"),
            headlineColor = Color.parseColor("#F8FAFC"),
            captionColor = Color.parseColor("#CBD5E1"),
            motif = "minimal-text",
        )
        "saas-product" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#0B162C"),
            bgEndColor = Color.parseColor("#12264A"),
            panelColor = Color.parseColor("#1B335E"),
            accentColor = Color.parseColor("#38BDF8"),
            secondaryAccentColor = Color.parseColor("#818CF8"),
            headlineColor = Color.parseColor("#F8FAFC"),
            captionColor = Color.parseColor("#BAE6FD"),
            motif = "saas-product",
        )
        "shattered-glass" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#121024"),
            bgEndColor = Color.parseColor("#231942"),
            panelColor = Color.parseColor("#CC1B1633"),
            accentColor = Color.parseColor("#F472B6"),
            secondaryAccentColor = Color.parseColor("#60A5FA"),
            headlineColor = Color.parseColor("#FFFFFF"),
            captionColor = Color.parseColor("#E2E8F0"),
            motif = "shattered-glass",
        )
        "detective-board" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#2C1E16"),
            bgEndColor = Color.parseColor("#1F1510"),
            panelColor = Color.parseColor("#F5E6CA"),
            accentColor = Color.parseColor("#DC2626"),
            secondaryAccentColor = Color.parseColor("#D97706"),
            headlineColor = Color.parseColor("#1C1917"),
            captionColor = Color.parseColor("#44403C"),
            motif = "detective-board",
        )
        "folded-photo-story" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#1C1F2B"),
            bgEndColor = Color.parseColor("#2A2F42"),
            panelColor = Color.parseColor("#F8F4EC"),
            accentColor = Color.parseColor("#FB923C"),
            secondaryAccentColor = Color.parseColor("#94A3B8"),
            headlineColor = Color.parseColor("#1E293B"),
            captionColor = Color.parseColor("#475569"),
            motif = "folded-photo-story",
        )
        "interview-news" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#0B132B"),
            bgEndColor = Color.parseColor("#1C2541"),
            panelColor = Color.parseColor("#D90429"),
            accentColor = Color.parseColor("#FFB703"),
            secondaryAccentColor = Color.parseColor("#EDF2F4"),
            headlineColor = Color.parseColor("#FFFFFF"),
            captionColor = Color.parseColor("#F1F5F9"),
            motif = "interview-news",
        )
        "cosmos-story" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#050816"),
            bgEndColor = Color.parseColor("#1A103D"),
            panelColor = Color.parseColor("#B3111738"),
            accentColor = Color.parseColor("#C084FC"),
            secondaryAccentColor = Color.parseColor("#38BDF8"),
            headlineColor = Color.parseColor("#F8FAFC"),
            captionColor = Color.parseColor("#DDD6FE"),
            motif = "cosmos-story",
        )
        "mint-glass" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#064E3B"),
            bgEndColor = Color.parseColor("#0F766E"),
            panelColor = Color.parseColor("#CCECFDF5"),
            accentColor = Color.parseColor("#10B981"),
            secondaryAccentColor = Color.parseColor("#5EEAD4"),
            headlineColor = Color.parseColor("#064E3B"),
            captionColor = Color.parseColor("#115E59"),
            motif = "mint-glass",
        )
        "documentary" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#121212"),
            bgEndColor = Color.parseColor("#24211D"),
            panelColor = Color.parseColor("#99000000"),
            accentColor = Color.parseColor("#EAB308"),
            secondaryAccentColor = Color.parseColor("#A8A29E"),
            headlineColor = Color.parseColor("#FAFAF9"),
            captionColor = Color.parseColor("#D6D3D1"),
            motif = "documentary",
        )
        "qa-answer" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#10172A"),
            bgEndColor = Color.parseColor("#1E293B"),
            panelColor = Color.parseColor("#2563EB"),
            accentColor = Color.parseColor("#FACC15"),
            secondaryAccentColor = Color.parseColor("#22C55E"),
            headlineColor = Color.parseColor("#FFFFFF"),
            captionColor = Color.parseColor("#E2E8F0"),
            motif = "qa-answer",
        )

        // 10 Proposed Original Additions
        "paper-cut" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#FEF3C7"),
            bgEndColor = Color.parseColor("#FDE68A"),
            panelColor = Color.parseColor("#FFFBEB"),
            accentColor = Color.parseColor("#EA580C"),
            secondaryAccentColor = Color.parseColor("#0D9488"),
            headlineColor = Color.parseColor("#1C1917"),
            captionColor = Color.parseColor("#57534E"),
            motif = "paper-cut",
        )
        "comic-pop" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#FACC15"),
            bgEndColor = Color.parseColor("#F97316"),
            panelColor = Color.parseColor("#FFFFFF"),
            accentColor = Color.parseColor("#EF4444"),
            secondaryAccentColor = Color.parseColor("#2563EB"),
            headlineColor = Color.parseColor("#0F172A"),
            captionColor = Color.parseColor("#334155"),
            motif = "comic-pop",
        )
        "kinetic-type" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#09090B"),
            bgEndColor = Color.parseColor("#18181B"),
            panelColor = Color.parseColor("#27272A"),
            accentColor = Color.parseColor("#F59E0B"),
            secondaryAccentColor = Color.parseColor("#EC4899"),
            headlineColor = Color.parseColor("#FFFFFF"),
            captionColor = Color.parseColor("#FDE68A"),
            motif = "kinetic-type",
        )
        "retro-terminal" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#04120A"),
            bgEndColor = Color.parseColor("#071F12"),
            panelColor = Color.parseColor("#0B2A19"),
            accentColor = Color.parseColor("#22C55E"),
            secondaryAccentColor = Color.parseColor("#86EFAC"),
            headlineColor = Color.parseColor("#4ADE80"),
            captionColor = Color.parseColor("#BBF7D0"),
            motif = "retro-terminal",
        )
        "soft-gradient" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#312E81"),
            bgEndColor = Color.parseColor("#9D174D"),
            panelColor = Color.parseColor("#38FFFFFF"),
            accentColor = Color.parseColor("#FDE047"),
            secondaryAccentColor = Color.parseColor("#F472B6"),
            headlineColor = Color.parseColor("#FFFFFF"),
            captionColor = Color.parseColor("#F5D0FE"),
            motif = "soft-gradient",
        )
        "blueprint" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#0B2545"),
            bgEndColor = Color.parseColor("#133C6D"),
            panelColor = Color.parseColor("#2638BDF8"),
            accentColor = Color.parseColor("#7DD3FC"),
            secondaryAccentColor = Color.parseColor("#E0F2FE"),
            headlineColor = Color.parseColor("#F0F9FF"),
            captionColor = Color.parseColor("#BAE6FD"),
            motif = "blueprint",
        )
        "magazine-editorial" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#F5F2EB"),
            bgEndColor = Color.parseColor("#E7E2D8"),
            panelColor = Color.parseColor("#FFFFFF"),
            accentColor = Color.parseColor("#B91C1C"),
            secondaryAccentColor = Color.parseColor("#1F2937"),
            headlineColor = Color.parseColor("#111827"),
            captionColor = Color.parseColor("#4B5563"),
            motif = "magazine-editorial",
        )
        "3d-object-stage" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#0F172A"),
            bgEndColor = Color.parseColor("#1E1B4B"),
            panelColor = Color.parseColor("#312E81"),
            accentColor = Color.parseColor("#38BDF8"),
            secondaryAccentColor = Color.parseColor("#A855F7"),
            headlineColor = Color.parseColor("#F8FAFC"),
            captionColor = Color.parseColor("#C7D2FE"),
            motif = "3d-object-stage",
            capabilityNote = "2.5D layered stage (no external 3D mesh engine required)",
        )
        "split-screen" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#0F172A"),
            bgEndColor = Color.parseColor("#1E293B"),
            panelColor = Color.parseColor("#F97316"),
            accentColor = Color.parseColor("#2DD4BF"),
            secondaryAccentColor = Color.parseColor("#FB923C"),
            headlineColor = Color.parseColor("#FFFFFF"),
            captionColor = Color.parseColor("#E2E8F0"),
            motif = "split-screen",
        )
        "chalkboard" -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#14281D"),
            bgEndColor = Color.parseColor("#1D3829"),
            panelColor = Color.parseColor("#244533"),
            accentColor = Color.parseColor("#FDE047"),
            secondaryAccentColor = Color.parseColor("#F8FAFC"),
            headlineColor = Color.parseColor("#F8FAFC"),
            captionColor = Color.parseColor("#DCFCE7"),
            motif = "chalkboard",
        )
        else -> TemplateVisualStyle(
            bgStartColor = Color.parseColor("#0D1321"),
            bgEndColor = Color.parseColor("#182238"),
            panelColor = Color.parseColor("#1D2A44"),
            accentColor = Color.parseColor("#FFB703"),
            secondaryAccentColor = Color.parseColor("#64748B"),
            headlineColor = Color.parseColor("#F8FAFC"),
            captionColor = Color.parseColor("#CBD5E1"),
            motif = "minimal-text",
        )
    }
}

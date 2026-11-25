package com.example.feature.templates

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalStudioStrings
import com.example.core.render.SceneFrameRenderer
import com.example.data.templates.TemplateCatalogResult
import com.example.data.templates.TemplateDefinition
import com.example.data.templates.TemplateOrigin

enum class TemplateOriginFilter {
    ALL,
    OBSERVED,
    PROPOSED,
    FAVORITES,
}

private val CATEGORY_FILTERS = listOf(
    "all" to "All",
    "text" to "Text",
    "3d" to "3D / 2.5D",
    "news" to "News",
    "product" to "Product",
    "brand" to "Brand",
    "story" to "Story",
    "education" to "Education",
    "editorial" to "Editorial",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatesGalleryScreen(
    catalog: TemplateCatalogResult,
    renderer: SceneFrameRenderer,
    favoriteTemplateIds: Set<String>,
    recentTemplateIds: List<String>,
    selectedTemplateId: String? = null,
    onToggleFavorite: (String) -> Unit,
    onApplyTemplate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStudioStrings.current
    var originFilter by remember { mutableStateOf(TemplateOriginFilter.ALL) }
    var categoryFilter by remember { mutableStateOf("all") }

    val filteredTemplates = remember(catalog.templates, originFilter, categoryFilter, favoriteTemplateIds) {
        catalog.templates.filter { tpl ->
            val originMatches = when (originFilter) {
                TemplateOriginFilter.ALL -> true
                TemplateOriginFilter.OBSERVED -> tpl.origin == TemplateOrigin.OBSERVED_LABEL_ORIGINAL_DESIGN
                TemplateOriginFilter.PROPOSED -> tpl.origin == TemplateOrigin.PROPOSED_ORIGINAL
                TemplateOriginFilter.FAVORITES -> tpl.id in favoriteTemplateIds
            }
            val categoryMatches = categoryFilter == "all" || categoryFilter in tpl.categories
            originMatches && categoryMatches
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val columns = if (maxWidth >= 680.dp) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 960.dp)
                .align(Alignment.TopCenter)
                .testTag("templates_gallery_grid"),
        ) {
            item(span = { GridItemSpan(columns) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.templatesScreenTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "${catalog.templates.size} Valid JSON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    // Origin Separation Filter Tabs
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = originFilter == TemplateOriginFilter.ALL,
                                onClick = { originFilter = TemplateOriginFilter.ALL },
                                label = { Text("All (${catalog.templates.size})") },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("tpl_origin_all"),
                            )
                        }
                        item {
                            FilterChip(
                                selected = originFilter == TemplateOriginFilter.OBSERVED,
                                onClick = { originFilter = TemplateOriginFilter.OBSERVED },
                                label = { Text("Observed Labels (${catalog.observedCount})") },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("tpl_origin_observed"),
                            )
                        }
                        item {
                            FilterChip(
                                selected = originFilter == TemplateOriginFilter.PROPOSED,
                                onClick = { originFilter = TemplateOriginFilter.PROPOSED },
                                label = { Text("Proposed Original (${catalog.proposedCount})") },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("tpl_origin_proposed"),
                            )
                        }
                        item {
                            FilterChip(
                                selected = originFilter == TemplateOriginFilter.FAVORITES,
                                onClick = { originFilter = TemplateOriginFilter.FAVORITES },
                                label = { Text("Favorites (${favoriteTemplateIds.size})") },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("tpl_origin_fav"),
                            )
                        }
                    }

                    // Category Filters
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CATEGORY_FILTERS) { (key, label) ->
                            FilterChip(
                                selected = categoryFilter == key,
                                onClick = { categoryFilter = key },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            )
                        }
                    }
                }
            }

            items(
                items = filteredTemplates,
                key = { it.id },
            ) { template ->
                TemplatePreviewCard(
                    template = template,
                    renderer = renderer,
                    isFavorite = template.id in favoriteTemplateIds,
                    isSelected = selectedTemplateId == template.id,
                    isRecent = template.id in recentTemplateIds,
                    onToggleFavorite = { onToggleFavorite(template.id) },
                    onApply = onApplyTemplate?.let { apply -> { apply(template.id) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatePreviewCard(
    template: TemplateDefinition,
    renderer: SceneFrameRenderer,
    isFavorite: Boolean,
    isSelected: Boolean,
    isRecent: Boolean,
    onToggleFavorite: () -> Unit,
    onApply: (() -> Unit)?,
) {
    val previewBitmap: Bitmap = remember(template.id) {
        renderer.renderTemplateSampleBitmap(
            templateId = template.id,
            sampleHeadline = template.name,
            sampleCaption = "বাংলা মোশন · ${template.id}",
            width = 384,
            height = 224,
        )
    }

    val isObserved = template.origin == TemplateOrigin.OBSERVED_LABEL_ORIGINAL_DESIGN

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("template_card_${template.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9.5f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "${template.name} template preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isObserved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Text(
                        text = if (isObserved) "Observed Label · Original Design" else "Proposed Original",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isObserved) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .minimumInteractiveComponentSize()
                        .testTag("template_fav_${template.id}"),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite ${template.name}",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "ID: ${template.id}" + if (isRecent) " · Recent" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Active template",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            template.capabilityNote?.let { note ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                template.aspectRatios.forEach { ar ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = ar,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (onApply != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("apply_template_${template.id}"),
                ) {
                    Text(
                        text = if (isSelected) "Applied to Project" else "Apply Template",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

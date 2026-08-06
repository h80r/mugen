package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.entries.components.aurora.GlassmorphismCard
import eu.kanade.presentation.entries.components.getMarkdownLinkStyle
import eu.kanade.presentation.entries.components.markdownDescriptionAnnotated
import eu.kanade.presentation.entries.translation.AuroraEntryTranslationState
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

internal fun filterAnimeDescription(description: String?): String? {
    if (description.isNullOrBlank()) return null

    val patternsToFilter = listOf(
        "Original:", "Оригинал:",
        "Original Title:", "Оригинальное название:",
        "Rating:", "Рейтинг:",
        "Shikimori", "Сикимори",
        "Anilist", "Анилист",
    )

    return description.lines()
        .filterNot { line ->
            patternsToFilter.any { pattern ->
                line.contains(pattern, ignoreCase = true)
            }
        }
        .joinToString("\n")
        .trim()
        .takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeInfoCard(
    anime: Anime,
    translation: AuroraEntryTranslationState? = null,
    onTagSearch: (String) -> Unit,
    descriptionExpanded: Boolean,
    genresExpanded: Boolean,
    onToggleDescription: () -> Unit,
    onToggleGenres: () -> Unit,
    selectedGenres: Set<String> = emptySet(),
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onSearchSelected: (() -> Unit)? = null,
    onClearSelected: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    GlassmorphismCard(
        modifier = modifier,
        verticalPadding = 8.dp,
        innerPadding = 20.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val filteredDescription = remember(anime.displayDescription, translation?.description) {
                translation?.description?.takeUnless { it.isBlank() }
                    ?: filterAnimeDescription(anime.displayDescription)
            }

            Text(
                text = stringResource(AYMR.strings.aurora_description_header),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary.copy(alpha = 0.6f),
                letterSpacing = 0.8.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                val descriptionToggleEnabled = (filteredDescription?.length ?: 0) > 200
                val descriptionStyle = TextStyle(
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
                val annotatedDescription = markdownDescriptionAnnotated(
                    content = filteredDescription,
                    style = descriptionStyle,
                    linkStyle = getMarkdownLinkStyle().toSpanStyle(),
                )
                Text(
                    text = annotatedDescription ?: AnnotatedString(stringResource(AYMR.strings.aurora_no_description)),
                    style = descriptionStyle,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (descriptionToggleEnabled) {
                                Modifier.clickable { onToggleDescription() }
                            } else {
                                Modifier
                            },
                        ),
                )

                if (descriptionToggleEnabled) {
                    Icon(
                        imageVector = if (descriptionExpanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onToggleDescription() },
                    )
                }
            }

            if (!anime.displayGenre.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        val genresToShow = if (genresExpanded) anime.displayGenre!! else anime.displayGenre!!.take(3)
                        genresToShow.forEach { genre ->
                            val isSelected = genre in selectedGenres
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) {
                                            colors.accent.copy(
                                                alpha = 0.3f,
                                            )
                                        } else {
                                            colors.accent.copy(alpha = 0.15f)
                                        },
                                    )
                                    .pointerInput(genre, selectedGenres) {
                                        detectTapGestures(
                                            onTap = {
                                                if (selectedGenres.isNotEmpty()) {
                                                    onGenreLongClick?.invoke(genre)
                                                } else {
                                                    onTagSearch(genre)
                                                }
                                            },
                                            onLongPress = {
                                                onGenreLongClick?.invoke(genre)
                                            },
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = genre,
                                    fontSize = 11.sp,
                                    color = if (isSelected) colors.accent else colors.accent,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        if (selectedGenres.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent.copy(alpha = 0.8f))
                                    .clickable { onSearchSelected?.invoke() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "🔎 Search (${selectedGenres.size})",
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accent.copy(alpha = 0.15f))
                                    .clickable { onClearSelected?.invoke() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✕", color = colors.accent, fontSize = 11.sp)
                            }
                        }
                    }

                    if (anime.displayGenre!!.size > 3) {
                        Icon(
                            imageVector = if (genresExpanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onToggleGenres() },
                        )
                    }
                }
            }
        }
    }
}

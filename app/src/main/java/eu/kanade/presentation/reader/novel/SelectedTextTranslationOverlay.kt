@file:OptIn(ExperimentalMaterial3Api::class)

package eu.kanade.presentation.reader.novel

import android.text.Spanned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import eu.kanade.presentation.reader.settings.AuroraGlassSection
import eu.kanade.presentation.reader.settings.AuroraTabRow
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.NovelDictionaryUiState
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationErrorReason
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextTranslationUiState
import eu.kanade.tachiyomi.ui.reader.novel.isNovelSelectedTextSingleWord
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

private enum class TabType {
    DICTIONARY,
    TRANSLATION,
}

@Composable
internal fun SelectedTextTranslationOverlay(
    state: NovelReaderScreenModel.State.Success,
    onTranslate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onLookupDefinition: () -> Unit = {},
    onRetryDictionary: () -> Unit = {},
    onDismissDictionary: () -> Unit = {},
    onPlayPronunciation: (String) -> Unit = {},
) {
    val selection = state.selectedTextTranslationSelection ?: return
    val showTranslation = state.readerSettings.selectedTextTranslationEnabled
    val showDictionary = state.novelDictionaryEnabled
    val translationState = state.selectedTextTranslationUiState
    val dictionaryState = state.novelDictionaryUiState
    val initialTab = when {
        dictionaryState !is NovelDictionaryUiState.Idle -> TabType.DICTIONARY
        translationState !is NovelSelectedTextTranslationUiState.Idle -> TabType.TRANSLATION
        else -> return
    }
    var activeTab by remember(selection) { mutableStateOf(initialTab) }
    var showBothTabs by remember(selection) { mutableStateOf(false) }

    val aurora = AuroraTheme.colors
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    NovelReaderAuroraSheet(
        onDismissRequest = {
            onDismiss()
            onDismissDictionary()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = pageMaxHeight),
        ) {
            // Match the reader settings drawer chrome: the shared sheet supplies the glass,
            // border, blur, and bottom-sheet behavior; this is its familiar drag handle.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (aurora.isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f),
                        ),
                )
            }
            if (showBothTabs && showTranslation && showDictionary) {
                AuroraTabRow(
                    titles = listOf(
                        stringResource(AYMR.strings.novel_reader_dictionary_action_lookup),
                        stringResource(AYMR.strings.novel_reader_selected_text_translation_tab),
                    ),
                    selectedIndex = activeTab.ordinal,
                    onSelect = { index ->
                        activeTab = TabType.entries[index]
                        if (activeTab == TabType.DICTIONARY && dictionaryState is NovelDictionaryUiState.Idle) {
                            onLookupDefinition()
                        } else if (activeTab == TabType.TRANSLATION &&
                            translationState is NovelSelectedTextTranslationUiState.Idle
                        ) {
                            onTranslate()
                        }
                    },
                )
            }
            AuroraGlassSection(
                title = when (activeTab) {
                    TabType.DICTIONARY -> stringResource(AYMR.strings.novel_reader_dictionary_action_lookup)
                    TabType.TRANSLATION -> stringResource(AYMR.strings.novel_reader_selected_text_translation_tab)
                },
                modifier = Modifier.weight(1f, fill = false),
            ) {
                // A lookup only opens after its explicit console action. The alternate mode is
                // offered progressively once a result is available.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = pageMaxHeight)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (activeTab == TabType.DICTIONARY) {
                        DictionaryContent(
                            selection = selection,
                            state = dictionaryState,
                            onRetry = onRetryDictionary,
                            onPlayPronunciation = onPlayPronunciation,
                        )
                    } else {
                        TranslationContent(
                            selection = selection,
                            state = translationState,
                            onRetry = onRetry,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val offerDictionary = !showBothTabs && showDictionary &&
                    translationState is NovelSelectedTextTranslationUiState.Result &&
                    isNovelSelectedTextSingleWord(selection.text)
                val offerTranslation = !showBothTabs && showTranslation &&
                    dictionaryState is NovelDictionaryUiState.Result
                if (offerDictionary) {
                    TextButton(onClick = {
                        showBothTabs = true
                        activeTab = TabType.DICTIONARY
                        onLookupDefinition()
                    }) {
                        Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_view_definition))
                    }
                } else if (offerTranslation) {
                    TextButton(onClick = {
                        showBothTabs = true
                        activeTab = TabType.TRANSLATION
                        onTranslate()
                    }) {
                        Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_view_translation))
                    }
                }
                TextButton(onClick = {
                    onDismiss()
                    onDismissDictionary()
                }) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_close))
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun DictionaryContent(
    selection: NovelSelectedTextSelection?,
    state: NovelDictionaryUiState,
    onRetry: () -> Unit,
    onPlayPronunciation: (String) -> Unit,
) {
    when (state) {
        is NovelDictionaryUiState.Looking -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_dictionary_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        is NovelDictionaryUiState.Result -> {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Word Headword and TTS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.result.entries.firstOrNull()?.headword ?: selection?.text ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        state.result.entries.firstOrNull()?.pronunciation?.takeIf<String> { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onPlayPronunciation(
                                state.result.entries.firstOrNull()?.headword ?: selection?.text ?: "",
                            )
                        },
                        modifier = Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(12.dp),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(AYMR.strings.novel_reader_selected_text_translation_action_speak),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // Definitions List
                state.result.entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            entry.partOfSpeech?.takeIf<String> { it.isNotBlank() }?.let { pos ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = pos,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            entry.sourceLanguage?.takeIf<String> { it.isNotBlank() }?.let { lang ->
                                Text(
                                    text = lang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = htmlToAnnotatedString(entry.definitionsHtml),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Attribution Footer
                state.result.attribution?.takeIf<String> { it.isNotBlank() }?.let { attr ->
                    Text(
                        text = attr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        is NovelDictionaryUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        is NovelDictionaryUiState.Unavailable -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        NovelDictionaryUiState.Idle -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun TranslationContent(
    selection: NovelSelectedTextSelection?,
    state: NovelSelectedTextTranslationUiState,
    onRetry: () -> Unit,
) {
    when (state) {
        is NovelSelectedTextTranslationUiState.Translating -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_selected_text_translation_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        is NovelSelectedTextTranslationUiState.Result -> {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = selection?.text ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.translationResult.translation,
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.translationResult.detectedSourceLanguage?.takeIf<String> { it.isNotBlank() }?.let { lang ->
                    Text(
                        text = stringResource(AYMR.strings.novel_reader_selected_text_translation_source_language, lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is NovelSelectedTextTranslationUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        is NovelSelectedTextTranslationUiState.Unavailable -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = translationErrorMessage(state.reason), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) {
                    Text(stringResource(AYMR.strings.novel_reader_selected_text_translation_action_retry))
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

private fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return spanned.toAnnotatedString()
}

private fun Spanned.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    val spannedText = this@toAnnotatedString
    append(spannedText.toString())

    val styleSpans = spannedText.getSpans(0, spannedText.length, android.text.style.StyleSpan::class.java)
    for (span in styleSpans) {
        val start = spannedText.getSpanStart(span)
        val end = spannedText.getSpanEnd(span)
        if (start in 0..spannedText.length && end in 0..spannedText.length && start < end) {
            when (span.style) {
                android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                android.graphics.Typeface.BOLD_ITALIC -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    start,
                    end,
                )
            }
        }
    }

    val underlineSpans = spannedText.getSpans(0, spannedText.length, android.text.style.UnderlineSpan::class.java)
    for (span in underlineSpans) {
        val start = spannedText.getSpanStart(span)
        val end = spannedText.getSpanEnd(span)
        if (start in 0..spannedText.length && end in 0..spannedText.length && start < end) {
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
        }
    }
}

@Composable
private fun translationErrorMessage(reason: NovelSelectedTextTranslationErrorReason): String {
    return when (reason) {
        NovelSelectedTextTranslationErrorReason.EmptySelection,
        NovelSelectedTextTranslationErrorReason.TooLongSelection,
        NovelSelectedTextTranslationErrorReason.ParserFailure,
        NovelSelectedTextTranslationErrorReason.WebViewUnavailable,
        -> {
            stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.BackendUnavailable -> {
            reason.message?.takeIf<String> { it.isNotBlank() }
                ?: stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.NetworkFailure -> {
            reason.message?.takeIf<String> { it.isNotBlank() }
                ?: stringResource(AYMR.strings.novel_reader_selected_text_translation_unavailable)
        }
        is NovelSelectedTextTranslationErrorReason.Cooldown -> {
            "${stringResource(
                AYMR.strings.novel_reader_selected_text_translation_unavailable,
            )} (${reason.remainingSeconds}s)"
        }
    }
}

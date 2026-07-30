package eu.kanade.presentation.reader.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.ActionMode
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage
import eu.kanade.domain.easteregg.lattice.LatticeCarrier
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateTimeText
import eu.kanade.presentation.easteregg.lattice.LatticeCarrierSlot
import eu.kanade.presentation.reader.DisplayRefreshHost
import eu.kanade.presentation.reader.ReaderChapterListItem
import eu.kanade.presentation.reader.ReaderChapterListSheet
import eu.kanade.presentation.reader.components.AutoScrollActionFab
import eu.kanade.presentation.reader.novel.components.SeamlessFocusGuardLayout
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSection
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookSpine
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookUiCommand
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import eu.kanade.tachiyomi.ui.reader.novel.encodeNativeScrollProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodePageReaderProgress
import eu.kanade.tachiyomi.ui.reader.novel.encodeWebScrollProgressPercent
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelBookFlipAnimationSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnActivationZone
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnShadowIntensity
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTurnSpeed
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTapZoneAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import eu.kanade.tachiyomi.ui.reader.novel.setting.parseNovelReaderTapZoneActions
import eu.kanade.tachiyomi.ui.reader.novel.setting.resolveConfiguredNovelReaderTapAction
import eu.kanade.tachiyomi.ui.reader.novel.tts.NativeScrollTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.NativeScrollTtsNavigator
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsNavigationAnchor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPageReaderPosition
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPageSlice
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsPlaybackStartRequest
import eu.kanade.tachiyomi.ui.reader.novel.tts.PageReaderTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.PageReaderTtsNavigator
import eu.kanade.tachiyomi.ui.reader.novel.tts.WebViewTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.WebViewTtsNavigator
import eu.kanade.tachiyomi.ui.reader.novel.tts.resolvePlainPageReaderTtsAnchors
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt

@Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
internal fun resolveNovelReaderBackdropColor(
    settings: NovelReaderSettings,
    isSystemDark: Boolean,
): Color {
    val theme = safeEnum(settings.theme, NovelReaderTheme.SYSTEM)
    val themeFallback = when (theme) {
        NovelReaderTheme.SYSTEM -> if (isSystemDark) Color(0xFF121212) else Color.White
        NovelReaderTheme.LIGHT -> Color.White
        NovelReaderTheme.DARK -> Color(0xFF121212)
    }
    val themeBackground = parseReaderColor(settings.backgroundColor)
        .takeIf { settings.backgroundColor?.isNotBlank() == true }
        ?: themeFallback

    val appearanceMode = safeEnum(settings.appearanceMode, NovelReaderAppearanceMode.THEME)
    return when (appearanceMode) {
        NovelReaderAppearanceMode.THEME -> themeBackground
        NovelReaderAppearanceMode.BACKGROUND -> {
            resolveReaderBackgroundBackdropColor(
                resolveReaderBackgroundSelection(
                    backgroundSource = safeEnum(settings.backgroundSource, NovelReaderBackgroundSource.PRESET),
                    backgroundPresetId = settings.backgroundPresetId,
                    customBackgroundId = settings.customBackgroundId,
                    customBackgroundItems = emptyList(),
                    customBackgroundPath = settings.customBackgroundPath,
                    customBackgroundExists = settings.customBackgroundPath.orEmpty().isNotBlank() &&
                        File(settings.customBackgroundPath.orEmpty()).exists(),
                ),
            )
        }
    }
}

private fun buildSourceIndexedPageReaderTextList(
    blocks: List<PlainPageReaderTextBlock>,
): List<String> {
    val maxSourceBlockIndex = blocks.maxOfOrNull { it.sourceBlockIndex } ?: return emptyList()
    return MutableList(maxSourceBlockIndex + 1) { "" }.apply {
        blocks.forEach { block ->
            this[block.sourceBlockIndex] = block.text
        }
    }
}

private const val MENU_ID_DICTIONARY = 0x9001
private const val MENU_ID_TRANSLATION = 0x9002

class NovelReaderWebView(context: Context) : WebView(context) {
    var localSelection: NovelSelectedTextSelection? = null
    var onSelectedTextSelectionChanged: ((NovelSelectedTextSelection?) -> Unit)? = null
    var isExecutingAction = false
    var isDictionaryEnabled = false
    var isTranslationEnabled = false

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val wrappedCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                val result = callback?.onCreateActionMode(mode, menu) ?: true
                if (menu != null) {
                    val menuOrder = 100
                    if (isDictionaryEnabled) {
                        menu.add(
                            Menu.NONE,
                            MENU_ID_DICTIONARY,
                            menuOrder,
                            context.getString(AYMR.strings.novel_reader_text_selection_action_dictionary.resourceId),
                        )
                    }
                    if (isTranslationEnabled) {
                        menu.add(
                            Menu.NONE,
                            MENU_ID_TRANSLATION,
                            menuOrder + 1,
                            context.getString(AYMR.strings.novel_reader_text_selection_action_translate.resourceId),
                        )
                    }
                }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return callback?.onPrepareActionMode(mode, menu) ?: false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item != null) {
                    val selection = localSelection
                    if (selection != null) {
                        when (item.itemId) {
                            MENU_ID_DICTIONARY -> {
                                isExecutingAction = true
                                val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.DICTIONARY)
                                onSelectedTextSelectionChanged?.invoke(selectionWithAction)
                                mode?.finish()
                                return true
                            }
                            MENU_ID_TRANSLATION -> {
                                isExecutingAction = true
                                val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.TRANSLATION)
                                onSelectedTextSelectionChanged?.invoke(selectionWithAction)
                                mode?.finish()
                                return true
                            }
                        }
                    }
                }
                return callback?.onActionItemClicked(mode, item) ?: false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                callback?.onDestroyActionMode(mode)
            }
        }
        return super.startActionMode(wrappedCallback, type)
    }
}

fun createNovelReaderWebView(context: Context): WebView {
    return NovelReaderWebView(context)
}

@Suppress("ktlint:standard:max-line-length", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelReaderScreen(
    rawState: NovelReaderScreenModel.State.Success,
    showReaderUi: Boolean,
    bookEngineSpine: NovelBookSpine = NovelBookSpine.EMPTY,
    bookEngineLocation: NovelBookLocation = NovelBookLocation.START,
    bookModeCommands: List<NovelBookUiCommand> = emptyList(),
    actions: NovelReaderScreenActions,
) {
    val onBack = actions.onBack
    val onReadingProgress = actions.onReadingProgress
    val onSeekBookModeProgress = actions.onSeekBookModeProgress
    val onToggleBookmark = actions.onToggleBookmark
    val onOpenDictionaryHistory = actions.onOpenDictionaryHistory
    val onStartGeminiTranslation = actions.onStartGeminiTranslation
    val onStopGeminiTranslation = actions.onStopGeminiTranslation
    val onToggleGeminiTranslationVisibility = actions.onToggleGeminiTranslationVisibility
    val onClearGeminiTranslation = actions.onClearGeminiTranslation
    val onClearAllGeminiTranslationCache = actions.onClearAllGeminiTranslationCache
    val onAddAiTranslationLog = actions.onAddAiTranslationLog
    val onClearGeminiLogs = actions.onClearGeminiLogs
    val onSetGeminiApiKey = actions.onSetGeminiApiKey
    val onSetGeminiModel = actions.onSetGeminiModel
    val onSetGeminiBatchSize = actions.onSetGeminiBatchSize
    val onSetGeminiConcurrency = actions.onSetGeminiConcurrency
    val onSetGeminiRelaxedMode = actions.onSetGeminiRelaxedMode
    val onSetGeminiDisableCache = actions.onSetGeminiDisableCache
    val onSetGeminiReasoningEffort = actions.onSetGeminiReasoningEffort
    val onSetGeminiBudgetTokens = actions.onSetGeminiBudgetTokens
    val onSetGeminiTemperature = actions.onSetGeminiTemperature
    val onSetGeminiTopP = actions.onSetGeminiTopP
    val onSetGeminiTopK = actions.onSetGeminiTopK
    val onSetGeminiPromptMode = actions.onSetGeminiPromptMode
    val onSetGeminiSourceLang = actions.onSetGeminiSourceLang
    val onSetGeminiTargetLang = actions.onSetGeminiTargetLang
    val onSetGeminiStylePreset = actions.onSetGeminiStylePreset
    val onSetGeminiEnabledPromptModifiers = actions.onSetGeminiEnabledPromptModifiers
    val onSetGeminiCustomPromptModifier = actions.onSetGeminiCustomPromptModifier
    val onSetGeminiAutoTranslateEnglishSource = actions.onSetGeminiAutoTranslateEnglishSource
    val onSetGeminiPrefetchNextChapterTranslation = actions.onSetGeminiPrefetchNextChapterTranslation
    val onSetGeminiPrivateUnlocked = actions.onSetGeminiPrivateUnlocked
    val onSetGeminiPrivatePythonLikeMode = actions.onSetGeminiPrivatePythonLikeMode
    val onSetTranslationProvider = actions.onSetTranslationProvider
    val onSetOpenRouterBaseUrl = actions.onSetOpenRouterBaseUrl
    val onSetOpenRouterApiKey = actions.onSetOpenRouterApiKey
    val onSetOpenRouterModel = actions.onSetOpenRouterModel
    val onRefreshOpenRouterModels = actions.onRefreshOpenRouterModels
    val onTestOpenRouterConnection = actions.onTestOpenRouterConnection
    val onSetDeepSeekBaseUrl = actions.onSetDeepSeekBaseUrl
    val onSetDeepSeekApiKey = actions.onSetDeepSeekApiKey
    val onSetDeepSeekModel = actions.onSetDeepSeekModel
    val onRefreshDeepSeekModels = actions.onRefreshDeepSeekModels
    val onTestDeepSeekConnection = actions.onTestDeepSeekConnection
    val onSetMistralBaseUrl = actions.onSetMistralBaseUrl
    val onSetMistralApiKey = actions.onSetMistralApiKey
    val onSetMistralModel = actions.onSetMistralModel
    val onRefreshMistralModels = actions.onRefreshMistralModels
    val onTestMistralConnection = actions.onTestMistralConnection
    val onSetNvidiaBaseUrl = actions.onSetNvidiaBaseUrl
    val onSetNvidiaApiKey = actions.onSetNvidiaApiKey
    val onSetNvidiaModel = actions.onSetNvidiaModel
    val onRefreshNvidiaModels = actions.onRefreshNvidiaModels
    val onTestNvidiaConnection = actions.onTestNvidiaConnection
    val onSetOllamaCloudBaseUrl = actions.onSetOllamaCloudBaseUrl
    val onSetOllamaCloudApiKey = actions.onSetOllamaCloudApiKey
    val onSetOllamaCloudModel = actions.onSetOllamaCloudModel
    val onRefreshOllamaCloudModels = actions.onRefreshOllamaCloudModels
    val onTestOllamaCloudConnection = actions.onTestOllamaCloudConnection
    val onStartGoogleTranslation = actions.onStartGoogleTranslation
    val onStopGoogleTranslation = actions.onStopGoogleTranslation
    val onResumeGoogleTranslation = actions.onResumeGoogleTranslation
    val onToggleGoogleTranslationVisibility = actions.onToggleGoogleTranslationVisibility
    val onClearGoogleTranslation = actions.onClearGoogleTranslation
    val onSetGoogleTranslationEnabled = actions.onSetGoogleTranslationEnabled
    val onSetGoogleTranslationAutoStart = actions.onSetGoogleTranslationAutoStart
    val onSetGoogleTranslationSourceLang = actions.onSetGoogleTranslationSourceLang
    val onSetGoogleTranslationTargetLang = actions.onSetGoogleTranslationTargetLang
    val onToggleTtsPlayback = actions.onToggleTtsPlayback
    val onStopTtsPlayback = actions.onStopTtsPlayback
    val onSkipPreviousTts = actions.onSkipPreviousTts
    val onSkipNextTts = actions.onSkipNextTts
    val onPauseTtsForManualNavigation = actions.onPauseTtsForManualNavigation
    val onSetTtsEnginePackage = actions.onSetTtsEnginePackage
    val onSetTtsVoiceId = actions.onSetTtsVoiceId
    val onSetTtsLocaleTag = actions.onSetTtsLocaleTag
    val onSetTtsSpeechRate = actions.onSetTtsSpeechRate
    val onSetTtsPitch = actions.onSetTtsPitch
    val onDisableTts = actions.onDisableTts
    val onPreviewTtsVoice = actions.onPreviewTtsVoice
    val onStopTtsVoicePreview = actions.onStopTtsVoicePreview
    val onOpenPreviousChapter = actions.onOpenPreviousChapter
    val onOpenNextChapter = actions.onOpenNextChapter
    val onPrepareAutoScrollHandoff = actions.onPrepareAutoScrollHandoff
    val onConsumeAutoScrollHandoff = actions.onConsumeAutoScrollHandoff
    val onCancelAutoScrollHandoff = actions.onCancelAutoScrollHandoff
    val onRequestAutoScrollNextChapterPrefetch = actions.onRequestAutoScrollNextChapterPrefetch
    val onOpenChapter = actions.onOpenChapter
    val onDownloadChapter = actions.onDownloadChapter
    val onSetShowReaderUi = actions.onSetShowReaderUi
    val onOpenBottomSheet = actions.onOpenBottomSheet
    val onSelectedTextSelectionChanged = actions.onSelectedTextSelectionChanged
    val onTranslateSelectedText = actions.onTranslateSelectedText
    val onRetrySelectedTextTranslation = actions.onRetrySelectedTextTranslation
    val onDismissSelectedTextTranslation = actions.onDismissSelectedTextTranslation
    val onLookupSelectedTextDefinition = actions.onLookupSelectedTextDefinition
    val onRetryNovelDictionary = actions.onRetryNovelDictionary
    val onDismissNovelDictionary = actions.onDismissNovelDictionary
    val onPlaySelectedTextPronunciation = actions.onPlaySelectedTextPronunciation
    val loadBookEngineDocument = actions.loadBookEngineDocument
    val onBookEngineLocationChanged = actions.onBookEngineLocationChanged
    val onBookEngineSectionMeasured = actions.onBookEngineSectionMeasured
    val onBookModeCommandsExecuted = actions.onBookModeCommandsExecuted
    val onBookModeScroll = actions.onBookModeScroll
    val onBookModeSectionMeasured = actions.onBookModeSectionMeasured
    val onBookModeRetrySection = actions.onBookModeRetrySection
    val onBookModeDocumentReady = actions.onBookModeDocumentReady
    val onPrepareWholeBook = actions.onPrepareWholeBook
    val nativeBookBlocksForSection = actions.nativeBookBlocksForSection
    val sanitizedSettings = remember(rawState.readerSettings) {
        rawState.readerSettings.copy(
            theme = safeEnum(rawState.readerSettings.theme, NovelReaderTheme.SYSTEM),
            appearanceMode = safeEnum(rawState.readerSettings.appearanceMode, NovelReaderAppearanceMode.THEME),
            backgroundSource = safeEnum(rawState.readerSettings.backgroundSource, NovelReaderBackgroundSource.PRESET),
            backgroundTexture = safeEnum(rawState.readerSettings.backgroundTexture, NovelReaderBackgroundTexture.NONE),
            textAlign = safeEnum(rawState.readerSettings.textAlign, TextAlign.SOURCE),
            pageTransitionStyle = safeEnum(rawState.readerSettings.pageTransitionStyle, NovelPageTransitionStyle.SLIDE),
            bookFlipAnimationSpeed = safeEnum(
                rawState.readerSettings.bookFlipAnimationSpeed,
                NovelBookFlipAnimationSpeed.SLOW,
            ),
            pageTurnSpeed = safeEnum(rawState.readerSettings.pageTurnSpeed, NovelPageTurnSpeed.NORMAL),
            pageTurnIntensity = safeEnum(rawState.readerSettings.pageTurnIntensity, NovelPageTurnIntensity.MEDIUM),
            pageTurnShadowIntensity = safeEnum(
                rawState.readerSettings.pageTurnShadowIntensity,
                NovelPageTurnShadowIntensity.MEDIUM,
            ),
            pageTurnActivationZone = safeEnum(
                rawState.readerSettings.pageTurnActivationZone,
                NovelPageTurnActivationZone.WIDE,
            ),
            translationProvider = safeEnum(
                rawState.readerSettings.translationProvider,
                NovelTranslationProvider.GEMINI,
            ),
            geminiPromptMode = safeEnum(rawState.readerSettings.geminiPromptMode, GeminiPromptMode.ADULT_18),
            geminiStylePreset = safeEnum(
                rawState.readerSettings.geminiStylePreset,
                NovelTranslationStylePreset.PROFESSIONAL,
            ),
            ttsHighlightMode = safeEnum(rawState.readerSettings.ttsHighlightMode, NovelTtsHighlightMode.AUTO),
        )
    }
    val state = remember(rawState, sanitizedSettings) {
        rawState.copy(readerSettings = sanitizedSettings)
    }
    // Sub-object selectors: derivedStateOf prevents recomposition of translation/gemini
    // panels when unrelated state (scroll progress) changes.
    val readerSettings by remember(state) { derivedStateOf { state.readerSettings } }
    val geminiTranslation by remember(state) { derivedStateOf { state.geminiTranslation } }
    val googleTranslation by remember(state) { derivedStateOf { state.googleTranslation } }
    val aiProviders by remember(state) { derivedStateOf { state.aiProviders } }
    val progress by remember(state) { derivedStateOf { state.progress } }

    var showSettings by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showTtsBehaviorSettings by remember { mutableStateOf(false) }
    var selectedTextSelectionSessionId by remember(state.chapter.id) {
        mutableIntStateOf(0)
    }
    val appHaptics = LocalAppHaptics.current
    val ttsPlacement = remember(state.readerSettings.ttsEnabled) {
        resolveNovelReaderTtsSettingsPlacementSnapshot(state.readerSettings.ttsEnabled)
    }
    LaunchedEffect(ttsPlacement.showFooterEntry) {
        if (!ttsPlacement.showFooterEntry) {
            showTtsBehaviorSettings = false
        }
    }
    // A seamless in-place chapter switch reuses this composition, so the renderer that is already
    // mounted must not change while the chapter content is swapped: detaching the reader WebView in
    // the same pass that mounts the native lazy list makes Android restart a focus search from the
    // window root, and Compose then disposes lazy subcompositions in the middle of that layout pass
    // ("Cannot start a writer when another writer is pending"). Keep the mounted renderer until the
    // reader screen itself is recreated or the reader settings change.
    val mountedReaderRenderer = remember { arrayOfNulls<Boolean>(1) }
    val mountedRendererSwitchToken = remember { longArrayOf(state.seamlessSwitchToken) }
    val seamlessRendererSwap = mountedReaderRenderer[0] != null &&
        state.seamlessSwitchToken != mountedRendererSwitchToken[0]
    var showWebView by remember(
        state.chapter.id,
        state.readerSettings.preferWebViewRenderer,
        state.contentBlocks.size,
        state.bookMode.isEnabled,
    ) {
        val resolvedShowWebView = shouldStartInWebView(
            preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            pageReaderEnabled = state.readerSettings.pageReader,
            contentBlocksCount = state.contentBlocks.size,
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
            bookModeEnabled = state.bookMode.isEnabled,
        )
        mutableStateOf(
            if (seamlessRendererSwap) {
                mountedReaderRenderer[0] ?: resolvedShowWebView
            } else {
                resolvedShowWebView
            },
        )
    }
    mountedReaderRenderer[0] = showWebView
    mountedRendererSwitchToken[0] = state.seamlessSwitchToken
    val nextSelectedTextSelectionSessionId = remember(state.chapter.id) {
        {
            selectedTextSelectionSessionId += 1
            selectedTextSelectionSessionId.toLong()
        }
    }
    LaunchedEffect(
        state.chapter.id,
        state.readerSettings.preferWebViewRenderer,
        state.readerSettings.richNativeRendererExperimental,
        state.readerSettings.pageReader,
        state.contentBlocks.size,
        state.richContentUnsupportedFeaturesDetected,
        state.bookMode.isEnabled,
    ) {
        // Never flip the mounted renderer as part of a seamless chapter swap (see comment above).
        if (seamlessRendererSwap) return@LaunchedEffect
        showWebView = syncShowWebViewWithReaderSettings(
            currentShowWebView = showWebView,
            preferWebViewRenderer = state.readerSettings.preferWebViewRenderer,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            pageReaderEnabled = state.readerSettings.pageReader,
            contentBlocksCount = state.contentBlocks.size,
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
            bookModeEnabled = state.bookMode.isEnabled,
        )
    }
    val readerPreferences = remember { Injekt.get<NovelReaderPreferences>() }
    // Opt-in experimental chapter handoff: in-place chapter switch for scrolled reading and no
    // intermediate chapter page in the paged reader. Disabled by default.
    val seamlessChapterTransitionEnabled by readerPreferences.seamlessChapterTransition().collectAsState()
    val displayRefreshPreferences = remember { Injekt.get<ReaderPreferences>() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val flashOnPageChange by displayRefreshPreferences.flashOnPageChange().collectAsState()
    val eInkProfile by uiPreferences.eInkProfile().collectAsState()
    val displayRefreshHost = remember { DisplayRefreshHost() }
    val sourceId = state.novel.source
    val hasSourceOverride = remember(sourceId) { readerPreferences.getSourceOverride(sourceId) != null }
    var pageViewportSize by remember(state.chapter.id) { mutableStateOf(IntSize.Zero) }
    var hasCompletedInitialReaderLayout by remember(state.chapter.id) { mutableStateOf(false) }
    val autoScrollHandoff = remember(state.chapter.id) {
        onConsumeAutoScrollHandoff(state.chapter.id)
    }
    var autoScrollEnabled by remember(state.chapter.id, autoScrollHandoff) {
        mutableStateOf(
            resolveInitialAutoScrollEnabled(
                savedPreferenceEnabled = state.readerSettings.autoScroll,
                handoff = autoScrollHandoff,
            ),
        )
    }
    var autoScrollSpeed by remember(state.chapter.id, state.readerSettings.autoScrollInterval, autoScrollHandoff) {
        mutableIntStateOf(
            autoScrollHandoff?.speed ?: intervalToAutoScrollSpeed(state.readerSettings.autoScrollInterval),
        )
    }
    var autoScrollExpanded by remember(state.chapter.id) { mutableStateOf(false) }
    var autoScrollWasUsed by remember(state.chapter.id) { mutableStateOf(false) }
    var touchCooldownUntilNanos by remember(state.chapter.id) { mutableLongStateOf(0L) }
    var speedFactor by remember(state.chapter.id) { mutableFloatStateOf(1f) }
    var autoScrollEndStableFrames by remember(state.chapter.id) { mutableIntStateOf(0) }
    var autoScrollEndDwellActive by remember(state.chapter.id) { mutableStateOf(false) }
    var autoScrollEndDwellRemainingSeconds by remember(state.chapter.id) { mutableIntStateOf(0) }
    var showGeminiDialog by remember(state.chapter.id) { mutableStateOf(false) }
    var showGoogleDialog by remember(state.chapter.id) { mutableStateOf(false) }
    var translationSwitchRequest by remember(state.chapter.id) {
        mutableStateOf<TranslationSwitchRequest?>(null)
    }
    var requestedTtsChapterSyncTarget by remember(state.chapter.id) { mutableStateOf<Long?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pendingProgrammaticTtsBlockIndex by remember(state.chapter.id) { mutableStateOf<Int?>(null) }
    var suppressManualTtsPauseUntilMs by remember(state.chapter.id) { mutableLongStateOf(0L) }
    val shouldHideWebViewUntilReveal = state.enableJs
    var webProgressPercent by remember(state.chapter.id) {
        mutableIntStateOf(state.lastSavedWebProgressPercent.coerceIn(0, 100))
    }
    var webAutoScrollNearEnd by remember(state.chapter.id) { mutableStateOf(false) }
    var shouldRestoreWebScroll by remember(state.chapter.id) { mutableStateOf(true) }
    var webViewPageReadyForAutoScroll by remember(state.chapter.id) { mutableStateOf(false) }
    var appliedWebCssFingerprint by remember(state.chapter.id) { mutableStateOf<String?>(null) }
    // Deliberately a plain holder instead of Compose state: it has to survive an in-place chapter
    // switch (so it cannot be keyed on the chapter id) and it is written from the AndroidView update
    // block, where a snapshot state write would schedule recomposition during a layout pass.
    val appliedSeamlessSwitchToken = remember { longArrayOf(state.seamlessSwitchToken) }
    var hasReportedReadingProgress by remember(state.chapter.id, showWebView, state.readerSettings.pageReader) {
        mutableStateOf(false)
    }
    fun persistAutoScrollEnabledPreference(
        enabled: Boolean,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScroll = enabled,
                )
            }
        } else {
            readerPreferences.autoScroll().set(enabled)
        }
    }
    fun persistAutoScrollIntervalPreference(
        interval: Int,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollInterval = interval,
                )
            }
        } else {
            readerPreferences.autoScrollInterval().set(interval)
        }
    }
    fun persistAutoScrollAdaptiveDelayPreference(
        enabled: Boolean,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollAdaptiveDelay = enabled,
                )
            }
        } else {
            readerPreferences.autoScrollAdaptiveDelay().set(enabled)
        }
    }
    fun persistAutoScrollChapterEndBehaviorPreference(
        behavior: NovelAutoScrollChapterEndBehavior,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollChapterEndBehavior = behavior,
                )
            }
        } else {
            readerPreferences.autoScrollChapterEndBehavior().set(behavior)
        }
    }
    fun persistAutoScrollEndPauseMsPreference(
        pauseMs: Long,
    ) {
        if (hasSourceOverride) {
            readerPreferences.updateSourceOverride(sourceId) { override ->
                override.copy(
                    autoScrollEndPauseMs = pauseMs,
                )
            }
        } else {
            readerPreferences.autoScrollEndPauseMs().set(pauseMs)
        }
    }
    fun reportReadingProgress(
        currentIndex: Int,
        totalItems: Int,
        persistedProgress: Long?,
        flashDisplay: Boolean = false,
    ) {
        // Book mode keeps progress in its own domain (spine section + fraction, persisted as an
        // encoded book location). The classic per-chapter reporters (web scroll listener, paginated
        // and native readers) stay wired up for the normal reader, so ignore them while book mode is
        // active instead of letting a per-chapter percentage overwrite the book location.
        if (state.bookMode.isEnabled) return
        if (flashDisplay && flashOnPageChange && eInkProfile.isEnabled && hasReportedReadingProgress) {
            displayRefreshHost.flash()
        }
        hasReportedReadingProgress = true
        onReadingProgress(currentIndex, totalItems, persistedProgress)
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val viewConfiguration = LocalViewConfiguration.current
    val batteryLevel by rememberBatteryLevel(context)
    val timeText by rememberCurrentTimeText(context)
    val geminiTranslationLabel = stringResource(AYMR.strings.novel_reader_gemini_button)
    val googleTranslationLabel = stringResource(AYMR.strings.novel_reader_google_translate)
    val disableGeminiForGoogleMessage = stringResource(
        AYMR.strings.novel_reader_google_translate_disable_other_first,
        geminiTranslationLabel,
    )
    val disableGoogleForGeminiMessage = stringResource(
        AYMR.strings.novel_reader_google_translate_disable_other_first,
        googleTranslationLabel,
    )
    fun requestGoogleTranslationStart() {
        when {
            state.isGeminiTranslating -> {
                Toast.makeText(
                    context,
                    disableGeminiForGoogleMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            state.isGeminiTranslationVisible || state.hasGeminiTranslationCache -> {
                translationSwitchRequest = TranslationSwitchRequest(
                    from = TranslationKind.Gemini,
                    to = TranslationKind.Google,
                )
            }
            else -> onStartGoogleTranslation()
        }
    }
    fun requestGeminiTranslationStart() {
        when {
            state.isGoogleTranslating -> {
                Toast.makeText(
                    context,
                    disableGoogleForGeminiMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            state.isGoogleTranslationVisible || state.hasGoogleTranslationCache -> {
                translationSwitchRequest = TranslationSwitchRequest(
                    from = TranslationKind.Google,
                    to = TranslationKind.Gemini,
                )
            }
            else -> onStartGeminiTranslation()
        }
    }
    val missingCustomBackgroundMessage =
        stringResource(AYMR.strings.novel_reader_background_custom_missing_fallback)
    val customBackgroundId = state.readerSettings.customBackgroundId
    val customBackgroundPath = state.readerSettings.customBackgroundPath
    val customBackgroundItems = remember(
        customBackgroundId,
        customBackgroundPath,
    ) {
        if (
            customBackgroundPath.isNotBlank() &&
            customBackgroundId.isNotBlank() &&
            customBackgroundId == customBackgroundPath
        ) {
            ensureLegacyNovelReaderBackgroundItem(
                context = context,
                legacyPath = customBackgroundPath,
                preferredId = customBackgroundId,
            )
        }

        readNovelReaderCustomBackgroundItems(context)
    }
    val customBackgroundExists = remember(
        customBackgroundId,
        customBackgroundPath,
        customBackgroundItems,
    ) {
        val selectedPathFromCatalog = customBackgroundItems
            .firstOrNull { it.id == customBackgroundId }
            ?.absolutePath
        val candidatePath = selectedPathFromCatalog ?: customBackgroundPath
        candidatePath.isNotBlank() && File(candidatePath).exists()
    }
    val backgroundSource = readerSettings.backgroundSource
    val backgroundSelection = remember(
        backgroundSource,
        readerSettings.backgroundPresetId,
        customBackgroundId,
        customBackgroundPath,
        customBackgroundItems,
        customBackgroundExists,
    ) {
        resolveReaderBackgroundSelection(
            backgroundSource = backgroundSource,
            backgroundPresetId = readerSettings.backgroundPresetId,
            customBackgroundId = customBackgroundId,
            customBackgroundItems = customBackgroundItems,
            customBackgroundPath = customBackgroundPath,
            customBackgroundExists = customBackgroundExists,
        )
    }
    val backgroundImageModel = remember(backgroundSelection) {
        resolveReaderBackgroundImageModel(backgroundSelection)
    }
    val customBackgroundLuminance = remember(backgroundSelection.customPath) {
        backgroundSelection.customPath?.let(::sampleReaderBackgroundLuminance)
    }
    val effectiveBackgroundLuminance = remember(
        backgroundSelection.source,
        backgroundSelection.preset.isDarkPreferred,
        customBackgroundLuminance,
    ) {
        when (backgroundSelection.source) {
            NovelReaderBackgroundSource.PRESET -> {
                if (backgroundSelection.preset.isDarkPreferred) {
                    0.2f
                } else {
                    0.8f
                }
            }
            NovelReaderBackgroundSource.CUSTOM -> {
                customBackgroundLuminance ?: when (backgroundSelection.customIsDarkHint) {
                    true -> 0.2f
                    false -> 0.8f
                    null -> if (backgroundSelection.preset.isDarkPreferred) 0.2f else 0.8f
                }
            }
        }
    }
    val backgroundModeTextColor = remember(effectiveBackgroundLuminance) {
        resolveReaderTextColorForBackgroundMode(effectiveBackgroundLuminance)
    }
    val backgroundModeBaseColor = remember(backgroundSelection) {
        resolveReaderBackgroundBackdropColor(backgroundSelection)
    }
    val backgroundModeWebImageUrl = remember(backgroundSelection) {
        resolveReaderBackgroundWebImageUrl(backgroundSelection)
    }
    val backgroundModeIdentity = remember(backgroundSelection) {
        resolveReaderBackgroundIdentity(backgroundSelection)
    }
    val isEInkMode = AuroraTheme.colors.isEInk
    val appearanceMode = readerSettings.appearanceMode
    val isBackgroundMode = appearanceMode == NovelReaderAppearanceMode.BACKGROUND
    val activeBackgroundTexture = if (isBackgroundMode || isEInkMode) {
        NovelReaderBackgroundTexture.NONE
    } else {
        readerSettings.backgroundTexture
    }
    val activeOledEdgeGradient = if (isBackgroundMode || isEInkMode) {
        false
    } else {
        state.readerSettings.oledEdgeGradient == true
    }
    val theme = readerSettings.theme
    val isDarkTheme = when {
        isEInkMode -> AuroraTheme.colors.isDark
        else -> when (theme) {
            NovelReaderTheme.SYSTEM -> MaterialTheme.colorScheme.background.luminance() < 0.5f
            NovelReaderTheme.DARK -> true
            NovelReaderTheme.LIGHT -> false
        }
    }
    val fallbackTextColor = if (isEInkMode) {
        AuroraTheme.colors.textPrimary
    } else if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFFEDEDED)
    } else {
        androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    }
    val fallbackBackground = if (isEInkMode) {
        AuroraTheme.colors.background
    } else if (isDarkTheme) {
        androidx.compose.ui.graphics.Color(0xFF121212)
    } else {
        androidx.compose.ui.graphics.Color.White
    }
    val themeModeTextColor = parseReaderColor(state.readerSettings.textColor)
        .takeIf { state.readerSettings.textColor?.isNotBlank() == true }
        ?: fallbackTextColor
    val themeModeBackground = parseReaderColor(state.readerSettings.backgroundColor)
        .takeIf { state.readerSettings.backgroundColor?.isNotBlank() == true }
        ?: fallbackBackground
    val textColor = when {
        isEInkMode -> AuroraTheme.colors.textPrimary
        isBackgroundMode -> backgroundModeTextColor
        else -> themeModeTextColor
    }
    val chapterTitleTextColor = textColor
    val textBackground = when {
        isEInkMode -> AuroraTheme.colors.background
        isBackgroundMode -> backgroundModeBaseColor
        else -> themeModeBackground
    }

    SideEffect {
        NovelReaderBackdropSession.update(textBackground)
    }

    LaunchedEffect(
        isBackgroundMode,
        isEInkMode,
        backgroundSource,
        customBackgroundPath,
        customBackgroundExists,
    ) {
        if (isBackgroundMode &&
            !isEInkMode &&
            backgroundSource == NovelReaderBackgroundSource.CUSTOM &&
            customBackgroundPath.isNotBlank() &&
            !customBackgroundExists
        ) {
            Toast.makeText(context, missingCustomBackgroundMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val readerFontCatalog = remember(context) {
        buildNovelReaderFontCatalog(context)
    }
    val selectedReaderFont = remember(state.readerSettings.fontFamily, readerFontCatalog) {
        resolveNovelReaderSelectedFont(
            fonts = readerFontCatalog,
            selectedFontId = state.readerSettings.fontFamily,
        )
    }
    val composeTypeface = remember(
        selectedReaderFont.id,
        state.readerSettings.forceBoldText,
        state.readerSettings.forceItalicText,
        context,
    ) {
        loadNovelReaderTypeface(
            context = context,
            font = selectedReaderFont,
            forceBoldText = state.readerSettings.forceBoldText,
            forceItalicText = state.readerSettings.forceItalicText,
        )
    }
    val composeFontFamily = remember(selectedReaderFont.id, composeTypeface) {
        resolveNovelReaderComposeFontFamily(
            font = selectedReaderFont,
            typeface = composeTypeface,
        )
    }
    val chapterTitleTypeface = remember(
        context,
        state.readerSettings.forceBoldText,
        state.readerSettings.forceItalicText,
    ) {
        novelReaderBuiltInFonts.firstOrNull { it.id == "domine" }?.let { font ->
            loadNovelReaderTypeface(
                context = context,
                font = font,
                forceBoldText = state.readerSettings.forceBoldText,
                forceItalicText = state.readerSettings.forceItalicText,
            )
        }
    }
    val chapterTitleFontFamily = remember {
        novelReaderBuiltInFonts.firstOrNull { it.id == "domine" }?.fontResId?.let { FontFamily(Font(it)) }
    }
    val paragraphSpacing = remember(state.readerSettings.paragraphSpacing) {
        resolveParagraphSpacingDp(state.readerSettings.paragraphSpacing)
    }
    val initialNativeReaderIndex = remember(
        state.lastSavedIndex,
        state.lastSavedPageReaderProgress,
        state.contentBlocks.size,
    ) {
        resolveInitialNativeReaderIndex(
            nativeLastSavedIndex = state.lastSavedIndex,
            savedPageReaderProgress = state.lastSavedPageReaderProgress,
            itemCount = state.contentBlocks.size,
        )
    }
    val textListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialNativeReaderIndex
            .coerceIn(0, (state.contentBlocks.lastIndex).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = if (state.lastSavedPageReaderProgress != null) {
            0
        } else {
            state.lastSavedScrollOffsetPx.coerceAtLeast(0)
        },
    )

    // РџРѕР»СѓС‡Р°РµРј СЂР°Р·РјРµСЂС‹ system bars
    val view = LocalView.current
    val density = LocalDensity.current
    val rootInsets = ViewCompat.getRootWindowInsets(view)
    val statusBarHeight = rootInsets
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
        ?.top
        ?: rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
        ?: 0
    val navigationBarHeight = rootInsets
        ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        ?.bottom
        ?: rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
        ?: 0

    // AppBar height (~64dp + status bar).
    val appBarHeight = with(density) { (64.dp + statusBarHeight.toDp()).toPx().toInt() }
    // Р’С‹СЃРѕС‚Р° Bottom bar (~80dp + navigation bar)
    val bottomBarHeight = with(density) { (80.dp + navigationBarHeight.toDp()).toPx().toInt() }
    val statusBarTopPadding = with(density) { statusBarHeight.toDp() }
    val volumeScrollStepPx = with(density) { (configuration.screenHeightDp.dp * 0.25f).toPx() }
    val baseContentPadding = MaterialTheme.padding.small
    val contentPaddingPx = with(density) {
        resolveReaderContentPaddingPx(
            showReaderUi = showReaderUi,
            basePaddingPx = baseContentPadding.roundToPx(),
        ).toDp()
    }
    val ttsScrollTopPadding = resolveNovelReaderTtsScrollTopPadding(
        hasActiveTtsSession = state.ttsUiState.activeSession != null,
        statusBarTopPadding = statusBarTopPadding,
    )
    val scrollContentBlocks = remember(state.chapter.id, state.contentBlocks) {
        state.contentBlocks.takeIf { it.isNotEmpty() }
            ?: state.textBlocks.map { NovelReaderScreenModel.ContentBlock.Text(it) }
    }
    val showPageChapterTitle = state.readerSettings.showPageChapterTitle
    val pageReaderTextBlocks = remember(state.chapter.id, scrollContentBlocks, showPageChapterTitle) {
        stripPageReaderChapterTitleBlocks(
            textBlocks = scrollContentBlocks
                .mapIndexedNotNull { index, block ->
                    val text = (block as? NovelReaderScreenModel.ContentBlock.Text)?.text?.takeIf { it.isNotBlank() }
                        ?: return@mapIndexedNotNull null
                    PlainPageReaderTextBlock(
                        sourceBlockIndex = index,
                        text = text,
                    )
                },
            chapterTitle = resolvePageReaderChapterTitleForFiltering(
                showPageChapterTitle = showPageChapterTitle,
                chapterTitle = state.chapter.name,
            ),
        )
    }
    val richScrollBlocks = remember(state.chapter.id, state.richContentBlocks) {
        state.richContentBlocks
    }
    val pageReaderRichBlocks = remember(state.chapter.id, richScrollBlocks, showPageChapterTitle) {
        stripPageReaderChapterTitleRichBlocks(
            richBlocks = richScrollBlocks.withIndex().toList(),
            chapterTitle = resolvePageReaderChapterTitleForFiltering(
                showPageChapterTitle = showPageChapterTitle,
                chapterTitle = state.chapter.name,
            ),
        )
    }
    val shouldPaginatePageReader = shouldPaginateForPageReader(
        pageReaderEnabled = state.readerSettings.pageReader,
        contentBlocksCount = state.contentBlocks.size,
    )
    val pageReaderLayoutTextAlign = remember(
        state.readerSettings.textAlign,
        state.readerSettings.preserveSourceTextAlignInNative,
    ) {
        resolvePageReaderLayoutTextAlign(
            globalTextAlign = state.readerSettings.textAlign,
            preserveSourceTextAlignInNative = state.readerSettings.preserveSourceTextAlignInNative,
        )
    }
    val pageReaderPages: List<List<PlainPageSlice>> = remember(
        state.chapter.id,
        pageReaderTextBlocks,
        showPageChapterTitle,
        shouldPaginatePageReader,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        pageReaderLayoutTextAlign,
        composeTypeface,
        chapterTitleTypeface,
        pageViewportSize,
        contentPaddingPx,
        statusBarTopPadding,
    ) {
        if (!shouldPaginatePageReader || pageReaderTextBlocks.isEmpty()) {
            emptyList()
        } else {
            val screenWidthPx = pageViewportSize.width.takeIf { it > 0 }
                ?: with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = pageViewportSize.height.takeIf { it > 0 }
                ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
            val horizontalPaddingPx = with(density) { (state.readerSettings.margin.dp * 2).roundToPx() }
            val topPaddingPx = with(density) { (contentPaddingPx + statusBarTopPadding).roundToPx() }
            val bottomPaddingPx = with(density) { contentPaddingPx.roundToPx() }
            val bookBottomInsetPx = with(density) {
                resolveNovelPageReaderBookBottomInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val pageFitSafetyPx = with(density) {
                resolveNovelPageReaderPageFitSafetyInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val verticalPaddingPx = topPaddingPx +
                bottomPaddingPx +
                bookBottomInsetPx +
                pageFitSafetyPx +
                navigationBarHeight
            paginatePlainPageBlocks(
                textBlocks = pageReaderTextBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                textAlign = pageReaderLayoutTextAlign,
                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                chapterTitle = if (showPageChapterTitle) state.chapter.name else null,
            )
        }
    }
    val shouldPaginateRichForPageReader = shouldUseRichNativePageRenderer(
        richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
        pageReaderEnabled = shouldPaginatePageReader,
        bionicReadingEnabled = state.readerSettings.bionicReading,
        richContentBlocks = state.richContentBlocks,
        richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
    )
    val richPageReaderPagination = remember(
        state.chapter.id,
        pageReaderRichBlocks,
        shouldPaginateRichForPageReader,
        showPageChapterTitle,
        state.readerSettings.fontSize,
        state.readerSettings.lineHeight,
        state.readerSettings.margin,
        state.readerSettings.textAlign,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        pageReaderLayoutTextAlign,
        composeTypeface,
        chapterTitleTypeface,
        pageViewportSize,
        contentPaddingPx,
        statusBarTopPadding,
    ) {
        if (!shouldPaginateRichForPageReader) {
            MixedRichPagePagination(blockTexts = emptyList(), pages = emptyList())
        } else {
            val screenWidthPx = pageViewportSize.width.takeIf { it > 0 }
                ?: with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val screenHeightPx = pageViewportSize.height.takeIf { it > 0 }
                ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
            val horizontalPaddingPx = with(density) { (state.readerSettings.margin.dp * 2).roundToPx() }
            val topPaddingPx = with(density) { (contentPaddingPx + statusBarTopPadding).roundToPx() }
            val bottomPaddingPx = with(density) { contentPaddingPx.roundToPx() }
            val bookBottomInsetPx = with(density) {
                resolveNovelPageReaderBookBottomInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val pageFitSafetyPx = with(density) {
                resolveNovelPageReaderPageFitSafetyInset(
                    density = this,
                    fontSize = state.readerSettings.fontSize,
                    lineHeight = state.readerSettings.lineHeight,
                ).roundToPx()
            }
            val verticalPaddingPx = topPaddingPx +
                bottomPaddingPx +
                bookBottomInsetPx +
                pageFitSafetyPx +
                navigationBarHeight
            paginateMixedRichPageBlocks(
                richBlocks = pageReaderRichBlocks,
                paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
                widthPx = (screenWidthPx - horizontalPaddingPx).coerceAtLeast(1),
                heightPx = (screenHeightPx - verticalPaddingPx).coerceAtLeast(1),
                textSizePx = with(density) { state.readerSettings.fontSize.sp.toPx() },
                lineHeightMultiplier = state.readerSettings.lineHeight.coerceAtLeast(1f),
                typeface = composeTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                textAlign = pageReaderLayoutTextAlign,
                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                chapterTitle = if (showPageChapterTitle) state.chapter.name else null,
            )
        }
    }
    val richPageReaderBlockTexts = richPageReaderPagination.blockTexts
    val richPageReaderPages = richPageReaderPagination.pages
    val usePageReader = shouldPaginatePageReader &&
        (
            pageReaderPages.isNotEmpty() || richPageReaderPages.isNotEmpty()
            )
    val useRichPageReader = usePageReader && richPageReaderPages.isNotEmpty()
    val pageReaderCharacterCounts = remember(
        useRichPageReader,
        pageReaderPages,
        richPageReaderPages,
        pageReaderTextBlocks,
        richPageReaderBlockTexts,
    ) {
        if (useRichPageReader) {
            richPageReaderPages.map { page ->
                richPageReaderCharacterCount(page, richPageReaderBlockTexts)
            }
        } else {
            pageReaderPages.map { page ->
                plainPageReaderCharacterCount(page, pageReaderTextBlocks)
            }
        }
    }
    val pageReaderContentPages = remember(
        useRichPageReader,
        pageReaderPages,
        richPageReaderPages,
        pageReaderTextBlocks,
        richPageReaderBlockTexts,
        state.readerSettings.paragraphSpacing,
        state.readerSettings.forceParagraphIndent,
        showPageChapterTitle,
    ) {
        normalizePageReaderContentPages(
            useRichPageReader = useRichPageReader,
            plainPages = pageReaderPages,
            richPages = richPageReaderPages,
            plainTextBlocks = pageReaderTextBlocks,
            richBlockTexts = richPageReaderBlockTexts,
            paragraphSpacingPx = with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() },
            forceParagraphIndent = state.readerSettings.forceParagraphIndent,
            chapterTitle = if (showPageChapterTitle) state.chapter.name else null,
        )
    }
    val activePageTransitionStyle = remember(state.readerSettings.pageTransitionStyle) {
        resolveActivePageTransitionStyle(
            requestedStyle = state.readerSettings.pageTransitionStyle,
            pageTurnRendererSupported = true,
            isEInkMode = isEInkMode,
        )
    }
    val pageReaderRendererRoute = remember(usePageReader, activePageTransitionStyle) {
        resolvePageReaderRendererRoute(
            usePageReader = usePageReader,
            activeStyle = activePageTransitionStyle,
        )
    }
    val pageReaderItemsCount = pageReaderContentPages.size
    // The pager only needs an extra virtual page before/after the chapter while the intermediate
    // "next/previous chapter" placeholder is shown. Seamless chapter transitions hide that
    // placeholder, so the extra slots must not exist either: otherwise the edge page falls back to
    // clamped content and the last page of the chapter is rendered twice.
    val composePagerBoundaryPagesEnabled = !seamlessChapterTransitionEnabled
    val composePagerHasPreviousChapter = state.previousChapterId != null && composePagerBoundaryPagesEnabled
    val composePagerHasNextChapter = state.nextChapterId != null && composePagerBoundaryPagesEnabled
    val composePagerVirtualPageCount = remember(
        pageReaderItemsCount,
        composePagerHasPreviousChapter,
        composePagerHasNextChapter,
    ) {
        resolveComposePagerVirtualPageCount(
            contentPageCount = pageReaderItemsCount,
            hasPreviousChapter = composePagerHasPreviousChapter,
            hasNextChapter = composePagerHasNextChapter,
        )
    }
    val pageReaderChapterHandoffTarget = remember(state.chapter.id) {
        NovelReaderChapterHandoffPolicy.consumeInternalChapterHandoff()
    }
    val useRichNativeScroll = shouldUseRichNativeScrollRenderer(
        richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
        showWebView = showWebView,
        usePageReader = usePageReader,
        bionicReadingEnabled = state.readerSettings.bionicReading,
        richContentBlocks = richScrollBlocks,
        richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
    )
    val nativeScrollItemsCount = if (useRichNativeScroll) richScrollBlocks.size else scrollContentBlocks.size
    // Book mode streams the whole novel into one document, so the per-chapter content blocks stay
    // empty and readiness has to come from the book session instead. Otherwise auto-scroll and the
    // reader chrome wait forever for a chapter payload that book mode never loads.
    // Surface published by the mounted book renderer, see NovelBookScrollSurface. Declared here,
    // above auto-scroll readiness, because readiness has to know whether the book renderer is
    // mounted: book mode has no chapter WebView to ask.
    var bookScrollSurface by remember(state.novel.id) { mutableStateOf<NovelBookScrollSurface?>(null) }
    val autoScrollContentReady = when {
        // `webViewPageReadyForAutoScroll` is only ever set by the chapter WebView, which book mode
        // never mounts, so requiring it here kept readiness false forever and auto-scroll could
        // never evaluate the end of the document.
        state.bookMode.isEnabled -> state.bookMode.isReady
        showWebView -> webViewPageReadyForAutoScroll && scrollContentBlocks.isNotEmpty()
        else -> scrollContentBlocks.isNotEmpty() || richScrollBlocks.isNotEmpty()
    }
    val autoScrollHasRenderableItems = when {
        // Same reason: `webViewInstance` is the chapter WebView and stays null over a book. What is
        // renderable there is the book surface, or the native list that hosts the book sections.
        state.bookMode.isEnabled ->
            state.bookMode.sectionCount > 0 &&
                (bookScrollSurface != null || nativeScrollItemsCount > 0)
        showWebView -> webViewInstance != null
        usePageReader -> pageReaderItemsCount > 0
        else -> nativeScrollItemsCount > 0
    }
    val initialContentPage = resolveInitialPageReaderPage(
        savedPageReaderProgress = state.lastSavedPageReaderProgress,
        legacyLastSavedIndex = state.lastSavedIndex,
        pageCount = pageReaderItemsCount.coerceAtLeast(1),
        chapterHandoffTarget = pageReaderChapterHandoffTarget,
    )
    val initialPagerPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
        resolveComposePagerVirtualPageIndex(
            actualPageIndex = initialContentPage,
            hasPreviousChapter = composePagerHasPreviousChapter,
        )
    } else {
        initialContentPage
    }
    val pagerState = rememberPagerState(
        initialPage = initialPagerPage,
        pageCount = {
            if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                composePagerVirtualPageCount.coerceAtLeast(1)
            } else {
                pageReaderItemsCount.coerceAtLeast(1)
            }
        },
    )
    val pageReaderTtsNavigationAdapter = remember(
        pagerState,
        pageReaderRendererRoute,
        composePagerHasPreviousChapter,
    ) {
        PageReaderTtsNavigationAdapter(
            navigator = object : PageReaderTtsNavigator {
                override suspend fun scrollToPage(pageIndex: Int) {
                    val targetPage = if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        resolveComposePagerVirtualPageIndex(
                            actualPageIndex = pageIndex,
                            hasPreviousChapter = composePagerHasPreviousChapter,
                        )
                    } else {
                        pageIndex
                    }.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
                    pagerState.scrollToPage(targetPage)
                }
            },
        )
    }
    val nativeScrollTtsNavigationAdapter = remember(textListState) {
        NativeScrollTtsNavigationAdapter(
            navigator = object : NativeScrollTtsNavigator {
                override suspend fun scrollToBlock(blockIndex: Int, scrollOffsetPx: Int) {
                    textListState.scrollToItem(blockIndex, scrollOffsetPx)
                }
            },
        )
    }
    // Book mode: run the queued DOM work against the live document, then acknowledge it so the
    // screen model can queue the next window. Commands stay pending while the WebView is missing.
    // The reader document is loaded asynchronously and can be replaced, which wipes every seeded
    // placeholder, appended section, flow class and relocate listener. This counter tracks the loaded
    // document so all of that work is (re)applied against the document that is actually on screen.
    var bookModeDocumentGeneration by remember(state.novel.id) { mutableStateOf(0) }
    // Timestamp until which the document's own position reports are ignored, see
    // BOOK_MODE_SCROLL_RESTORE_GUARD_MS.
    val bookModeWebScrollGuard = remember(state.novel.id) { longArrayOf(0L) }
    LaunchedEffect(bookModeCommands, webViewInstance) {
        if (bookModeCommands.isEmpty()) return@LaunchedEffect
        val view = webViewInstance ?: return@LaunchedEffect
        if (!view.settings.javaScriptEnabled) return@LaunchedEffect
        val executedCommandIds = mutableListOf<Long>()
        for (command in bookModeCommands) {
            val script = when (command) {
                is NovelBookUiCommand.Append -> buildAppendBookSectionJavascript(
                    sectionIndex = command.sectionIndex,
                    sectionHtml = command.html,
                    keepScrollAnchored = command.keepScrollAnchored,
                )
                is NovelBookUiCommand.Prune -> buildPruneBookSectionJavascript(
                    sectionIndex = command.sectionIndex,
                )
                is NovelBookUiCommand.ScrollTo -> buildScrollToBookSectionJavascript(
                    sectionIndex = command.sectionIndex,
                    sectionFraction = command.sectionFraction,
                )
                // Seed the full spine as collapsed placeholders before any other DOM work, so a
                // resume in the middle of the book still produces a document that can be scrolled
                // back to the first chapter instead of starting at the resume section.
                is NovelBookUiCommand.Seed -> buildBookSkeletonJavascript(
                    sections = command.sections.map { seed ->
                        NovelBookSkeletonSection(
                            sectionIndex = seed.sectionIndex,
                            chapterId = seed.chapterId,
                        )
                    },
                )
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                view.post {
                    view.evaluateJavascript(script) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
            }
            if (command is NovelBookUiCommand.ScrollTo) {
                // The jump settles over the next frames; until then the document still reports the
                // old (or empty) position and must not be allowed to overwrite the book position.
                bookModeWebScrollGuard[0] = System.currentTimeMillis() + BOOK_MODE_SCROLL_RESTORE_GUARD_MS
            }
            executedCommandIds += command.id
        }
        if (executedCommandIds.isNotEmpty()) {
            onBookModeCommandsExecuted(executedCommandIds)
            view.post {
                view.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
            }
        }
    }
    // Book mode: the document PUSHES its reading position to the reader (relocate events), coalesced to
    // at most one per animation frame. The previous version polled the document every 400 ms forever,
    // which kept the JS thread and the progress-persistence pipeline permanently busy: that is what
    // produced the scroll jank, the endless logcat spam and the unresponsive reader chrome.
    val bookModeRelocateHandler: (NovelBookDocumentMetrics) -> Unit = remember(state.novel.id) {
        { metrics ->
            // Heights are layout information only; book progress stays in the spine's text domain.
            metrics.measuredSections().forEach { section ->
                onBookModeSectionMeasured(section.chapterId, section.heightPx)
            }
            // A document that only holds collapsed placeholders has no reading position yet: right
            // after a (re)load it reports the top of the book, and reporting that back replaced the
            // stored position with the first chapter.
            val hasRenderedSection = metrics.sections.any { !it.isPruned && it.heightPx > 0 }
            val restoring = System.currentTimeMillis() < bookModeWebScrollGuard[0]
            if (hasRenderedSection && !restoring) {
                metrics.currentSection()?.let { current ->
                    onBookModeScroll(current.index, metrics.fractionInside(current))
                }
            }
        }
    }
    LaunchedEffect(state.bookMode.isEnabled, webViewInstance, bookModeDocumentGeneration) {
        if (!state.bookMode.isEnabled) return@LaunchedEffect
        val view = webViewInstance ?: return@LaunchedEffect
        if (!view.settings.javaScriptEnabled) return@LaunchedEffect
        view.post {
            view.registerBookRelocateBridge(bookModeRelocateHandler)
            view.installBookRelocateBridgeScript()
        }
    }
    // A freshly loaded document is empty, so the book has to be put back into it: skeleton, resident
    // sections and reading position.
    LaunchedEffect(state.bookMode.isEnabled, bookModeDocumentGeneration) {
        if (!state.bookMode.isEnabled) return@LaunchedEffect
        if (bookModeDocumentGeneration <= 0) return@LaunchedEffect
        // The fresh document is empty and scrolled to the top. Hold its position reports back until
        // the book has been seeded and the reading position re-applied.
        bookModeWebScrollGuard[0] = System.currentTimeMillis() + BOOK_MODE_SCROLL_RESTORE_GUARD_MS
        onBookModeDocumentReady()
        webViewInstance?.post {
            webViewInstance?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
        }
    }
    // Book mode is renderer independent: the reader settings pick the book renderer, and the WebView
    // adapter only has to switch the document's flow. "Pages" therefore works in book mode too, over
    // the same spine, sections and progress as the scrolled flow.
    val bookRenderer = remember(
        state.readerSettings.pageReader,
        state.readerSettings.richNativeRendererExperimental,
        state.readerSettings.bionicReading,
        state.richContentUnsupportedFeaturesDetected,
    ) {
        resolveNovelBookRenderer(
            pageReaderEnabled = state.readerSettings.pageReader,
            richNativeRendererExperimentalEnabled = state.readerSettings.richNativeRendererExperimental,
            bionicReadingEnabled = state.readerSettings.bionicReading,
            customStylesPresent = state.readerSettings.customCSS.isNotBlank() ||
                state.readerSettings.customJS.isNotBlank(),
            richContentUnsupportedFeaturesDetected = state.richContentUnsupportedFeaturesDetected,
        )
    }
    val bookView = remember(webViewInstance) {
        webViewInstance?.let { WebViewNovelBookView(it) }
    }
    // Keyed on the renderer only. Keying this on `bookModeCommands` re-ran the flow switch (a full
    // document reflow) for every append/prune batch, which is what made scrolling stutter.
    LaunchedEffect(state.bookMode.isEnabled, bookView, bookRenderer, bookModeDocumentGeneration) {
        if (!state.bookMode.isEnabled) return@LaunchedEffect
        val view = bookView ?: return@LaunchedEffect
        view.setFlow(bookRenderer.flow)
    }
    // Native renderer side of book mode. Both renderers consume the same command stream, so the
    // native list folds appends and prunes into its resident sections instead of running JavaScript.
    val useNativeBookScroll = state.bookMode.isEnabled && !bookRenderer.usesWebView
    // `bookScrollSurface` is declared next to auto-scroll readiness above, which needs it.
    var bookModeNativeSections by remember(state.novel.id) {
        mutableStateOf<NovelBookNativeSections>(emptyList())
    }
    val bookModeNativeEntries = remember(bookModeNativeSections, state.bookMode.failedSectionIndices) {
        buildNovelBookNativeEntries(
            sections = bookModeNativeSections,
            failedSectionIndices = state.bookMode.failedSectionIndices,
        )
    }
    // [0] = id of the scroll command that was already applied, [1] = when the list was last moved
    // programmatically. Kept in an array so updating it never triggers a recomposition of the reader.
    val bookModeScrollGuard = remember(state.novel.id) { longArrayOf(0L, 0L) }
    LaunchedEffect(useNativeBookScroll, bookModeCommands) {
        if (!useNativeBookScroll) return@LaunchedEffect
        val commands = bookModeCommands
        if (commands.isEmpty()) return@LaunchedEffect
        val nextSections = applyNovelBookCommandsToNativeSections(
            sections = bookModeNativeSections,
            commands = commands,
            parseSection = ::parseNovelBookNativeSection,
            precompiledSection = nativeBookBlocksForSection,
        )
        bookModeNativeSections = nextSections
        val scrollTarget = resolveNovelBookNativeScrollTarget(
            entries = buildNovelBookNativeEntries(
                sections = nextSections,
                failedSectionIndices = state.bookMode.failedSectionIndices,
            ),
            commands = commands,
            lastAppliedCommandId = bookModeScrollGuard[0],
        )
        if (scrollTarget != null) {
            bookModeScrollGuard[0] = scrollTarget.commandId
            bookModeScrollGuard[1] = System.currentTimeMillis()
            textListState.scrollToItem(scrollTarget.itemIndex)
            val itemHeight = textListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == scrollTarget.itemIndex }
                ?.size
                ?: 0
            val offsetPx = (itemHeight * scrollTarget.fraction).toInt()
            if (offsetPx > 0) {
                textListState.scrollToItem(scrollTarget.itemIndex, offsetPx)
            }
            bookModeScrollGuard[1] = System.currentTimeMillis()
        }
        onBookModeCommandsExecuted(commands.map { it.id })
    }
    // Position reporting for the native list, mirroring what the WebView relocate bridge sends.
    LaunchedEffect(
        useNativeBookScroll,
        textListState.firstVisibleItemIndex,
        textListState.firstVisibleItemScrollOffset,
        bookModeNativeEntries,
    ) {
        if (!useNativeBookScroll) return@LaunchedEffect
        // Ignore the frames a programmatic jump produces; reporting them moved the book's position
        // to the intermediate layout and pulled the reader back.
        if (System.currentTimeMillis() - bookModeScrollGuard[1] < BOOK_MODE_NATIVE_SCROLL_GUARD_MS) {
            return@LaunchedEffect
        }
        val viewportItems = textListState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            val sectionIndex = bookModeNativeEntries.getOrNull(item.index)?.sectionIndex
                ?: return@mapNotNull null
            NovelBookNativeViewportItem(
                sectionIndex = sectionIndex,
                offsetPx = item.offset,
                heightPx = item.size,
            )
        }
        val location = resolveNovelBookNativeRelocate(viewportItems) ?: return@LaunchedEffect
        onBookModeScroll(location.sectionIndex, location.sectionFraction)
    }
    // Appending, pruning or jumping to a section changes the layout, so ask the document for a single
    // relocate event once the queued DOM work settled. Installing again is a no-op when already done.
    LaunchedEffect(state.bookMode.isEnabled, webViewInstance, bookModeCommands) {
        if (!state.bookMode.isEnabled) return@LaunchedEffect
        val view = webViewInstance ?: return@LaunchedEffect
        if (!view.settings.javaScriptEnabled) return@LaunchedEffect
        kotlinx.coroutines.delay(BOOK_MODE_RELOCATE_SETTLE_DELAY_MS)
        view.post {
            view.installBookRelocateBridgeScript()
            view.requestBookRelocate()
        }
    }
    val webViewTtsNavigationAdapter = remember(state.chapter.id, scrollContentBlocks.size) {
        WebViewTtsNavigationAdapter(
            navigator = object : WebViewTtsNavigator {
                override suspend fun evaluateJavascript(script: String): String? {
                    val view = webViewInstance ?: return null
                    if (!view.settings.javaScriptEnabled) return null
                    return suspendCancellableCoroutine { continuation ->
                        view.post {
                            view.evaluateJavascript(script) { result ->
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            }
                        }
                    }
                }
            },
            totalBlocks = scrollContentBlocks.size.coerceAtLeast(1),
        )
    }
    // Book mode renders its own document, so neither the WebView adapter (chapter WebView) nor the
    // native adapter (per-chapter lazy list) can follow the voice there.
    val bookTtsNavigationAdapter = remember(state.novel.id) {
        BookTtsNavigationAdapter(
            surface = { bookScrollSurface },
            sectionIndexForSpeech = { null },
        )
    }
    SideEffect {
        pageReaderTtsNavigationAdapter.hashCode()
        nativeScrollTtsNavigationAdapter.hashCode()
        webViewTtsNavigationAdapter.hashCode()
        bookTtsNavigationAdapter.hashCode()
    }
    LaunchedEffect(
        state.chapter.id,
        pageReaderRendererRoute,
        pageReaderItemsCount,
        composePagerHasPreviousChapter,
        composePagerHasNextChapter,
        initialPagerPage,
    ) {
        if (pagerState.currentPage != initialPagerPage) {
            pagerState.scrollToPage(initialPagerPage)
        }
    }
    // A seamless in-place chapter switch reuses this composition, so the scrolled reader keeps the
    // lazy list state of the previous chapter and stays near its end instead of jumping to the new
    // chapter's saved position. The pager route above resets itself the same way.
    val appliedNativeScrollRestoreChapterId = remember { longArrayOf(state.chapter.id) }
    LaunchedEffect(state.chapter.id, nativeScrollItemsCount) {
        if (appliedNativeScrollRestoreChapterId[0] == state.chapter.id) return@LaunchedEffect
        // Book mode is one continuous document: the current chapter changes while reading and the
        // list position must never be reset under the reader.
        if (state.bookMode.isEnabled) {
            appliedNativeScrollRestoreChapterId[0] = state.chapter.id
            return@LaunchedEffect
        }
        if (nativeScrollItemsCount <= 0) return@LaunchedEffect
        appliedNativeScrollRestoreChapterId[0] = state.chapter.id
        textListState.scrollToItem(
            initialNativeReaderIndex.coerceIn(0, (nativeScrollItemsCount - 1).coerceAtLeast(0)),
            if (state.lastSavedPageReaderProgress != null) {
                0
            } else {
                state.lastSavedScrollOffsetPx.coerceAtLeast(0)
            },
        )
    }
    var pageTurnCurrentPage by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableIntStateOf(initialPagerPage)
    }
    var pageTurnRequestedPage by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableIntStateOf(-1)
    }
    var pageTurnChapterNavigationRequest by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableStateOf<PageTurnChapterNavigationRequest?>(null)
    }
    var pageTurnChapterNavigationRequestToken by remember(pageReaderRendererRoute, state.chapter.id) {
        mutableStateOf(0L)
    }
    val pageReaderProgressPageIndex by remember(
        pageReaderRendererRoute,
        pagerState.currentPage,
        pageTurnCurrentPage,
        composePagerHasPreviousChapter,
        pageReaderItemsCount,
    ) {
        derivedStateOf {
            resolvePageReaderCurrentPage(
                pageReaderRendererRoute = pageReaderRendererRoute,
                pagerCurrentPage = pagerState.currentPage,
                pageTurnCurrentPage = pageTurnCurrentPage,
                composePagerContentPageCount = pageReaderItemsCount,
                composePagerHasPreviousChapter = composePagerHasPreviousChapter,
                pageTurnContentPageCount = pageReaderItemsCount,
                pageTurnHasPreviousChapter = composePagerHasPreviousChapter,
            )
        }
    }
    val latestPageReaderProgressPageIndex by rememberUpdatedState(pageReaderProgressPageIndex)
    val latestPageReaderItemsCount by rememberUpdatedState(pageReaderItemsCount)
    val pageReaderTtsPosition = remember(
        usePageReader,
        useRichPageReader,
        pageReaderProgressPageIndex,
        pageReaderPages,
        richPageReaderPages,
        pageReaderTextBlocks,
        richPageReaderBlockTexts,
    ) {
        if (!usePageReader) {
            null
        } else {
            val blockTexts = if (useRichPageReader) {
                buildSourceIndexedPageReaderTextList(
                    richPageReaderBlockTexts.map {
                        PlainPageReaderTextBlock(
                            sourceBlockIndex = it.sourceBlockIndex,
                            text = it.text.text,
                        )
                    },
                )
            } else {
                buildSourceIndexedPageReaderTextList(pageReaderTextBlocks)
            }
            val pages = if (useRichPageReader) {
                richPageReaderPages.map { page ->
                    page.filterIsInstance<RichPageSlice.Text>().map { slice ->
                        NovelTtsPageSlice(
                            blockIndex = slice.blockIndex,
                            start = slice.range.start,
                            endExclusive = slice.range.endExclusive,
                        )
                    }
                }
            } else {
                pageReaderPages.map { page ->
                    page.map { slice ->
                        NovelTtsPageSlice(
                            blockIndex = slice.blockIndex,
                            start = slice.range.start,
                            endExclusive = slice.range.endExclusive,
                        )
                    }
                }
            }
            NovelTtsPageReaderPosition(
                pageIndex = pageReaderProgressPageIndex,
                blockTexts = blockTexts,
                pages = pages,
            )
        }
    }
    val currentTtsBlockIndex by remember(
        showWebView,
        usePageReader,
        pageReaderProgressPageIndex,
        pageReaderPages,
        richPageReaderPages,
        textListState.firstVisibleItemIndex,
        scrollContentBlocks.size,
        richScrollBlocks.size,
        webProgressPercent,
    ) {
        derivedStateOf {
            when {
                showWebView -> {
                    val targetBlockCount = scrollContentBlocks.size.coerceAtLeast(1)
                    (((webProgressPercent.coerceIn(0, 100) / 100f) * (targetBlockCount - 1)).roundToInt())
                        .coerceIn(0, targetBlockCount - 1)
                }
                usePageReader -> {
                    if (useRichPageReader) {
                        richPageReaderPages
                            .getOrNull(pageReaderProgressPageIndex)
                            ?.filterIsInstance<RichPageSlice.Text>()
                            ?.firstOrNull()
                            ?.blockIndex
                            ?: 0
                    } else {
                        pageReaderPages
                            .getOrNull(pageReaderProgressPageIndex)
                            ?.firstOrNull()
                            ?.blockIndex
                            ?: 0
                    }
                }
                useRichNativeScroll -> textListState.firstVisibleItemIndex.coerceIn(
                    0,
                    richScrollBlocks.lastIndex.coerceAtLeast(0),
                )
                else -> textListState.firstVisibleItemIndex.coerceIn(0, scrollContentBlocks.lastIndex.coerceAtLeast(0))
            }
        }
    }
    val currentTtsStartRequest by remember(
        currentTtsBlockIndex,
        pageReaderTtsPosition,
        usePageReader,
    ) {
        derivedStateOf {
            NovelTtsPlaybackStartRequest(
                fallbackBlockIndex = currentTtsBlockIndex,
                pageReaderPosition = if (usePageReader) pageReaderTtsPosition else null,
            )
        }
    }
    LaunchedEffect(
        state.chapter.id,
        state.nextChapterId,
        state.ttsUiState.pendingChapterHandoffId,
        state.ttsUiState.activeSession?.chapterId,
    ) {
        val targetChapterId = state.ttsUiState.pendingChapterHandoffId
            ?: resolveTtsAutoAdvancedChapterNavigationTarget(
                currentChapterId = state.chapter.id,
                activeTtsChapterId = state.ttsUiState.activeSession?.chapterId,
                nextChapterId = state.nextChapterId,
            )
            ?: return@LaunchedEffect
        if (requestedTtsChapterSyncTarget == targetChapterId) return@LaunchedEffect
        requestedTtsChapterSyncTarget = targetChapterId
        NovelReaderTtsChapterHandoffPolicy.markPendingRestore(targetChapterId)
        webViewInstance?.clearFocus()
        onOpenNextChapter?.invoke(targetChapterId)
    }
    val activePageReaderTtsAnchors = remember(
        usePageReader,
        pageReaderTtsPosition,
        state.ttsUiState.activeSession?.model,
    ) {
        val sessionModel = state.ttsUiState.activeSession?.model
        val position = pageReaderTtsPosition
        if (!usePageReader || sessionModel == null || position == null) {
            emptyMap()
        } else {
            resolvePlainPageReaderTtsAnchors(
                textBlocks = position.blockTexts,
                pages = position.pages,
                chapterModel = sessionModel,
            )
        }
    }
    LaunchedEffect(
        state.ttsUiState.activeSession?.utterance?.id,
        state.readerSettings.ttsFollowAlong,
        showWebView,
        usePageReader,
        pageReaderProgressPageIndex,
        activePageReaderTtsAnchors,
        state.bookMode.isEnabled,
        bookScrollSurface,
    ) {
        if (!state.readerSettings.ttsFollowAlong) return@LaunchedEffect
        val session = state.ttsUiState.activeSession ?: return@LaunchedEffect
        val segment = session.model.findSegmentForUtterance(session.utterance.id) ?: return@LaunchedEffect
        pendingProgrammaticTtsBlockIndex = segment.sourceBlockIndex
        suppressManualTtsPauseUntilMs = SystemClock.elapsedRealtime() + 1_500L
        when {
            // Checked first: over a book `showWebView` is false and `usePageReader` is irrelevant,
            // so follow-along used to fall into the native branch and scroll an empty chapter list.
            state.bookMode.isEnabled -> bookTtsNavigationAdapter.syncToSegment(segment)
            showWebView -> webViewTtsNavigationAdapter.syncToSegment(segment)
            usePageReader -> {
                val anchor = activePageReaderTtsAnchors[session.utterance.id]
                val targetPage = when {
                    anchor == null -> segment.pageCandidates.firstOrNull()
                    anchor.pageCandidates.contains(pageReaderProgressPageIndex) -> pageReaderProgressPageIndex
                    else -> anchor.pageIndex
                } ?: return@LaunchedEffect
                pageReaderTtsNavigationAdapter.restorePosition(
                    NovelTtsNavigationAnchor(pageIndex = targetPage),
                )
            }
            else -> nativeScrollTtsNavigationAdapter.syncToSegment(segment)
        }
    }
    LaunchedEffect(currentTtsBlockIndex, pendingProgrammaticTtsBlockIndex, suppressManualTtsPauseUntilMs) {
        val pendingBlockIndex = pendingProgrammaticTtsBlockIndex ?: return@LaunchedEffect
        if (currentTtsBlockIndex == pendingBlockIndex ||
            SystemClock.elapsedRealtime() >= suppressManualTtsPauseUntilMs
        ) {
            pendingProgrammaticTtsBlockIndex = null
            suppressManualTtsPauseUntilMs = 0L
        }
    }
    val ttsHighlightState = remember(
        usePageReader,
        pageReaderProgressPageIndex,
        activePageReaderTtsAnchors,
        state.ttsUiState.activeSession?.utterance?.id,
        state.ttsUiState.activeSourceBlockIndex,
        state.ttsUiState.activeUtteranceText,
        state.ttsUiState.activeWordRange,
        state.ttsUiState.activeHighlightMode,
    ) {
        val activeUtterance = state.ttsUiState.activeSession?.utterance
        val activePageAnchor = if (usePageReader) {
            activeUtterance?.id?.let(activePageReaderTtsAnchors::get)
        } else {
            null
        }
        NovelReaderTtsHighlightState(
            sourceBlockIndex = state.ttsUiState.activeSourceBlockIndex,
            utteranceText = state.ttsUiState.activeUtteranceText,
            wordRange = state.ttsUiState.activeWordRange,
            pageIndex = activePageAnchor?.pageCandidates
                ?.firstOrNull { it == pageReaderProgressPageIndex }
                ?: activePageAnchor?.pageIndex,
            blockTextStart = activePageAnchor?.blockTextStart ?: activeUtterance?.blockTextStart,
            blockTextEndExclusive = activePageAnchor?.blockTextEndExclusive ?: activeUtterance?.blockTextEndExclusive,
            mode = state.ttsUiState.activeHighlightMode,
        )
    }
    val ttsHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val readingProgressPercent by remember(
        showWebView,
        webProgressPercent,
        nativeScrollItemsCount,
        pageReaderItemsCount,
        pageReaderProgressPageIndex,
        textListState.firstVisibleItemIndex,
        textListState.canScrollForward,
        usePageReader,
        state.bookMode.isEnabled,
        state.bookMode.bookProgressFraction,
    ) {
        derivedStateOf {
            when {
                // In book mode the reader never leaves the book, so progress, "time to end" and the
                // word counter all describe the whole novel instead of the section under the viewport.
                state.bookMode.isEnabled -> {
                    (state.bookMode.bookProgressFraction * 100f).roundToInt().coerceIn(0, 100)
                }
                showWebView -> webProgressPercent
                usePageReader -> {
                    resolvePageReaderReadingProgressPercent(
                        pageIndex = pageReaderProgressPageIndex,
                        pageCount = pageReaderItemsCount,
                    )
                }
                nativeScrollItemsCount <= 0 -> 0
                !textListState.canScrollForward -> 100
                else -> {
                    (((textListState.firstVisibleItemIndex + 1).toFloat() / nativeScrollItemsCount.toFloat()) * 100f)
                        .roundToInt()
                        .coerceIn(0, 100)
                }
            }
        }
    }
    val totalWords = remember(state.chapter.id, pageReaderTextBlocks) {
        countNovelWords(pageReaderTextBlocks.map { it.text })
    }
    val readWords by remember(totalWords, readingProgressPercent) {
        derivedStateOf {
            estimateNovelReadWords(
                totalWords = totalWords,
                readingProgressPercent = readingProgressPercent,
            )
        }
    }
    LaunchedEffect(
        state.ttsUiState.isPlaying,
        state.readerSettings.ttsPauseOnManualNavigation,
        usePageReader,
        currentTtsBlockIndex,
        pageReaderProgressPageIndex,
        state.ttsUiState.activeSourceBlockIndex,
        state.ttsUiState.activeSession?.utterance?.id,
        activePageReaderTtsAnchors,
    ) {
        val activePageCandidates = if (usePageReader) {
            state.ttsUiState.activeSession
                ?.utterance
                ?.id
                ?.let(activePageReaderTtsAnchors::get)
                ?.pageCandidates
                ?.toSet()
        } else {
            null
        }
        val shouldPauseForManualNavigation = resolveShouldPauseTtsForManualNavigation(
            isPlaying = state.ttsUiState.isPlaying,
            pauseOnManualNavigation = state.readerSettings.ttsPauseOnManualNavigation,
            nowMs = SystemClock.elapsedRealtime(),
            suppressUntilMs = suppressManualTtsPauseUntilMs,
            usePageReader = usePageReader,
            currentBlockIndex = currentTtsBlockIndex,
            activeSourceBlockIndex = state.ttsUiState.activeSourceBlockIndex,
            currentPageIndex = if (usePageReader) pageReaderProgressPageIndex else null,
            activePageCandidates = activePageCandidates,
        )
        if (shouldPauseForManualNavigation) {
            onPauseTtsForManualNavigation(currentTtsStartRequest)
        }
    }
    var readingPaceState by remember(state.chapter.id) {
        mutableStateOf(NovelReaderReadingPaceState())
    }
    LaunchedEffect(state.chapter.id, readingProgressPercent) {
        readingPaceState = updateNovelReaderReadingPace(
            paceState = readingPaceState,
            readingProgressPercent = readingProgressPercent,
            timestampMs = SystemClock.elapsedRealtime(),
        )
    }
    val remainingMinutes = remember(readingPaceState, readingProgressPercent) {
        estimateNovelReaderRemainingMinutes(
            paceState = readingPaceState,
            readingProgressPercent = readingProgressPercent,
        )
    }
    val showBottomInfoOverlay = shouldShowBottomInfoOverlay(
        showReaderUi = showReaderUi,
        showBatteryAndTime = state.readerSettings.showBatteryAndTime,
        showKindleInfoBlock = state.readerSettings.showKindleInfoBlock,
        showTimeToEnd = state.readerSettings.showTimeToEnd,
        showWordCount = state.readerSettings.showWordCount,
    )
    val minVerticalChapterSwipeDistancePx = with(density) { 120.dp.toPx() }
    val verticalChapterSwipeHorizontalTolerancePx = with(density) { 20.dp.toPx() }
    val minVerticalChapterSwipeHoldDurationMillis = 180L

    LaunchedEffect(state.chapter.id) {
        if (state.readerSettings.autoScroll) {
            persistAutoScrollEnabledPreference(enabled = true)
        }
    }

    // РЈРїСЂР°РІР»РµРЅРёРµ System UI РґР»СЏ fullscreen СЂРµР¶РёРјР°
    LaunchedEffect(state.chapter.id, usePageReader) {
        onSetShowReaderUi(
            resolveReaderUiAfterChapterChange(
                currentShowReaderUi = showReaderUi,
                usePageReader = usePageReader,
            ),
        )
    }

    // Volume Buttons Handler
    val coroutineScope = rememberCoroutineScope()
    val latestShowReaderUi by rememberUpdatedState(showReaderUi)
    val latestTapToScrollEnabled by rememberUpdatedState(state.readerSettings.tapToScroll)
    val bookFlipPageAnimationDurationMillis = resolveBookFlipPageAnimationDurationMillis(
        transitionStyle = activePageTransitionStyle,
        animationSpeed = state.readerSettings.bookFlipAnimationSpeed,
    )

    fun requestPageTurnChapterNavigation(direction: PageTurnChapterNavigationDirection) {
        pageTurnChapterNavigationRequestToken += 1L
        pageTurnChapterNavigationRequest = PageTurnChapterNavigationRequest(
            direction = direction,
            token = pageTurnChapterNavigationRequestToken,
        )
    }

    fun openPreviousChapterFromReader() {
        val chapterId = state.previousChapterId ?: return
        NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
            NovelReaderPageReaderHandoffTarget.END,
        )
        // A seamless in-place chapter switch can detach the reader WebView (renderer may change
        // between chapters). If the WebView still holds view focus when it is detached, ViewGroup
        // restarts a focus search from the window root while Compose is applying the composition,
        // which synchronously remeasures the lazy layout and disposes subcompositions mid-pass
        // ("Cannot start a writer when another writer is pending"). Dropping focus first avoids it.
        webViewInstance?.clearFocus()
        onOpenPreviousChapter?.invoke(chapterId)
    }

    // True only on the last section of the spine: the single place where auto-scroll over a book is
    // really out of content.
    fun isAtEndOfBook(): Boolean {
        val sectionCount = state.bookMode.sectionCount
        return sectionCount <= 0 || state.bookMode.currentSectionIndex >= sectionCount - 1
    }

    fun openNextChapterFromReader() {
        val chapterId = state.nextChapterId ?: return
        NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
            NovelReaderPageReaderHandoffTarget.START,
        )
        webViewInstance?.clearFocus()
        onOpenNextChapter?.invoke(chapterId)
    }

    fun handleAutoScrollChapterEnd() {
        if (state.bookMode.isEnabled) {
            // A book has no chapter boundary to hand off at: the spine continues inside the same
            // document and the next section is stitched in on demand. Treating the end of the
            // resident window as the end of a chapter is what stopped auto-scroll mid-book (or, with
            // continuous reading, kicked the reader out into the next chapter).
            if (!isAtEndOfBook()) {
                autoScrollEndStableFrames = 0
                autoScrollEndDwellActive = false
                return
            }
            autoScrollEnabled = false
            autoScrollEndStableFrames = 0
            autoScrollEndDwellActive = false
            onCancelAutoScrollHandoff()
            return
        }
        val nextChapterId = state.nextChapterId
        val behavior = state.readerSettings.autoScrollChapterEndBehavior
        if (!shouldAutoScrollAdvanceToNextChapter(behavior, nextChapterId != null) || nextChapterId == null) {
            autoScrollEnabled = false
            autoScrollEndStableFrames = 0
            autoScrollEndDwellActive = false
            onCancelAutoScrollHandoff()
            return
        }
        if (shouldAutoScrollContinueAcrossChapters(behavior)) {
            onPrepareAutoScrollHandoff(nextChapterId, autoScrollSpeed)
        } else {
            onCancelAutoScrollHandoff()
        }
        autoScrollEnabled = false
        autoScrollEndStableFrames = 0
        autoScrollEndDwellActive = false
        openNextChapterFromReader()
    }

    suspend fun handleAutoScrollStableChapterEndAfterDwell() {
        if (state.bookMode.isEnabled && !isAtEndOfBook()) {
            // Not the end of anything the reader should pause at - only the end of the sections that
            // are currently resident.
            autoScrollEndStableFrames = 0
            return
        }
        val behavior = state.readerSettings.autoScrollChapterEndBehavior
        if (behavior == NovelAutoScrollChapterEndBehavior.StopAtEnd) {
            autoScrollEnabled = false
            autoScrollEndStableFrames = 0
            autoScrollEndDwellActive = false
            onCancelAutoScrollHandoff()
            return
        }

        autoScrollEndStableFrames = 0
        val endPauseMs = state.readerSettings.autoScrollEndPauseMs
        val totalSeconds = ((endPauseMs + 999L) / 1000L).toInt()
        autoScrollEndDwellRemainingSeconds = totalSeconds
        autoScrollEndDwellActive = true

        for (sec in totalSeconds downTo 1) {
            autoScrollEndDwellRemainingSeconds = sec
            delay(1000L)
            if (!autoScrollEnabled || showReaderUi || !autoScrollEndDwellActive) return
        }

        autoScrollEndDwellRemainingSeconds = 0
        autoScrollEndDwellActive = false
        handleAutoScrollChapterEnd()
    }

    suspend fun moveBackwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
        if (state.bookMode.isEnabled) {
            // The book is one continuous document, so stepping backwards never leaves it and chapter
            // navigation must not kick in. The book view knows whether a step is a page (paginated
            // flow) or a viewport of scroll.
            val surface = bookScrollSurface
            if (surface != null) {
                if (surface.isPaginated()) {
                    surface.step(forward = false)
                } else {
                    surface.scrollBy(-volumeScrollStepPx.roundToInt())
                }
            } else {
                bookView?.previous(activePageTransitionStyle.name)
            }
            return
        }
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(-1)) {
                webView.scrollBy(0, -volumeScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                openPreviousChapterFromReader()
            }
        } else if (usePageReader) {
            if (
                pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER &&
                activePageTransitionStyle == NovelPageTransitionStyle.CURL
            ) {
                requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.PREVIOUS)
            } else {
                val currentPage = pageReaderProgressPageIndex
                val currentVirtualPage = resolveComposePagerVirtualPageIndex(
                    actualPageIndex = currentPage,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                )
                if (currentVirtualPage > 0) {
                    val targetVirtualPage = currentVirtualPage - 1
                    pageTurnCurrentPage = resolveComposePagerActualPageIndex(
                        currentPage = targetVirtualPage,
                        contentPageCount = pageReaderItemsCount,
                        hasPreviousChapter = composePagerHasPreviousChapter,
                    )
                    if (pageAnimationDurationMillis != null) {
                        pagerState.animateScrollToPage(
                            targetVirtualPage,
                            animationSpec = tween(durationMillis = pageAnimationDurationMillis),
                        )
                    } else {
                        pagerState.animateScrollToPage(targetVirtualPage)
                    }
                } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
                    openPreviousChapterFromReader()
                }
            }
        } else if (textListState.canScrollBackward) {
            textListState.scrollBy(-volumeScrollStepPx)
        } else if (state.readerSettings.swipeToPrevChapter && state.previousChapterId != null) {
            openPreviousChapterFromReader()
        }
    }

    suspend fun moveForwardByReaderActionWithAnimation(pageAnimationDurationMillis: Int?) {
        if (state.bookMode.isEnabled) {
            val surface = bookScrollSurface
            if (surface != null) {
                if (surface.isPaginated()) {
                    surface.step(forward = true)
                } else {
                    surface.scrollBy(volumeScrollStepPx.roundToInt())
                }
            } else {
                bookView?.next(activePageTransitionStyle.name)
            }
            return
        }
        if (showWebView) {
            val webView = webViewInstance
            if (webView != null && webView.canScrollVertically(1)) {
                webView.scrollBy(0, volumeScrollStepPx.roundToInt())
            } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                openNextChapterFromReader()
            }
        } else if (usePageReader) {
            if (
                pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER &&
                activePageTransitionStyle == NovelPageTransitionStyle.CURL
            ) {
                requestPageTurnChapterNavigation(PageTurnChapterNavigationDirection.NEXT)
            } else {
                val currentPage = pageReaderProgressPageIndex
                val currentVirtualPage = resolveComposePagerVirtualPageIndex(
                    actualPageIndex = currentPage,
                    hasPreviousChapter = composePagerHasPreviousChapter,
                )
                val virtualLastPage = composePagerVirtualPageCount - 1
                if (currentVirtualPage < virtualLastPage) {
                    val targetVirtualPage = currentVirtualPage + 1
                    pageTurnCurrentPage = resolveComposePagerActualPageIndex(
                        currentPage = targetVirtualPage,
                        contentPageCount = pageReaderItemsCount,
                        hasPreviousChapter = composePagerHasPreviousChapter,
                    )
                    if (pageAnimationDurationMillis != null) {
                        pagerState.animateScrollToPage(
                            targetVirtualPage,
                            animationSpec = tween(durationMillis = pageAnimationDurationMillis),
                        )
                    } else {
                        pagerState.animateScrollToPage(targetVirtualPage)
                    }
                } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
                    openNextChapterFromReader()
                }
            }
        } else if (textListState.canScrollForward) {
            textListState.scrollBy(volumeScrollStepPx)
        } else if (state.readerSettings.swipeToNextChapter && state.nextChapterId != null) {
            openNextChapterFromReader()
        }
    }

    suspend fun moveBackwardByReaderAction() {
        moveBackwardByReaderActionWithAnimation(bookFlipPageAnimationDurationMillis)
    }

    suspend fun moveForwardByReaderAction() {
        moveForwardByReaderActionWithAnimation(bookFlipPageAnimationDurationMillis)
    }

    val latestCustomTapZonesEnabled by rememberUpdatedState(state.readerSettings.customTapZones)
    val latestTapZoneActions by rememberUpdatedState(
        parseNovelReaderTapZoneActions(state.readerSettings.tapZoneActions),
    )
    val latestReaderShortTapHandler by rememberUpdatedState<(Float, Float, Float, Float) -> Unit> {
            tapX,
            tapY,
            width,
            height,
        ->
        dispatchConfiguredReaderTapAction(
            tapX = tapX,
            tapY = tapY,
            width = width,
            height = height,
            customTapZonesEnabled = latestCustomTapZonesEnabled,
            tapZoneActions = latestTapZoneActions,
            tapToScrollEnabled = latestTapToScrollEnabled,
            onToggleUi = { onSetShowReaderUi(!latestShowReaderUi) },
            onBackward = { coroutineScope.launch { moveBackwardByReaderAction() } },
            onForward = { coroutineScope.launch { moveForwardByReaderAction() } },
            onNextChapter = { openNextChapterFromReader() },
            onPrevChapter = { openPreviousChapterFromReader() },
        )
    }

    fun handleVolumeKey(event: KeyEvent): Boolean {
        if (!state.readerSettings.useVolumeButtons) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) return true
        if (event.action != KeyEvent.ACTION_UP) return false
        if (latestShowReaderUi) return true
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                coroutineScope.launch { moveBackwardByReaderAction() }
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                coroutineScope.launch { moveForwardByReaderAction() }
                true
            }
            else -> false
        }
    }

    DisposableEffect(
        view,
        state.readerSettings.useVolumeButtons,
        usePageReader,
        showWebView,
        showReaderUi,
        pageReaderItemsCount,
        nativeScrollItemsCount,
    ) {
        val listener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            handleVolumeKey(event)
        }
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, _, event -> handleVolumeKey(event) }
        ViewCompat.addOnUnhandledKeyEventListener(view, listener)
        onDispose {
            view.setOnKeyListener(null)
            ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
        }
    }

    LaunchedEffect(
        autoScrollEnabled,
        autoScrollSpeed,
        state.readerSettings.autoScrollInterval,
        state.readerSettings.autoScrollAdaptiveDelay,
        usePageReader,
        showReaderUi,
        showWebView,
        webViewInstance,
        state.nextChapterId,
        state.readerSettings.autoScrollChapterEndBehavior,
        pageReaderItemsCount,
        autoScrollContentReady,
        autoScrollHasRenderableItems,
        hasCompletedInitialReaderLayout,
        state.bookMode.isEnabled,
        bookScrollSurface,
    ) {
        if (!autoScrollEnabled) return@LaunchedEffect
        var previousFrameNanos: Long? = null
        var stepRemainderPx = 0f
        while (isActive && autoScrollEnabled) {
            if (showReaderUi) {
                previousFrameNanos = null
                stepRemainderPx = 0f
                delay(120)
                continue
            }

            if (resolveAutoScrollPrefetchNeeded(
                    currentIndex = readingProgressPercent,
                    totalItems = 100,
                    behavior = state.readerSettings.autoScrollChapterEndBehavior,
                )
            ) {
                onRequestAutoScrollNextChapterPrefetch()
            }

            val isInCooldown = System.nanoTime() < touchCooldownUntilNanos
            speedFactor = resolveAutoScrollSpeedFactor(
                currentFactor = speedFactor,
                inCooldown = isInCooldown,
                delta = AUTO_SCROLL_SPEED_FACTOR_DELTA,
            )
            if (isInCooldown && speedFactor <= 0f) {
                delay(100)
                continue
            }

            // Book mode renders its own surface, so none of the chapter branches below can move
            // it. Without this branch auto-scroll either span on an absent WebView or scrolled the
            // empty chapter list, which is why it looked dead over a book.
            // The native book renderer publishes no surface: it draws the book sections into the
            // shared lazy list, so it is handled by the list branch at the bottom instead of idling
            // here forever.
            if (state.bookMode.isEnabled && (bookScrollSurface != null || !useNativeBookScroll)) {
                val surface = bookScrollSurface
                if (surface == null) {
                    previousFrameNanos = null
                    stepRemainderPx = 0f
                    autoScrollEndStableFrames = 0
                    delay(120)
                    continue
                }
                if (surface.isPaginated()) {
                    previousFrameNanos = null
                    stepRemainderPx = 0f
                    autoScrollEndStableFrames = 0
                    delay(
                        autoScrollPageDelayMsForCharacterCount(
                            intervalSeconds = state.readerSettings.autoScrollInterval,
                            characterCount = 0,
                            adaptiveEnabled = false,
                        ),
                    )
                    if (showReaderUi || !autoScrollEnabled) continue
                    surface.step(forward = true)
                    continue
                }
                val frameTimeNanos = withFrameNanos { it }
                val previousNanos = previousFrameNanos
                previousFrameNanos = frameTimeNanos
                if (previousNanos == null) continue
                val frameDeltaNanos = (frameTimeNanos - previousNanos).coerceAtLeast(1L)
                val frameStepPx = autoScrollFrameStepPx(
                    speed = autoScrollSpeed,
                    frameDeltaNanos = frameDeltaNanos,
                ) * speedFactor
                val resolvedStep = resolveAutoScrollStep(frameStepPx, stepRemainderPx)
                val stepPx = resolvedStep.stepPx
                stepRemainderPx = resolvedStep.remainderPx
                if (stepPx == 0) continue
                val consumedPx = surface.scrollBy(stepPx)
                // The scrolled book stitches the next section in at its boundary, so "cannot scroll
                // further" only means the end of the book when no section is left after this one.
                val hasSectionsLeft = state.bookMode.currentSectionIndex <
                    state.bookMode.sectionCount - 1
                val endState = resolveNovelAutoScrollEndState(
                    canScrollForward = surface.canScrollForward() || hasSectionsLeft,
                    scrollConsumedPx = consumedPx.toFloat(),
                    isContentReady = autoScrollContentReady,
                    hasCompletedInitialLayout = hasCompletedInitialReaderLayout,
                    hasRenderableItems = autoScrollHasRenderableItems,
                    previousStableEndFrameCount = autoScrollEndStableFrames,
                )
                autoScrollEndStableFrames = endState.stableEndFrameCount
                if (endState.shouldEnterDwell) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
                continue
            }
            if (showWebView) {
                val webView = webViewInstance
                if (webView == null) {
                    previousFrameNanos = null
                    stepRemainderPx = 0f
                    autoScrollEndStableFrames = 0
                    delay(120)
                    continue
                }
                val frameTimeNanos = withFrameNanos { it }
                val previousNanos = previousFrameNanos
                previousFrameNanos = frameTimeNanos
                if (previousNanos == null) continue
                val frameDeltaNanos = (frameTimeNanos - previousNanos).coerceAtLeast(1L)
                val frameStepPx = autoScrollFrameStepPx(
                    speed = autoScrollSpeed,
                    frameDeltaNanos = frameDeltaNanos,
                ) * speedFactor
                val resolvedStep = resolveAutoScrollStep(frameStepPx, stepRemainderPx)
                val stepPx = resolvedStep.stepPx
                stepRemainderPx = resolvedStep.remainderPx
                if (stepPx == 0) continue
                val canScrollBefore = webView.canScrollVertically(1)
                if (canScrollBefore) {
                    webView.scrollBy(0, stepPx)
                }
                val reachedWebAutoScrollThreshold = webAutoScrollNearEnd || !webView.canScrollVertically(1)
                val endState = resolveNovelAutoScrollEndState(
                    canScrollForward = webView.canScrollVertically(1) && !reachedWebAutoScrollThreshold,
                    scrollConsumedPx = if (canScrollBefore) stepPx.toFloat() else 0f,
                    isContentReady = autoScrollContentReady,
                    hasCompletedInitialLayout = hasCompletedInitialReaderLayout,
                    hasRenderableItems = autoScrollHasRenderableItems,
                    previousStableEndFrameCount = autoScrollEndStableFrames,
                )
                autoScrollEndStableFrames = endState.stableEndFrameCount
                if (endState.shouldEnterDwell) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
                continue
            }
            if (usePageReader) {
                previousFrameNanos = null
                stepRemainderPx = 0f
                autoScrollEndStableFrames = 0
                delay(
                    autoScrollPageDelayMsForCharacterCount(
                        intervalSeconds = state.readerSettings.autoScrollInterval,
                        characterCount = pageReaderCharacterCounts.getOrNull(pageReaderProgressPageIndex) ?: 0,
                        adaptiveEnabled = state.readerSettings.autoScrollAdaptiveDelay,
                    ),
                )
                if (showReaderUi || showWebView || !autoScrollEnabled) continue
                val currentPage = pageReaderProgressPageIndex
                if (currentPage < pageReaderItemsCount - 1) {
                    moveForwardByReaderActionWithAnimation(bookFlipPageAnimationDurationMillis)
                } else if (autoScrollContentReady && hasCompletedInitialReaderLayout && autoScrollHasRenderableItems) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
            } else {
                val frameTimeNanos = withFrameNanos { it }
                val previousNanos = previousFrameNanos
                previousFrameNanos = frameTimeNanos
                if (previousNanos == null) continue
                val frameDeltaNanos = (frameTimeNanos - previousNanos).coerceAtLeast(1L)
                val frameStepPx = autoScrollFrameStepPx(
                    speed = autoScrollSpeed,
                    frameDeltaNanos = frameDeltaNanos,
                ) * speedFactor
                val resolvedStep = resolveAutoScrollStep(frameStepPx, stepRemainderPx)
                val stepPx = resolvedStep.stepPx
                stepRemainderPx = resolvedStep.remainderPx
                if (stepPx == 0) continue
                val consumed = textListState.scrollBy(stepPx.toFloat())
                val layoutInfo = textListState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                val nativeNearConfiguredEndOffset = state.readerSettings.autoScrollOffset > 0 &&
                    lastVisibleItem != null &&
                    lastVisibleItem.index >= nativeScrollItemsCount - 1 &&
                    lastVisibleItem.offset + lastVisibleItem.size <=
                    layoutInfo.viewportEndOffset + state.readerSettings.autoScrollOffset
                val endState = resolveNovelAutoScrollEndState(
                    canScrollForward = textListState.canScrollForward && !nativeNearConfiguredEndOffset,
                    scrollConsumedPx = consumed,
                    isContentReady = autoScrollContentReady,
                    hasCompletedInitialLayout = hasCompletedInitialReaderLayout,
                    hasRenderableItems = autoScrollHasRenderableItems,
                    previousStableEndFrameCount = autoScrollEndStableFrames,
                )
                autoScrollEndStableFrames = endState.stableEndFrameCount
                if (endState.shouldEnterDwell) {
                    handleAutoScrollStableChapterEndAfterDwell()
                }
            }
        }
    }

    val sourceManager = remember { Injekt.get<NovelSourceManager>() }
    val refererUrl = remember(sourceId) {
        (sourceManager.get(sourceId) as? NovelSiteSource)?.siteUrl
    }

    CompositionLocalProvider(LocalNovelReaderReferer provides refererUrl) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(textBackground)
                .onSizeChanged { size ->
                    pageViewportSize = size
                    if (size.width > 0 && size.height > 0) {
                        hasCompletedInitialReaderLayout = true
                    }
                }
                .pointerInput(autoScrollEnabled) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        touchCooldownUntilNanos = System.nanoTime() + AUTO_SCROLL_COOLDOWN_MS * 1_000_000L
                    }
                },
        ) {
            if (
                shouldShowNovelAtmosphereBackground(
                    usePageReader = usePageReader,
                    activePageTransitionStyle = activePageTransitionStyle,
                )
            ) {
                NovelAtmosphereBackground(
                    backgroundColor = textBackground,
                    backgroundTexture = activeBackgroundTexture,
                    nativeTextureStrengthPercent = if (isBackgroundMode) {
                        0
                    } else {
                        state.readerSettings.nativeTextureStrengthPercent
                    },
                    oledEdgeGradient = activeOledEdgeGradient,
                    isDarkTheme = isDarkTheme,
                    pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                    pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                    backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                )
            }
            // Контент главы занимает весь экран; padding уже учтён в contentPadding.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!showWebView && scrollContentBlocks.isNotEmpty()) {
                    // Track progress according to the active reader mode.
                    if (usePageReader) {
                        LaunchedEffect(pageReaderProgressPageIndex, pageReaderItemsCount) {
                            reportReadingProgress(
                                pageReaderProgressPageIndex,
                                pageReaderItemsCount,
                                encodePageReaderProgress(
                                    index = pageReaderProgressPageIndex,
                                    totalItems = pageReaderItemsCount,
                                ),
                                flashDisplay = true,
                            )
                        }
                        DisposableEffect(pagerState, state.chapter.id) {
                            onDispose {
                                val latestIndex = latestPageReaderProgressPageIndex
                                val latestTotal = latestPageReaderItemsCount.coerceAtLeast(1)
                                reportReadingProgress(
                                    latestIndex,
                                    latestTotal,
                                    encodePageReaderProgress(
                                        index = latestIndex,
                                        totalItems = latestTotal,
                                    ),
                                )
                            }
                        }
                    } else {
                        LaunchedEffect(
                            textListState.firstVisibleItemIndex,
                            textListState.canScrollForward,
                            nativeScrollItemsCount,
                        ) {
                            val (progressIndex, progressTotal) = resolveNativeScrollProgressForTracking(
                                firstVisibleItemIndex = textListState.firstVisibleItemIndex,
                                textBlocksCount = nativeScrollItemsCount,
                                canScrollForward = textListState.canScrollForward,
                            )
                            reportReadingProgress(
                                progressIndex,
                                progressTotal,
                                encodeNativeScrollProgress(
                                    index = textListState.firstVisibleItemIndex,
                                    offsetPx = textListState.firstVisibleItemScrollOffset,
                                    totalItems = nativeScrollItemsCount,
                                ),
                            )
                        }
                        DisposableEffect(textListState, textListState.canScrollForward, nativeScrollItemsCount) {
                            onDispose {
                                val (progressIndex, progressTotal) = resolveNativeScrollProgressForTracking(
                                    firstVisibleItemIndex = textListState.firstVisibleItemIndex,
                                    textBlocksCount = nativeScrollItemsCount,
                                    canScrollForward = textListState.canScrollForward,
                                )
                                reportReadingProgress(
                                    progressIndex,
                                    progressTotal,
                                    encodeNativeScrollProgress(
                                        index = textListState.firstVisibleItemIndex,
                                        offsetPx = textListState.firstVisibleItemScrollOffset,
                                        totalItems = nativeScrollItemsCount,
                                    ),
                                    flashDisplay = true,
                                )
                            }
                        }
                    }

                    // Page Reader Mode (РїРѕСЃС‚СЂР°РЅРёС‡РЅС‹Р№ СЂРµР¶РёРј)
                    val bookReaderPaddingPx = with(density) { 4.dp.roundToPx() }
                    val bookReaderMaxStatusInsetPx = with(density) { 16.dp.roundToPx() }
                    val bookReaderPaddingTop = resolveWebViewPaddingTopPx(
                        statusBarHeightPx = statusBarHeight,
                        showReaderUi = showReaderUi,
                        appBarHeightPx = appBarHeight,
                        basePaddingPx = bookReaderPaddingPx,
                        maxStatusBarInsetPx = bookReaderMaxStatusInsetPx,
                    )
                    val bookReaderPaddingBottom = resolveWebViewPaddingBottomPx(
                        navigationBarHeightPx = navigationBarHeight,
                        showReaderUi = showReaderUi,
                        bottomBarHeightPx = bottomBarHeight,
                        basePaddingPx = bookReaderPaddingPx,
                    )
                    val bookReaderPaddingHorizontal = with(density) {
                        state.readerSettings.margin.dp.roundToPx()
                    }
                    val bookReaderParagraphSpacingPx = with(density) {
                        state.readerSettings.paragraphSpacing.dp.roundToPx()
                    }
                    val bookReaderTextAlignCss = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                    val bookReaderFirstLineIndentCss = resolveWebViewFirstLineIndentCss(
                        forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                    )
                    val bookReaderTextShadowCss = resolveWebReaderTextShadowCss(
                        textShadowEnabled = state.readerSettings.textShadow,
                        textShadowColor = state.readerSettings.textShadowColor,
                        textShadowBlur = state.readerSettings.textShadowBlur,
                        textShadowX = state.readerSettings.textShadowX,
                        textShadowY = state.readerSettings.textShadowY,
                        textColor = textColor,
                        backgroundColor = textBackground,
                    )
                    val bookReaderSelectedFontFamily = selectedReaderFont.id.takeIf { it.isNotBlank() }
                    val bookReaderBaseCss = buildWebReaderCssText(
                        fontFaceCss = buildNovelReaderFontFaceCss(selectedReaderFont),
                        paddingTop = bookReaderPaddingTop,
                        paddingBottom = bookReaderPaddingBottom,
                        paddingHorizontal = bookReaderPaddingHorizontal,
                        fontSizePx = state.readerSettings.fontSize,
                        lineHeightMultiplier = state.readerSettings.lineHeight,
                        paragraphSpacingPx = bookReaderParagraphSpacingPx,
                        textAlignCss = bookReaderTextAlignCss,
                        firstLineIndentCss = bookReaderFirstLineIndentCss,
                        textColorHex = colorToCssHex(textColor),
                        backgroundHex = colorToCssHex(textBackground),
                        appearanceMode = appearanceMode,
                        backgroundTexture = activeBackgroundTexture,
                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                        backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                        fontFamilyName = bookReaderSelectedFontFamily,
                        customCss = state.readerSettings.customCSS,
                        textShadowCss = bookReaderTextShadowCss,
                        forceBoldText = state.readerSettings.forceBoldText,
                        forceItalicText = state.readerSettings.forceItalicText,
                    )
                    val bookReaderCss = remember(bookReaderBaseCss) {
                        withNovelBookReaderContentOverrides(bookReaderBaseCss)
                    }

                    if (state.bookMode.isEnabled && loadBookEngineDocument != null) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            NovelBookReader(
                                spine = bookEngineSpine,
                                location = bookEngineLocation,
                                flow = if (state.readerSettings.pageReader) {
                                    NovelBookEngineFlow.PAGINATED
                                } else {
                                    NovelBookEngineFlow.SCROLLED
                                },
                                transitionStyleName = activePageTransitionStyle.name,
                                loadDocument = loadBookEngineDocument,
                                onLocationChanged = onBookEngineLocationChanged,
                                onSectionMeasured = onBookEngineSectionMeasured,
                                onToggleReaderUi = { onSetShowReaderUi(!showReaderUi) },
                                onSurfaceChanged = { surface -> bookScrollSurface = surface },
                                readerCss = bookReaderCss,
                                resolveResource = { requestUrl ->
                                    resolveReaderBackgroundWebResourceResponse(
                                        requestUrl = requestUrl,
                                        context = context,
                                        selection = backgroundSelection,
                                    ) ?: resolveReaderFontWebResourceResponse(
                                        requestUrl = requestUrl,
                                        selectedFont = selectedReaderFont,
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // The renderer paints its own start before the queued resume
                            // scroll lands. Those frames are covered instead of flashing
                            // the first page of the book.
                            var localRestoringPosition by remember(state.bookMode.isRestoringPosition) {
                                mutableStateOf(state.bookMode.isRestoringPosition)
                            }
                            LaunchedEffect(state.bookMode.isRestoringPosition) {
                                if (state.bookMode.isRestoringPosition) {
                                    kotlinx.coroutines.delay(1000L)
                                    localRestoringPosition = false
                                }
                            }
                            if (localRestoringPosition) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.matchParentSize(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    NovelAtmosphereBackground(
                                        backgroundColor = textBackground,
                                        backgroundTexture = activeBackgroundTexture,
                                        nativeTextureStrengthPercent = if (isBackgroundMode) {
                                            0
                                        } else {
                                            state.readerSettings.nativeTextureStrengthPercent
                                        },
                                        oledEdgeGradient = activeOledEdgeGradient,
                                        isDarkTheme = isDarkTheme,
                                        pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                                        pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                                        backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                                    )
                                    androidx.compose.material3.CircularProgressIndicator(
                                        color = textColor.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.COMPOSE_PAGER) {
                        ComposePagerPageRenderer(
                            pagerState = pagerState,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            readerSettings = state.readerSettings,
                            textColor = textColor,
                            textBackground = textBackground,
                            chapterTitleTextColor = chapterTitleTextColor,
                            backgroundTexture = activeBackgroundTexture,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                            activeOledEdgeGradient = activeOledEdgeGradient,
                            isDarkTheme = isDarkTheme,
                            pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                            pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                            textTypeface = composeTypeface,
                            chapterTitleTypeface = chapterTitleTypeface,
                            contentPadding = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            ttsHighlightState = ttsHighlightState,
                            ttsHighlightColor = ttsHighlightColor,
                            hasPreviousChapter = state.previousChapterId != null,
                            previousChapterName = state.previousChapterName,
                            hasNextChapter = state.nextChapterId != null,
                            nextChapterName = state.nextChapterName,
                            previousChapterLabel = stringResource(MR.strings.action_previous_chapter),
                            nextChapterLabel = stringResource(MR.strings.action_next_chapter),
                            boundaryChapterHint = stringResource(MR.strings.reader_boundary_release_to_open),
                            onToggleUi = { onSetShowReaderUi(!showReaderUi) },
                            onMoveBackward = {
                                coroutineScope.launch {
                                    moveBackwardByReaderActionWithAnimation(
                                        bookFlipPageAnimationDurationMillis,
                                    )
                                }
                            },
                            onMoveForward = {
                                coroutineScope.launch {
                                    moveForwardByReaderActionWithAnimation(
                                        bookFlipPageAnimationDurationMillis,
                                    )
                                }
                            },
                            onOpenPreviousChapter = {
                                openPreviousChapterFromReader()
                            },
                            onOpenNextChapter = { openNextChapterFromReader() },
                            onTextTap = { tapX, tapY, width, height ->
                                latestReaderShortTapHandler(tapX, tapY, width, height)
                            },
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                        PageTurnPageRenderer(
                            pagerState = pagerState,
                            chapterId = state.chapter.id,
                            contentPages = pageReaderContentPages,
                            transitionStyle = activePageTransitionStyle,
                            showBoundaryChapterPages = !seamlessChapterTransitionEnabled,
                            readerSettings = state.readerSettings,
                            textColor = textColor,
                            textBackground = textBackground,
                            chapterTitleTextColor = chapterTitleTextColor,
                            backgroundTexture = activeBackgroundTexture,
                            nativeTextureStrengthPercent = state.readerSettings.nativeTextureStrengthPercent,
                            backgroundImageModel = if (isBackgroundMode) backgroundImageModel else null,
                            backgroundModeIdentity = if (isBackgroundMode) backgroundModeIdentity else "",
                            isBackgroundMode = isBackgroundMode,
                            activeBackgroundTexture = activeBackgroundTexture,
                            activeOledEdgeGradient = activeOledEdgeGradient,
                            isDarkTheme = isDarkTheme,
                            pageEdgeShadow = state.readerSettings.pageEdgeShadow,
                            pageEdgeShadowAlpha = state.readerSettings.pageEdgeShadowAlpha,
                            textTypeface = composeTypeface,
                            chapterTitleTypeface = chapterTitleTypeface,
                            contentPadding = contentPaddingPx,
                            statusBarTopPadding = statusBarTopPadding,
                            ttsHighlightState = ttsHighlightState,
                            ttsHighlightColor = ttsHighlightColor,
                            hasPreviousChapter = state.previousChapterId != null,
                            previousChapterName = state.previousChapterName,
                            hasNextChapter = state.nextChapterId != null,
                            nextChapterName = state.nextChapterName,
                            previousChapterLabel = stringResource(MR.strings.action_previous_chapter),
                            nextChapterLabel = stringResource(MR.strings.action_next_chapter),
                            boundaryChapterHint = stringResource(MR.strings.reader_boundary_release_to_open),
                            onToggleUi = { onSetShowReaderUi(!showReaderUi) },
                            requestedPage = pageTurnRequestedPage,
                            onRequestedPageConsumed = { pageTurnRequestedPage = -1 },
                            onCurrentPageChange = { pageTurnCurrentPage = it },
                            onMoveBackward = { coroutineScope.launch { moveBackwardByReaderAction() } },
                            onMoveForward = { coroutineScope.launch { moveForwardByReaderAction() } },
                            onOpenPreviousChapter = {
                                openPreviousChapterFromReader()
                            },
                            onOpenNextChapter = { openNextChapterFromReader() },
                            chapterNavigationRequest = pageTurnChapterNavigationRequest,
                            onChapterNavigationRequestConsumed = {
                                pageTurnChapterNavigationRequest = null
                            },
                            onTextTap = { tapX, tapY, width, height ->
                                latestReaderShortTapHandler(tapX, tapY, width, height)
                            },
                            selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                            onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        )
                    } else {
                        // Scroll mode.
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(
                                    state.readerSettings.swipeToPrevChapter,
                                    state.readerSettings.swipeToNextChapter,
                                    state.previousChapterId,
                                    state.nextChapterId,
                                    nativeScrollItemsCount,
                                ) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = true)
                                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                        val elapsedMillis = up.uptimeMillis - down.uptimeMillis
                                        if (elapsedMillis >= viewConfiguration.longPressTimeoutMillis) {
                                            return@awaitEachGesture
                                        }
                                        latestReaderShortTapHandler(
                                            up.position.x,
                                            up.position.y,
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                        )
                                    }
                                }
                                .then(
                                    if (state.readerSettings.swipeGestures) {
                                        Modifier.pointerInput(
                                            state.previousChapterId,
                                            state.nextChapterId,
                                        ) {
                                            var totalDrag = 0f
                                            var handled = false
                                            detectHorizontalDragGestures(
                                                onDragStart = {
                                                    totalDrag = 0f
                                                    handled = false
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (handled) return@detectHorizontalDragGestures
                                                    totalDrag += dragAmount
                                                    if (
                                                        totalDrag > 160f &&
                                                        state.previousChapterId != null
                                                    ) {
                                                        handled = true
                                                        openPreviousChapterFromReader()
                                                    } else if (
                                                        totalDrag < -160f &&
                                                        state.nextChapterId != null
                                                    ) {
                                                        handled = true
                                                        openNextChapterFromReader()
                                                    }
                                                },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (
                                        state.readerSettings.swipeToNextChapter ||
                                        state.readerSettings.swipeToPrevChapter
                                    ) {
                                        Modifier.pointerInput(
                                            state.readerSettings.swipeToNextChapter,
                                            state.readerSettings.swipeToPrevChapter,
                                            usePageReader,
                                            showReaderUi,
                                            showWebView,
                                            state.previousChapterId,
                                            state.nextChapterId,
                                        ) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var currentPosition = down.position
                                                var gestureEndUptime = down.uptimeMillis
                                                val wasNearChapterEndAtDown =
                                                    !textListState.canScrollForward || readingProgressPercent > 97
                                                val wasNearChapterStartAtDown =
                                                    !textListState.canScrollBackward || readingProgressPercent < 3

                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                        ?: event.changes.firstOrNull()
                                                        ?: break
                                                    currentPosition = change.position
                                                    gestureEndUptime = change.uptimeMillis
                                                    if (!change.pressed) break
                                                }

                                                if (showReaderUi || showWebView || usePageReader) {
                                                    return@awaitEachGesture
                                                }

                                                val deltaX = currentPosition.x - down.position.x
                                                val deltaY = currentPosition.y - down.position.y
                                                val isNearChapterEnd =
                                                    !textListState.canScrollForward || readingProgressPercent > 97
                                                val isNearChapterStart =
                                                    !textListState.canScrollBackward || readingProgressPercent < 3
                                                val gestureDurationMillis = (gestureEndUptime - down.uptimeMillis)
                                                    .coerceAtLeast(0L)
                                                when (
                                                    resolveVerticalChapterSwipeAction(
                                                        swipeGesturesEnabled = state.readerSettings.swipeGestures,
                                                        swipeToNextChapter = state.readerSettings.swipeToNextChapter,
                                                        swipeToPrevChapter = state.readerSettings.swipeToPrevChapter,
                                                        deltaX = deltaX,
                                                        deltaY = deltaY,
                                                        minSwipeDistancePx = minVerticalChapterSwipeDistancePx,
                                                        horizontalTolerancePx = verticalChapterSwipeHorizontalTolerancePx,
                                                        gestureDurationMillis = gestureDurationMillis,
                                                        minHoldDurationMillis = minVerticalChapterSwipeHoldDurationMillis,
                                                        wasNearChapterEndAtDown = wasNearChapterEndAtDown,
                                                        wasNearChapterStartAtDown = wasNearChapterStartAtDown,
                                                        isNearChapterEnd = isNearChapterEnd,
                                                        isNearChapterStart = isNearChapterStart,
                                                    )
                                                ) {
                                                    VerticalChapterSwipeAction.NEXT -> {
                                                        val id = state.nextChapterId
                                                        val open = onOpenNextChapter
                                                        if (id != null && open != null) {
                                                            open(id)
                                                        }
                                                    }
                                                    VerticalChapterSwipeAction.PREVIOUS -> {
                                                        val id = state.previousChapterId
                                                        val open = onOpenPreviousChapter
                                                        if (id != null && open != null) {
                                                            open(id)
                                                        }
                                                    }
                                                    VerticalChapterSwipeAction.NONE -> Unit
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                            state = textListState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = contentPaddingPx + ttsScrollTopPadding,
                                bottom = contentPaddingPx,
                                start = state.readerSettings.margin.dp,
                                end = state.readerSettings.margin.dp,
                            ),
                        ) {
                            if (useNativeBookScroll) {
                                // Book mode, native renderer: one list item per resident book section,
                                // keyed by spine index so pruning and re-appending never recreates the
                                // whole book.
                                itemsIndexed(
                                    bookModeNativeEntries,
                                    key = { _, entry -> "book-${entry.sectionIndex}" },
                                ) { _, entry ->
                                    when (entry) {
                                        is NovelBookNativeEntry.Section -> {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                entry.section.blocks.forEachIndexed { blockIndex, block ->
                                                    NovelRichNativeScrollItem(
                                                        block = block,
                                                        index = blockIndex,
                                                        lastIndex = entry.section.blocks.lastIndex,
                                                        chapterTitle = state.chapter.name,
                                                        novelTitle = state.novel.title,
                                                        sourceId = state.novel.source,
                                                        chapterWebUrl = state.chapterWebUrl,
                                                        novelUrl = state.novel.url,
                                                        statusBarTopPadding = statusBarTopPadding,
                                                        textColor = textColor,
                                                        backgroundColor = textBackground,
                                                        readerSettings = state.readerSettings,
                                                        textTypeface = composeTypeface,
                                                        chapterTitleTypeface = chapterTitleTypeface,
                                                        paragraphSpacing = paragraphSpacing,
                                                        ttsHighlightState = ttsHighlightState,
                                                        ttsHighlightColor = ttsHighlightColor,
                                                        selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                                        onPlainTap = { tapX, tapY, width, height ->
                                                            latestReaderShortTapHandler(tapX, tapY, width, height)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        is NovelBookNativeEntry.Failed -> {
                                            // The book keeps the failed chapter's place, so the reader
                                            // can retry it without leaving the book.
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 24.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        AYMR.strings.novel_reader_book_mode_section_failed,
                                                    ),
                                                    color = textColor,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                )
                                                TextButton(
                                                    onClick = { onBookModeRetrySection(entry.sectionIndex) },
                                                    modifier = Modifier.padding(top = 4.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(
                                                            AYMR.strings.novel_reader_book_mode_section_retry,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (useRichNativeScroll) {
                                itemsIndexed(
                                    richScrollBlocks,
                                    // Keyed by position, not by content: a content-derived key
                                    // changes for every block when a translation is applied, which
                                    // forces the whole list to be disposed and recreated at once.
                                    key = { index, _ -> "rich-${state.chapter.id}-$index" },
                                ) { index, block ->
                                    NovelRichNativeScrollItem(
                                        block = block,
                                        index = index,
                                        lastIndex = richScrollBlocks.lastIndex,
                                        chapterTitle = state.chapter.name,
                                        novelTitle = state.novel.title,
                                        sourceId = state.novel.source,
                                        chapterWebUrl = state.chapterWebUrl,
                                        novelUrl = state.novel.url,
                                        statusBarTopPadding = statusBarTopPadding,
                                        textColor = textColor,
                                        backgroundColor = textBackground,
                                        readerSettings = state.readerSettings,
                                        textTypeface = composeTypeface,
                                        chapterTitleTypeface = chapterTitleTypeface,
                                        paragraphSpacing = paragraphSpacing,
                                        ttsHighlightState = ttsHighlightState,
                                        ttsHighlightColor = ttsHighlightColor,
                                        selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                        onPlainTap = { tapX, tapY, width, height ->
                                            latestReaderShortTapHandler(tapX, tapY, width, height)
                                        },
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    scrollContentBlocks,
                                    key = { index, _ -> "plain-${state.chapter.id}-$index" },
                                ) { index, block ->
                                    when (block) {
                                        is NovelReaderScreenModel.ContentBlock.Text -> {
                                            val isChapterTitle = index == 0 &&
                                                isNativeChapterTitleText(block.text, state.chapter.name)
                                            val baseTextContent = if (state.readerSettings.bionicReading) {
                                                toBionicText(block.text)
                                            } else {
                                                AnnotatedString(block.text)
                                            }
                                            val textContent = applyNovelReaderTtsHighlight(
                                                text = baseTextContent,
                                                blockText = block.text,
                                                sourceBlockIndex = index,
                                                highlightState = ttsHighlightState,
                                                highlightColor = ttsHighlightColor,
                                            )
                                            if (isChapterTitle) {
                                                Column(
                                                    modifier = Modifier.padding(
                                                        top = statusBarTopPadding + 10.dp,
                                                        bottom = if (index == scrollContentBlocks.lastIndex) {
                                                            0.dp
                                                        } else {
                                                            18.dp
                                                        },
                                                    ),
                                                ) {
                                                    NovelPageReaderTextBlock(
                                                        text = textContent,
                                                        isChapterTitle = true,
                                                        firstLineIndentEm = null,
                                                        readerSettings = state.readerSettings,
                                                        textColor = textColor,
                                                        textBackground = textBackground,
                                                        textAlign = state.readerSettings.textAlign,
                                                        textTypeface = composeTypeface,
                                                        chapterTitleTypeface = chapterTitleTypeface,
                                                        chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                                                        textShadowEnabled = state.readerSettings.textShadow,
                                                        textShadowColor = state.readerSettings.textShadowColor,
                                                        textShadowBlur = state.readerSettings.textShadowBlur,
                                                        textShadowX = state.readerSettings.textShadowX,
                                                        textShadowY = state.readerSettings.textShadowY,
                                                        selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                                                        selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                                        onPlainTap = { tapX, tapY, width, height ->
                                                            latestReaderShortTapHandler(tapX, tapY, width, height)
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(top = 8.dp)
                                                            .fillMaxWidth(0.72f)
                                                            .height(1.dp)
                                                            .background(
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                                            ),
                                                    )
                                                }
                                            } else {
                                                NovelPageReaderTextBlock(
                                                    text = textContent,
                                                    isChapterTitle = false,
                                                    firstLineIndentEm = if (state.readerSettings.forceParagraphIndent) {
                                                        FORCED_PARAGRAPH_FIRST_LINE_INDENT_EM
                                                    } else {
                                                        null
                                                    },
                                                    readerSettings = state.readerSettings,
                                                    textColor = textColor,
                                                    textBackground = textBackground,
                                                    textAlign = state.readerSettings.textAlign,
                                                    textTypeface = composeTypeface,
                                                    chapterTitleTypeface = chapterTitleTypeface,
                                                    chapterTitleTextColor = MaterialTheme.colorScheme.primary,
                                                    textShadowEnabled = state.readerSettings.textShadow,
                                                    textShadowColor = state.readerSettings.textShadowColor,
                                                    textShadowBlur = state.readerSettings.textShadowBlur,
                                                    textShadowX = state.readerSettings.textShadowX,
                                                    textShadowY = state.readerSettings.textShadowY,
                                                    selectionRenderer = NovelSelectedTextRenderer.NATIVE_SCROLL,
                                                    selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                                    onPlainTap = { tapX, tapY, width, height ->
                                                        latestReaderShortTapHandler(tapX, tapY, width, height)
                                                    },
                                                    modifier = Modifier.padding(
                                                        top = if (index == 0) statusBarTopPadding else 0.dp,
                                                        bottom = if (index == scrollContentBlocks.lastIndex) {
                                                            0.dp
                                                        } else {
                                                            paragraphSpacing
                                                        },
                                                    ),
                                                )
                                            }
                                        }
                                        is NovelReaderScreenModel.ContentBlock.Image -> {
                                            val referer = LocalNovelReaderReferer.current
                                            val imageModel = if (NovelPluginImage.isSupported(block.url)) {
                                                NovelPluginImage(block.url)
                                            } else if (referer != null) {
                                                NovelReaderRefererImage(
                                                    url = block.url,
                                                    referer = referer,
                                                )
                                            } else {
                                                block.url
                                            }
                                            AsyncImage(
                                                model = imageModel,
                                                contentDescription = block.alt,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        top = if (index == 0) statusBarTopPadding else 0.dp,
                                                        bottom = if (index ==
                                                            scrollContentBlocks.lastIndex
                                                        ) {
                                                            0.dp
                                                        } else {
                                                            paragraphSpacing
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val backgroundColor = resolveReaderWebViewBackgroundColor(
                        isBackgroundMode = isBackgroundMode,
                        backgroundColor = textBackground,
                    )
                    val baseUrl = remember(state.chapterWebUrl) {
                        state.chapterWebUrl
                    }

                    DisposableEffect(state.chapter.id) {
                        onDispose {
                            val webView = webViewInstance
                            val resolvedProgress = webView?.resolveCurrentWebViewProgressPercent()
                            val finalProgress = resolveFinalWebViewProgressPercent(
                                resolvedPercent = resolvedProgress,
                                cachedPercent = webProgressPercent,
                            )
                            reportReadingProgress(
                                finalProgress,
                                100,
                                encodeWebScrollProgressPercent(finalProgress),
                            )
                        }
                    }

                    DisposableEffect(Unit) {
                        onDispose {
                            val webView = webViewInstance
                            webView?.apply {
                                setOnTouchListener(null)
                                setOnScrollChangeListener(null)
                                webViewClient = object : WebViewClient() {}
                                stopLoading()
                                destroy()
                            }
                            webViewInstance = null
                        }
                    }

                    val initialWebReaderPaddingPx = with(density) { 4.dp.roundToPx() }
                    val initialMaxWebViewStatusInsetPx = with(density) { 16.dp.roundToPx() }
                    val initialPaddingTop = resolveWebViewPaddingTopPx(
                        statusBarHeightPx = statusBarHeight,
                        showReaderUi = showReaderUi,
                        appBarHeightPx = appBarHeight,
                        basePaddingPx = initialWebReaderPaddingPx,
                        maxStatusBarInsetPx = initialMaxWebViewStatusInsetPx,
                    )
                    val initialPaddingBottom = resolveWebViewPaddingBottomPx(
                        navigationBarHeightPx = navigationBarHeight,
                        showReaderUi = showReaderUi,
                        bottomBarHeightPx = bottomBarHeight,
                        basePaddingPx = initialWebReaderPaddingPx,
                    )
                    val initialPaddingHorizontal = with(density) { state.readerSettings.margin.dp.roundToPx() }
                    val initialCssTextAlign = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                    val initialCssFirstLineIndent = resolveWebViewFirstLineIndentCss(
                        forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                    )
                    val initialTextShadowCss = resolveWebReaderTextShadowCss(
                        textShadowEnabled = state.readerSettings.textShadow,
                        textShadowColor = state.readerSettings.textShadowColor,
                        textShadowBlur = state.readerSettings.textShadowBlur,
                        textShadowX = state.readerSettings.textShadowX,
                        textShadowY = state.readerSettings.textShadowY,
                        textColor = textColor,
                        backgroundColor = textBackground,
                    )
                    val initialSelectedFontFamily = selectedReaderFont.id.takeIf { it.isNotBlank() }
                    val initialFontFaceCss = buildNovelReaderFontFaceCss(selectedReaderFont)

                    // Book mode keeps one document alive for the whole novel, so the document identity
                    // must not change when the current chapter changes; otherwise the continuous DOM
                    // (and the reading position inside it) would be destroyed on every chapter switch.
                    val webReaderDocumentTag: Any = if (state.bookMode.isEnabled) {
                        BOOK_MODE_DOCUMENT_TAG
                    } else {
                        state.html
                    }
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val initialFactoryWebViewHtml = buildInitialWebReaderHtml(
                                // Book mode appends every chapter as its own section, so the document
                                // starts empty instead of holding the current chapter twice.
                                rawHtml = if (state.bookMode.isEnabled) "" else state.html,
                                readerCss = buildWebReaderCssText(
                                    fontFaceCss = initialFontFaceCss,
                                    paddingTop = initialPaddingTop,
                                    paddingBottom = initialPaddingBottom,
                                    paddingHorizontal = initialPaddingHorizontal,
                                    fontSizePx = state.readerSettings.fontSize,
                                    lineHeightMultiplier = state.readerSettings.lineHeight,
                                    paragraphSpacingPx = with(density) {
                                        state.readerSettings.paragraphSpacing.dp.roundToPx()
                                    },
                                    textAlignCss = initialCssTextAlign,
                                    firstLineIndentCss = initialCssFirstLineIndent,
                                    textColorHex = colorToCssHex(textColor),
                                    backgroundHex = colorToCssHex(textBackground),
                                    appearanceMode = appearanceMode,
                                    backgroundTexture = activeBackgroundTexture,
                                    oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                    backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                    fontFamilyName = initialSelectedFontFamily,
                                    customCss = state.readerSettings.customCSS,
                                    textShadowCss = initialTextShadowCss,
                                    forceBoldText = state.readerSettings.forceBoldText,
                                    forceItalicText = state.readerSettings.forceItalicText,
                                ),
                                hideUntilReveal = shouldHideWebViewUntilReveal,
                            )
                            val factoryShouldEarlyReveal = shouldUseEarlyWebViewReveal(state.html)
                            val factoryWebViewClient = object : WebViewClient() {
                                private var hasEarlyRevealedPage = false

                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                    super.onPageCommitVisible(view, url)
                                    if (!factoryShouldEarlyReveal || hasEarlyRevealedPage) return
                                    hasEarlyRevealedPage = true
                                    view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    resolveReaderBackgroundWebResourceResponse(
                                        requestUrl = requestUrl,
                                        context = context,
                                        selection = backgroundSelection,
                                    )?.let { response ->
                                        return response
                                    }
                                    resolveReaderFontWebResourceResponse(
                                        requestUrl = requestUrl,
                                        selectedFont = selectedReaderFont,
                                    )?.let { response ->
                                        return response
                                    }
                                    if (!NovelPluginImage.isSupported(requestUrl)) {
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    val image = NovelPluginImageResolver.resolveBlocking(requestUrl)
                                        ?: return super.shouldInterceptRequest(view, request)
                                    return WebResourceResponse(
                                        image.mimeType,
                                        null,
                                        ByteArrayInputStream(image.bytes),
                                    )
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    webViewPageReadyForAutoScroll = true
                                    bookModeDocumentGeneration++
                                    view?.applyReaderCss(
                                        fontFaceCss = initialFontFaceCss,
                                        paddingTop = initialPaddingTop,
                                        paddingBottom = initialPaddingBottom,
                                        paddingHorizontal = initialPaddingHorizontal,
                                        fontSizePx = state.readerSettings.fontSize,
                                        lineHeightMultiplier = state.readerSettings.lineHeight,
                                        paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                        textAlignCss = initialCssTextAlign,
                                        firstLineIndentCss = initialCssFirstLineIndent,
                                        textColorHex = colorToCssHex(textColor),
                                        backgroundHex = colorToCssHex(textBackground),
                                        appearanceMode = appearanceMode,
                                        backgroundTexture = activeBackgroundTexture,
                                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                        backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                        fontFamilyName = initialSelectedFontFamily,
                                        customCss = state.readerSettings.customCSS,
                                        textShadowCss = initialTextShadowCss,
                                        forceBoldText = state.readerSettings.forceBoldText,
                                        forceItalicText = state.readerSettings.forceItalicText,
                                        bionicReadingEnabled = state.readerSettings.bionicReading,
                                    )
                                    appliedWebCssFingerprint = buildWebReaderCssFingerprint(
                                        chapterId = state.chapter.id,
                                        paddingTop = initialPaddingTop,
                                        paddingBottom = initialPaddingBottom,
                                        paddingHorizontal = initialPaddingHorizontal,
                                        fontSizePx = state.readerSettings.fontSize,
                                        lineHeightMultiplier = state.readerSettings.lineHeight,
                                        paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                        textAlignCss = initialCssTextAlign,
                                        firstLineIndentCss = initialCssFirstLineIndent,
                                        textColorHex = colorToCssHex(textColor),
                                        backgroundHex = colorToCssHex(textBackground),
                                        appearanceMode = appearanceMode,
                                        backgroundTexture = activeBackgroundTexture,
                                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                        backgroundImageIdentity = if (isBackgroundMode) backgroundModeIdentity else null,
                                        fontFamilyName = initialSelectedFontFamily,
                                        customCss = state.readerSettings.customCSS,
                                        textShadowCss = initialTextShadowCss,
                                        forceBoldText = state.readerSettings.forceBoldText,
                                        forceItalicText = state.readerSettings.forceItalicText,
                                    )

                                    if (state.readerSettings.customJS.isNotEmpty()) {
                                        view?.evaluateJavascript(
                                            """
                                        (function() {
                                            ${state.readerSettings.customJS}
                                        })();
                                            """.trimIndent(),
                                            null,
                                        )
                                    }

                                    if (shouldRestoreWebScroll) {
                                        view?.restoreWebViewScroll(
                                            progressPercent = state.lastSavedWebProgressPercent.coerceIn(0, 100),
                                            onComplete = { restored ->
                                                shouldRestoreWebScroll = !restored
                                                if (restored) {
                                                    val settledProgress = view.resolveCurrentWebViewProgressPercent()
                                                    if (shouldDispatchWebProgressUpdate(
                                                            false,
                                                            settledProgress,
                                                            webProgressPercent,
                                                        )
                                                    ) {
                                                        webProgressPercent = settledProgress
                                                        webAutoScrollNearEnd = settledProgress >= 100
                                                        reportReadingProgress(
                                                            settledProgress,
                                                            100,
                                                            encodeWebScrollProgressPercent(settledProgress),
                                                        )
                                                    }
                                                }
                                                view.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                            },
                                        )
                                    } else {
                                        val settledProgress = view?.resolveCurrentWebViewProgressPercent()
                                            ?: webProgressPercent
                                        if (shouldDispatchWebProgressUpdate(
                                                false,
                                                settledProgress,
                                                webProgressPercent,
                                            )
                                        ) {
                                            webProgressPercent = settledProgress
                                            webAutoScrollNearEnd = settledProgress >= 100
                                            reportReadingProgress(
                                                settledProgress,
                                                100,
                                                encodeWebScrollProgressPercent(settledProgress),
                                            )
                                        }
                                        view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                    }
                                }
                            }

                            createNovelReaderWebView(context).apply {
                                webViewInstance = this
                                setBackgroundColor(backgroundColor)
                                alpha = if (shouldHideWebViewUntilReveal) 0f else 1f
                                settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(
                                    pluginRequestsJavaScript = state.enableJs,
                                    bookModeEnabled = state.bookMode.isEnabled,
                                )
                                settings.domStorageEnabled = false
                                registerWebReaderSelectionBridge(
                                    selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                )

                                webViewClient = factoryWebViewClient
                                setOnScrollChangeListener { view, _, scrollY, _, _ ->
                                    val webView = view as? WebView ?: return@setOnScrollChangeListener
                                    if (!shouldTrackWebViewProgress(shouldRestoreWebScroll)) {
                                        return@setOnScrollChangeListener
                                    }
                                    val totalScrollable = resolveWebViewTotalScrollablePx(
                                        contentHeightPx = webView.resolveWebViewContentHeightPx(),
                                        viewHeightPx = webView.height,
                                    )
                                    webAutoScrollNearEnd = resolveWebViewAutoScrollNearEnd(
                                        totalScrollablePx = totalScrollable,
                                        scrollYPx = scrollY,
                                        endOffsetPx = state.readerSettings.autoScrollOffset,
                                    )
                                    val newPercent = webView.resolveCurrentWebViewProgressPercent(
                                        scrollYOverride = scrollY,
                                    )

                                    if (shouldDispatchWebProgressUpdate(
                                            shouldRestoreWebScroll,
                                            newPercent,
                                            webProgressPercent,
                                        )
                                    ) {
                                        webProgressPercent = newPercent
                                        reportReadingProgress(
                                            newPercent,
                                            100,
                                            encodeWebScrollProgressPercent(newPercent),
                                        )
                                    }
                                }
                                loadDataWithBaseURL(baseUrl, initialFactoryWebViewHtml, "text/html", "utf-8", null)
                                tag = webReaderDocumentTag
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                            if (webView is NovelReaderWebView) {
                                webView.isDictionaryEnabled = state.novelDictionaryEnabled
                                webView.isTranslationEnabled = state.readerSettings.selectedTextTranslationEnabled
                            }
                            webView.setBackgroundColor(backgroundColor)
                            webView.settings.javaScriptEnabled = shouldEnableJavaScriptInReaderWebView(
                                pluginRequestsJavaScript = state.enableJs,
                                bookModeEnabled = state.bookMode.isEnabled,
                            )
                            webView.registerWebReaderSelectionBridge(
                                selectionSessionIdProvider = nextSelectedTextSelectionSessionId,
                                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                            )
                            val minWebSwipeDistancePx = minVerticalChapterSwipeDistancePx
                            val webSwipeHorizontalTolerancePx = verticalChapterSwipeHorizontalTolerancePx
                            val minWebSwipeHoldDurationMillis = minVerticalChapterSwipeHoldDurationMillis
                            var touchStartX = 0f
                            var touchStartY = 0f
                            var touchStartEventTime = 0L
                            var wasNearChapterEndAtDown = false
                            var wasNearChapterStartAtDown = false
                            var horizontalSwipeHandled = false
                            val gestureDetector = GestureDetector(
                                webView.context,
                                object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                        val viewWidth = webView.width.takeIf { it > 0 } ?: return false
                                        val viewHeight = webView.height.takeIf { it > 0 } ?: return false
                                        return when (
                                            resolveConfiguredNovelReaderTapAction(
                                                tapX = e.x,
                                                tapY = e.y,
                                                width = viewWidth.toFloat(),
                                                height = viewHeight.toFloat(),
                                                customTapZonesEnabled = latestCustomTapZonesEnabled,
                                                tapZoneActions = latestTapZoneActions,
                                                tapToScrollEnabled = latestTapToScrollEnabled,
                                            )
                                        ) {
                                            NovelReaderTapZoneAction.TOGGLE_UI -> {
                                                onSetShowReaderUi(!latestShowReaderUi)
                                                true
                                            }
                                            NovelReaderTapZoneAction.BACKWARD -> {
                                                coroutineScope.launch { moveBackwardByReaderAction() }
                                                true
                                            }
                                            NovelReaderTapZoneAction.FORWARD -> {
                                                coroutineScope.launch { moveForwardByReaderAction() }
                                                true
                                            }
                                            NovelReaderTapZoneAction.NEXT_CHAPTER -> {
                                                openNextChapterFromReader()
                                                true
                                            }
                                            NovelReaderTapZoneAction.PREV_CHAPTER -> {
                                                openPreviousChapterFromReader()
                                                true
                                            }
                                            NovelReaderTapZoneAction.NONE -> true
                                        }
                                    }
                                },
                            )
                            webView.setOnTouchListener { _, event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        touchStartX = event.x
                                        touchStartY = event.y
                                        touchStartEventTime = event.eventTime
                                        wasNearChapterEndAtDown = !webView.canScrollVertically(1)
                                        wasNearChapterStartAtDown = !webView.canScrollVertically(-1)
                                        horizontalSwipeHandled = false
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        if (!latestShowReaderUi && !horizontalSwipeHandled) {
                                            if (state.bookMode.isEnabled &&
                                                bookRenderer.flow == NovelBookFlow.PAGINATED
                                            ) {
                                                val deltaX = event.x - touchStartX
                                                val deltaY = event.y - touchStartY
                                                if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) &&
                                                    kotlin.math.abs(deltaX) > 80f
                                                ) {
                                                    horizontalSwipeHandled = true
                                                    if (deltaX < 0) {
                                                        coroutineScope.launch { moveForwardByReaderAction() }
                                                    } else {
                                                        coroutineScope.launch { moveBackwardByReaderAction() }
                                                    }
                                                }
                                            } else {
                                                when (
                                                    resolveHorizontalChapterSwipeAction(
                                                        swipeGesturesEnabled = state.readerSettings.swipeGestures,
                                                        deltaX = event.x - touchStartX,
                                                        deltaY = event.y - touchStartY,
                                                        thresholdPx = 160f,
                                                        hasPreviousChapter = state.previousChapterId != null,
                                                        hasNextChapter = state.nextChapterId != null,
                                                    )
                                                ) {
                                                    HorizontalChapterSwipeAction.PREVIOUS -> {
                                                        horizontalSwipeHandled = true
                                                        openPreviousChapterFromReader()
                                                    }
                                                    HorizontalChapterSwipeAction.NEXT -> {
                                                        horizontalSwipeHandled = true
                                                        openNextChapterFromReader()
                                                    }
                                                    HorizontalChapterSwipeAction.NONE -> Unit
                                                }
                                            }
                                        }
                                        if (!latestShowReaderUi && !horizontalSwipeHandled) {
                                            val deltaX = event.x - touchStartX
                                            val deltaY = event.y - touchStartY
                                            val gestureDurationMillis = (event.eventTime - touchStartEventTime)
                                                .coerceAtLeast(0L)
                                            val isNearChapterEnd =
                                                wasNearChapterEndAtDown && !webView.canScrollVertically(1)
                                            val isNearChapterStart =
                                                wasNearChapterStartAtDown && !webView.canScrollVertically(-1)

                                            when (
                                                resolveWebViewVerticalChapterSwipeAction(
                                                    swipeGesturesEnabled = state.readerSettings.swipeGestures,
                                                    swipeToNextChapter = state.readerSettings.swipeToNextChapter,
                                                    swipeToPrevChapter = state.readerSettings.swipeToPrevChapter,
                                                    deltaX = deltaX,
                                                    deltaY = deltaY,
                                                    minSwipeDistancePx = minWebSwipeDistancePx,
                                                    horizontalTolerancePx = webSwipeHorizontalTolerancePx,
                                                    gestureDurationMillis = gestureDurationMillis,
                                                    minHoldDurationMillis = minWebSwipeHoldDurationMillis,
                                                    wasNearChapterEndAtDown = wasNearChapterEndAtDown,
                                                    wasNearChapterStartAtDown = wasNearChapterStartAtDown,
                                                    isNearChapterEnd = isNearChapterEnd,
                                                    isNearChapterStart = isNearChapterStart,
                                                )
                                            ) {
                                                VerticalChapterSwipeAction.NEXT -> {
                                                    openNextChapterFromReader()
                                                }
                                                VerticalChapterSwipeAction.PREVIOUS -> {
                                                    openPreviousChapterFromReader()
                                                }
                                                VerticalChapterSwipeAction.NONE -> Unit
                                            }
                                        }
                                    }
                                }
                                if (!horizontalSwipeHandled) {
                                    gestureDetector.onTouchEvent(event)
                                }
                                false
                            }

                            val webReaderPaddingPx = with(density) { 4.dp.roundToPx() }
                            val maxWebViewStatusInsetPx = with(density) { 16.dp.roundToPx() }
                            val paddingTop = resolveWebViewPaddingTopPx(
                                statusBarHeightPx = statusBarHeight,
                                showReaderUi = showReaderUi,
                                appBarHeightPx = appBarHeight,
                                basePaddingPx = webReaderPaddingPx,
                                maxStatusBarInsetPx = maxWebViewStatusInsetPx,
                            )
                            val paddingBottom = resolveWebViewPaddingBottomPx(
                                navigationBarHeightPx = navigationBarHeight,
                                showReaderUi = showReaderUi,
                                bottomBarHeightPx = bottomBarHeight,
                                basePaddingPx = webReaderPaddingPx,
                            )
                            val paddingHorizontal = with(density) { state.readerSettings.margin.dp.roundToPx() }
                            val cssTextAlign = resolveWebViewTextAlignCss(state.readerSettings.textAlign)
                            val cssFirstLineIndent = resolveWebViewFirstLineIndentCss(
                                forceParagraphIndent = state.readerSettings.forceParagraphIndent,
                            )
                            val selectedFontFamily = selectedReaderFont.id.takeIf { it.isNotBlank() }
                            val fontFaceCss = buildNovelReaderFontFaceCss(selectedReaderFont)
                            val currentTextColorCss = colorToCssHex(textColor)
                            val currentBackgroundCss = colorToCssHex(textBackground)
                            // In book mode the document also needs the section/divider/placeholder
                            // styles. Appending them to the user's custom CSS keeps every CSS path
                            // (initial load, reload and live restyle) in sync automatically.
                            val bookSectionsCss = if (state.bookMode.isEnabled) buildBookSectionsCss() else ""
                            val currentCustomCss = if (bookSectionsCss.isEmpty()) {
                                state.readerSettings.customCSS
                            } else {
                                state.readerSettings.customCSS + "\n" + bookSectionsCss
                            }
                            val currentCustomJs = state.readerSettings.customJS
                            val currentTextShadowCss = resolveWebReaderTextShadowCss(
                                textShadowEnabled = state.readerSettings.textShadow,
                                textShadowColor = state.readerSettings.textShadowColor,
                                textShadowBlur = state.readerSettings.textShadowBlur,
                                textShadowX = state.readerSettings.textShadowX,
                                textShadowY = state.readerSettings.textShadowY,
                                textColor = textColor,
                                backgroundColor = textBackground,
                            )
                            val paragraphSpacingPx =
                                with(density) { state.readerSettings.paragraphSpacing.dp.roundToPx() }
                            val styleFingerprint = buildWebReaderCssFingerprint(
                                // Book mode keeps one document alive for the whole novel, so the
                                // fingerprint must not change per chapter.
                                chapterId = if (state.bookMode.isEnabled) 0L else state.chapter.id,
                                paddingTop = paddingTop,
                                paddingBottom = paddingBottom,
                                paddingHorizontal = paddingHorizontal,
                                fontSizePx = state.readerSettings.fontSize,
                                lineHeightMultiplier = state.readerSettings.lineHeight,
                                paragraphSpacingPx = paragraphSpacingPx,
                                textAlignCss = cssTextAlign,
                                firstLineIndentCss = cssFirstLineIndent,
                                textColorHex = colorToCssHex(textColor),
                                backgroundHex = colorToCssHex(textBackground),
                                appearanceMode = appearanceMode,
                                backgroundTexture = activeBackgroundTexture,
                                oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                backgroundImageIdentity = if (isBackgroundMode) backgroundModeIdentity else null,
                                fontFamilyName = selectedFontFamily,
                                customCss = state.readerSettings.customCSS,
                                textShadowCss = currentTextShadowCss,
                                forceBoldText = state.readerSettings.forceBoldText,
                                forceItalicText = state.readerSettings.forceItalicText,
                            )
                            val currentFontSize = state.readerSettings.fontSize
                            val currentLineHeight = state.readerSettings.lineHeight
                            val currentRestoreProgress = state.lastSavedWebProgressPercent.coerceIn(0, 100)
                            val shouldEarlyRevealWebView = shouldUseEarlyWebViewReveal(state.html)
                            webView.webViewClient = object : WebViewClient() {
                                private var hasEarlyRevealedPage = false

                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                    super.onPageCommitVisible(view, url)
                                    if (!shouldEarlyRevealWebView || hasEarlyRevealedPage) return
                                    hasEarlyRevealedPage = true
                                    view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    val requestUrl = request?.url?.toString().orEmpty()
                                    resolveReaderBackgroundWebResourceResponse(
                                        requestUrl = requestUrl,
                                        context = webView.context,
                                        selection = backgroundSelection,
                                    )?.let { response ->
                                        return response
                                    }
                                    resolveReaderFontWebResourceResponse(
                                        requestUrl = requestUrl,
                                        selectedFont = selectedReaderFont,
                                    )?.let { response ->
                                        return response
                                    }
                                    if (!NovelPluginImage.isSupported(requestUrl)) {
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    val image = NovelPluginImageResolver.resolveBlocking(requestUrl)
                                        ?: return super.shouldInterceptRequest(view, request)
                                    return WebResourceResponse(
                                        image.mimeType,
                                        null,
                                        ByteArrayInputStream(image.bytes),
                                    )
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    webViewPageReadyForAutoScroll = true
                                    bookModeDocumentGeneration++
                                    view?.applyReaderCss(
                                        fontFaceCss = fontFaceCss,
                                        paddingTop = paddingTop,
                                        paddingBottom = paddingBottom,
                                        paddingHorizontal = paddingHorizontal,
                                        fontSizePx = currentFontSize,
                                        lineHeightMultiplier = currentLineHeight,
                                        paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                        textAlignCss = cssTextAlign,
                                        firstLineIndentCss = cssFirstLineIndent,
                                        textColorHex = currentTextColorCss,
                                        backgroundHex = currentBackgroundCss,
                                        appearanceMode = appearanceMode,
                                        backgroundTexture = activeBackgroundTexture,
                                        oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                        backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                        fontFamilyName = selectedFontFamily,
                                        customCss = currentCustomCss,
                                        textShadowCss = currentTextShadowCss,
                                        forceBoldText = state.readerSettings.forceBoldText,
                                        forceItalicText = state.readerSettings.forceItalicText,
                                        bionicReadingEnabled = state.readerSettings.bionicReading,
                                    )
                                    appliedWebCssFingerprint = styleFingerprint

                                    if (currentCustomJs.isNotEmpty()) {
                                        view?.evaluateJavascript(
                                            """
                                        (function() {
                                            $currentCustomJs
                                        })();
                                            """.trimIndent(),
                                            null,
                                        )
                                    }

                                    if (shouldRestoreWebScroll) {
                                        view?.restoreWebViewScroll(
                                            progressPercent = currentRestoreProgress,
                                            onComplete = { restored ->
                                                shouldRestoreWebScroll = !restored
                                                if (restored) {
                                                    val settledProgress = view.resolveCurrentWebViewProgressPercent()
                                                    if (shouldDispatchWebProgressUpdate(
                                                            false,
                                                            settledProgress,
                                                            webProgressPercent,
                                                        )
                                                    ) {
                                                        webProgressPercent = settledProgress
                                                        reportReadingProgress(
                                                            settledProgress,
                                                            100,
                                                            encodeWebScrollProgressPercent(settledProgress),
                                                        )
                                                    }
                                                }
                                                view.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                            },
                                        )
                                    } else {
                                        val settledProgress = view?.resolveCurrentWebViewProgressPercent()
                                            ?: webProgressPercent
                                        if (shouldDispatchWebProgressUpdate(
                                                false,
                                                settledProgress,
                                                webProgressPercent,
                                            )
                                        ) {
                                            webProgressPercent = settledProgress
                                            reportReadingProgress(
                                                settledProgress,
                                                100,
                                                encodeWebScrollProgressPercent(settledProgress),
                                            )
                                        }
                                        view?.revealReaderDocumentAndWebView(shouldHideWebViewUntilReveal)
                                    }
                                }
                            }

                            if (webView.tag != webReaderDocumentTag) {
                                val currentRestoreProgress = state.lastSavedWebProgressPercent.coerceIn(0, 100)
                                // A seamless chapter switch replaces the document inside the live
                                // WebView. Keeping the old frame visible until the next document
                                // paints removes the fade-to-background flash at the chapter seam.
                                val isSeamlessChapterSwap =
                                    state.seamlessSwitchToken != appliedSeamlessSwitchToken[0]
                                appliedSeamlessSwitchToken[0] = state.seamlessSwitchToken
                                val seamlessInstantSwap = isSeamlessChapterSwap &&
                                    currentRestoreProgress <= 0
                                val currentReaderCss = buildWebReaderCssText(
                                    fontFaceCss = fontFaceCss,
                                    paddingTop = paddingTop,
                                    paddingBottom = paddingBottom,
                                    paddingHorizontal = paddingHorizontal,
                                    fontSizePx = currentFontSize,
                                    lineHeightMultiplier = currentLineHeight,
                                    paragraphSpacingPx = state.readerSettings.paragraphSpacing,
                                    textAlignCss = cssTextAlign,
                                    firstLineIndentCss = cssFirstLineIndent,
                                    textColorHex = currentTextColorCss,
                                    backgroundHex = currentBackgroundCss,
                                    appearanceMode = appearanceMode,
                                    backgroundTexture = activeBackgroundTexture,
                                    oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                    backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                    fontFamilyName = selectedFontFamily,
                                    customCss = currentCustomCss,
                                    textShadowCss = currentTextShadowCss,
                                    forceBoldText = state.readerSettings.forceBoldText,
                                    forceItalicText = state.readerSettings.forceItalicText,
                                )
                                val initialWebViewHtml = buildInitialWebReaderHtml(
                                    rawHtml = if (state.bookMode.isEnabled) "" else state.html,
                                    readerCss = currentReaderCss,
                                    hideUntilReveal = shouldHideWebViewUntilReveal && !seamlessInstantSwap,
                                )
                                val shouldEarlyRevealWebView = shouldUseEarlyWebViewReveal(state.html)
                                shouldRestoreWebScroll = true
                                appliedWebCssFingerprint = null
                                webView.animate().cancel()
                                webView.alpha = if (shouldHideWebViewUntilReveal && !seamlessInstantSwap) 0f else 1f
                                webView.loadDataWithBaseURL(baseUrl, initialWebViewHtml, "text/html", "utf-8", null)
                                webView.tag = webReaderDocumentTag
                            } else if (appliedWebCssFingerprint != styleFingerprint) {
                                webView.applyReaderCss(
                                    fontFaceCss = fontFaceCss,
                                    paddingTop = paddingTop,
                                    paddingBottom = paddingBottom,
                                    paddingHorizontal = paddingHorizontal,
                                    fontSizePx = state.readerSettings.fontSize,
                                    lineHeightMultiplier = state.readerSettings.lineHeight,
                                    paragraphSpacingPx = paragraphSpacingPx,
                                    textAlignCss = cssTextAlign,
                                    firstLineIndentCss = cssFirstLineIndent,
                                    textColorHex = colorToCssHex(textColor),
                                    backgroundHex = colorToCssHex(textBackground),
                                    appearanceMode = appearanceMode,
                                    backgroundTexture = activeBackgroundTexture,
                                    oledEdgeGradient = activeOledEdgeGradient && isDarkTheme,
                                    backgroundImageUrl = if (isBackgroundMode) backgroundModeWebImageUrl else null,
                                    fontFamilyName = selectedFontFamily,
                                    customCss = state.readerSettings.customCSS,
                                    textShadowCss = currentTextShadowCss,
                                    forceBoldText = state.readerSettings.forceBoldText,
                                    forceItalicText = state.readerSettings.forceItalicText,
                                    bionicReadingEnabled = state.readerSettings.bionicReading,
                                )
                                appliedWebCssFingerprint = styleFingerprint
                            }
                        },
                        onRelease = { webView ->
                            webView.clearFocus()
                        },
                    )
                }
            }

            // UI overlay above the content.
            AnimatedVisibility(
                visible = showBottomInfoOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(
                        bottom = with(density) { bottomBarHeight.toDp() } + MaterialTheme.padding.small,
                    ),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.small,
                            vertical = 6.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (state.readerSettings.showBatteryAndTime) {
                            Text(
                                text = "${batteryLevel.coerceIn(0, 100)}% $timeText",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (state.readerSettings.showKindleInfoBlock && state.readerSettings.showTimeToEnd) {
                            Text(
                                text = if (remainingMinutes == null) {
                                    stringResource(AYMR.strings.novel_reader_time_to_end_unknown)
                                } else {
                                    stringResource(
                                        AYMR.strings.novel_reader_time_to_end_minutes,
                                        remainingMinutes.coerceAtLeast(0),
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (state.readerSettings.showKindleInfoBlock && state.readerSettings.showWordCount) {
                            Text(
                                text = stringResource(
                                    AYMR.strings.novel_reader_words_progress,
                                    readWords,
                                    totalWords,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            val seekbarItemsCount = if (showWebView) {
                101
            } else if (usePageReader) {
                pageReaderItemsCount
            } else {
                nativeScrollItemsCount
            }
            val showPageReaderDismissLayer = shouldShowPageReaderDismissLayer(
                showReaderUi = showReaderUi,
                usePageReader = usePageReader,
            )
            if (showPageReaderDismissLayer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(showPageReaderDismissLayer) {
                            detectTapGestures(
                                onTap = {
                                    onSetShowReaderUi(false)
                                },
                            )
                        },
                )
            }
            if (
                shouldShowVerticalSeekbar(
                    showReaderUi = showReaderUi,
                    verticalSeekbarEnabled = state.readerSettings.verticalSeekbar,
                    showWebView = showWebView,
                    usePageReader = usePageReader,
                    textBlocksCount = seekbarItemsCount,
                )
            ) {
                val seekbarValue by remember(
                    showWebView,
                    webProgressPercent,
                    usePageReader,
                    pagerState.currentPage,
                    pageTurnCurrentPage,
                    textListState.firstVisibleItemIndex,
                    textListState.canScrollForward,
                    seekbarItemsCount,
                    readingProgressPercent,
                    state.bookMode.isEnabled,
                ) {
                    derivedStateOf {
                        resolveReaderVerticalSeekbarValue(
                            showWebView = showWebView,
                            webProgressPercent = webProgressPercent,
                            usePageReader = usePageReader,
                            pageReaderRendererRoute = pageReaderRendererRoute,
                            pagerCurrentPage = pagerState.currentPage,
                            pageTurnCurrentPage = pageTurnCurrentPage,
                            composePagerContentPageCount = pageReaderItemsCount,
                            composePagerHasPreviousChapter = composePagerHasPreviousChapter,
                            pageTurnContentPageCount = pageReaderItemsCount,
                            pageTurnHasPreviousChapter = composePagerHasPreviousChapter,
                            seekbarItemsCount = seekbarItemsCount,
                            readingProgressPercent = readingProgressPercent,
                            bookModeEnabled = state.bookMode.isEnabled,
                        )
                    }
                }
                val (pageRailTopLabel, pageRailBottomLabel) = if (state.bookMode.isEnabled) {
                    "${readingProgressPercent.coerceIn(0, 100)}%" to "100%"
                } else if (usePageReader) {
                    resolveReaderPageRailLabels(
                        pageIndex = pageReaderProgressPageIndex,
                        pageCount = pageReaderItemsCount,
                    )
                } else {
                    verticalSeekbarLabels(
                        readingProgressPercent = readingProgressPercent,
                        showScrollPercentage = state.readerSettings.showScrollPercentage,
                    )
                }
                val pageSeekbarTickFractions = if (state.bookMode.isEnabled) {
                    emptyList()
                } else if (usePageReader) {
                    resolveReaderVerticalSeekbarTickFractions(pageReaderItemsCount)
                } else {
                    emptyList()
                }
                Column(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .padding(end = MaterialTheme.padding.small)
                        .size(
                            width = if (usePageReader &&
                                !state.bookMode.isEnabled
                            ) {
                                40.dp
                            } else {
                                30.dp
                            },
                            height = 270.dp,
                        ),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    if (state.readerSettings.geminiEnabled) {
                        val hasTranslationResult =
                            state.hasGeminiTranslationCache || state.geminiTranslationProgress == 100
                        val quickActionIcon = when {
                            state.isGeminiTranslating -> Icons.Filled.Pause
                            hasTranslationResult && state.isGeminiTranslationVisible -> Icons.Filled.Public
                            else -> Icons.Filled.PlayArrow
                        }
                        val quickActionDescription = when {
                            state.isGeminiTranslating -> stringResource(MR.strings.reader_action_stop_translation)
                            hasTranslationResult && state.isGeminiTranslationVisible -> stringResource(
                                MR.strings.reader_action_show_original,
                            )
                            hasTranslationResult -> stringResource(MR.strings.reader_action_show_translation)
                            else -> stringResource(MR.strings.reader_action_start_translation)
                        }
                        val quickActionContainerColor = when {
                            state.isGeminiTranslating -> MaterialTheme.colorScheme.errorContainer
                            hasTranslationResult && state.isGeminiTranslationVisible ->
                                MaterialTheme.colorScheme.tertiaryContainer
                            hasTranslationResult -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val quickActionContentColor = when {
                            state.isGeminiTranslating -> MaterialTheme.colorScheme.onErrorContainer
                            hasTranslationResult && state.isGeminiTranslationVisible ->
                                MaterialTheme.colorScheme.onTertiaryContainer
                            hasTranslationResult ->
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        Column(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = quickActionContainerColor,
                                contentColor = quickActionContentColor,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        when {
                                            state.isGeminiTranslating -> onStopGeminiTranslation()
                                            hasTranslationResult -> onToggleGeminiTranslationVisibility()
                                            else -> requestGeminiTranslationStart()
                                        }
                                    },
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = quickActionIcon,
                                        contentDescription = quickActionDescription,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            if (state.isGeminiTranslating) {
                                LinearProgressIndicator(
                                    progress = { state.geminiTranslationProgress.coerceIn(0, 100) / 100f },
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.readerSettings.googleTranslationEnabled) {
                        val hasGoogleResult = state.hasGoogleTranslationCache || state.googleTranslationProgress == 100
                        val googleQuickActionIcon = when {
                            state.isGoogleTranslating -> Icons.Filled.Pause
                            hasGoogleResult && state.isGoogleTranslationVisible -> Icons.Filled.Public
                            else -> Icons.Filled.PlayArrow
                        }
                        val googleQuickActionDescription = when {
                            state.isGoogleTranslating -> stringResource(AYMR.strings.novel_reader_google_translate_stop)
                            hasGoogleResult && state.isGoogleTranslationVisible -> stringResource(
                                AYMR.strings.novel_reader_google_translate_original,
                            )
                            hasGoogleResult -> stringResource(AYMR.strings.novel_reader_google_translate_translated)
                            else -> stringResource(AYMR.strings.novel_reader_google_translate_start)
                        }
                        val googleQuickActionContainerColor = when {
                            state.isGoogleTranslating -> MaterialTheme.colorScheme.errorContainer
                            hasGoogleResult && state.isGoogleTranslationVisible ->
                                MaterialTheme.colorScheme.tertiaryContainer
                            hasGoogleResult -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val googleQuickActionContentColor = when {
                            state.isGoogleTranslating -> MaterialTheme.colorScheme.onErrorContainer
                            hasGoogleResult && state.isGoogleTranslationVisible ->
                                MaterialTheme.colorScheme.onTertiaryContainer
                            hasGoogleResult ->
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }

                        Column(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = googleQuickActionContainerColor,
                                contentColor = googleQuickActionContentColor,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        when {
                                            state.isGoogleTranslating -> onStopGoogleTranslation()
                                            hasGoogleResult -> onToggleGoogleTranslationVisibility()
                                            else -> requestGoogleTranslationStart()
                                        }
                                    },
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = googleQuickActionIcon,
                                        contentDescription = googleQuickActionDescription,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            if (state.isGoogleTranslating) {
                                LinearProgressIndicator(
                                    progress = { state.googleTranslationProgress.coerceIn(0, 100) / 100f },
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 3.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                    LnReaderVerticalSeekbar(
                        progress = seekbarValue,
                        topLabel = pageRailTopLabel,
                        bottomLabel = pageRailBottomLabel,
                        tickFractions = pageSeekbarTickFractions,
                        onProgressChange = { value ->
                            if (state.bookMode.isEnabled) {
                                onSeekBookModeProgress(value)
                            } else if (showWebView) {
                                val targetPercent = (value * 100f).roundToInt().coerceIn(0, 100)
                                webProgressPercent = targetPercent
                                val webView = webViewInstance
                                if (webView != null) {
                                    val totalScrollable = resolveWebViewTotalScrollablePx(
                                        contentHeightPx = webView.resolveWebViewContentHeightPx(),
                                        viewHeightPx = webView.height,
                                    )
                                    if (totalScrollable > 0) {
                                        val targetY = ((targetPercent.toFloat() / 100f) * totalScrollable.toFloat())
                                            .roundToInt()
                                            .coerceIn(0, totalScrollable)
                                        webView.scrollTo(0, targetY)
                                    } else {
                                        webView.scrollTo(0, 0)
                                    }
                                }
                                reportReadingProgress(targetPercent, 100, encodeWebScrollProgressPercent(targetPercent))
                            } else {
                                val maxIndex = (seekbarItemsCount - 1).coerceAtLeast(0)
                                val target = (value * maxIndex.toFloat())
                                    .roundToInt()
                                    .coerceIn(0, maxIndex)
                                if (usePageReader) {
                                    pageTurnCurrentPage = target
                                    if (pageReaderRendererRoute == NovelPageReaderRendererRoute.PAGE_TURN_RENDERER) {
                                        pageTurnRequestedPage = target
                                    } else {
                                        val virtualTarget = resolveComposePagerVirtualPageIndex(
                                            actualPageIndex = target,
                                            hasPreviousChapter = composePagerHasPreviousChapter,
                                        )
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(
                                                virtualTarget.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0)),
                                            )
                                        }
                                    }
                                } else {
                                    coroutineScope.launch {
                                        textListState.scrollToItem(target)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize(),
                    )
                }
            }

            if (shouldShowPersistentProgressLine(showReaderUi = showReaderUi)) {
                val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(lineColor.copy(alpha = 0.18f)),
                )
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .fillMaxWidth(readingProgressPercent.coerceIn(0, 100) / 100f)
                        .height(2.dp)
                        .background(lineColor),
                )
            }

            val panelSlideSpec = spring<androidx.compose.ui.unit.IntOffset>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            val panelFadeSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            val panelBackgroundColor = MaterialTheme.colorScheme
                .surfaceColorAtElevation(3.dp)
                .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
            // AppBar height (~64dp + status bar).
            AnimatedVisibility(
                visible = showReaderUi,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = panelSlideSpec,
                ) + fadeIn(animationSpec = panelFadeSpec),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = panelSlideSpec,
                ) + fadeOut(animationSpec = panelFadeSpec),
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            panelBackgroundColor,
                            RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                        )
                        .statusBarsPadding(),
                ) {
                    AppBar(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color.Transparent,
                        title = state.novel.title,
                        subtitle = state.chapter.name,
                        navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                        navigateUp = onBack,
                        actions = {
                            IconButton(onClick = onToggleBookmark) {
                                Icon(
                                    imageVector = if (state.chapter.bookmark) {
                                        Icons.Outlined.Bookmark
                                    } else {
                                        Icons.Outlined.BookmarkBorder
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                    )

                    AnimatedVisibility(visible = autoScrollExpanded) {
                        // Flat panel matching manga AutoScrollControlsPanel — no nested card.
                        val scheme = MaterialTheme.colorScheme
                        val isDark = isSystemInDarkTheme()
                        val valuePillBg = if (isDark) {
                            Color.White.copy(alpha = 0.10f)
                        } else {
                            Color.Black.copy(alpha = 0.06f)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (usePageReader) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_page_delay),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.primary,
                                    )
                                    Text(
                                        text = stringResource(
                                            AYMR.strings.reader_auto_scroll_page_time_fixed,
                                            state.readerSettings.autoScrollInterval,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(valuePillBg)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Slider(
                                    value = state.readerSettings.autoScrollInterval.toFloat().coerceIn(2f, 60f),
                                    onValueChange = {
                                        persistAutoScrollIntervalPreference(it.roundToInt())
                                    },
                                    valueRange = 2f..60f,
                                    steps = 58,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            appHaptics.tap()
                                            persistAutoScrollAdaptiveDelayPreference(
                                                !state.readerSettings.autoScrollAdaptiveDelay,
                                            )
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_adaptive_delay),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = state.readerSettings.autoScrollAdaptiveDelay,
                                        onCheckedChange = {
                                            appHaptics.tap()
                                            persistAutoScrollAdaptiveDelayPreference(it)
                                        },
                                    )
                                }
                                if (state.readerSettings.autoScrollAdaptiveDelay) {
                                    Text(
                                        text = stringResource(
                                            AYMR.strings.reader_auto_scroll_page_time,
                                            autoScrollPageDelayMsForCharacterCount(
                                                intervalSeconds = state.readerSettings.autoScrollInterval,
                                                characterCount =
                                                pageReaderCharacterCounts.getOrNull(pageReaderProgressPageIndex)
                                                    ?: 0,
                                                adaptiveEnabled = true,
                                            ) / 1000,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_speed),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.primary,
                                    )
                                    Text(
                                        text = "$autoScrollSpeed",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(valuePillBg)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Slider(
                                    value = autoScrollSpeed.toFloat(),
                                    onValueChange = {
                                        val newSpeed = it.roundToInt().coerceIn(1, 100)
                                        autoScrollSpeed = newSpeed
                                        persistAutoScrollIntervalPreference(
                                            interval = autoScrollSpeedToInterval(newSpeed),
                                        )
                                    },
                                    valueRange = 1f..100f,
                                    steps = 98,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        AYMR.strings.novel_reader_auto_scroll_chapter_end_behavior,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                )
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val behaviorEntries = novelAutoScrollChapterEndBehaviorEntries()
                                Box {
                                    Text(
                                        text = behaviorEntries[state.readerSettings.autoScrollChapterEndBehavior]
                                            ?: "",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.primary,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(valuePillBg)
                                            .clickable { dropdownExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                    ) {
                                        behaviorEntries.forEach { (behavior, label) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                },
                                                onClick = {
                                                    dropdownExpanded = false
                                                    persistAutoScrollChapterEndBehaviorPreference(behavior)
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.readerSettings.autoScrollChapterEndBehavior !=
                                NovelAutoScrollChapterEndBehavior.StopAtEnd
                            ) {
                                val currentPauseSec =
                                    (state.readerSettings.autoScrollEndPauseMs / 1000L).toInt()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(AYMR.strings.novel_reader_auto_scroll_end_pause),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.primary,
                                    )
                                    Text(
                                        text = stringResource(
                                            AYMR.strings.novel_reader_auto_scroll_end_pause_value,
                                            currentPauseSec,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSurface,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(valuePillBg)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Slider(
                                    value = currentPauseSec.toFloat().coerceIn(0f, 10f),
                                    onValueChange = {
                                        val seconds = it.roundToInt().coerceIn(0, 10)
                                        persistAutoScrollEndPauseMsPreference(seconds * 1000L)
                                    },
                                    valueRange = 0f..10f,
                                    steps = 10,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (autoScrollEnabled) {
                                            scheme.primary
                                        } else {
                                            scheme.primary.copy(alpha = 0.18f)
                                        },
                                    )
                                    .clickable {
                                        appHaptics.tap()
                                        val nextState = resolveAutoScrollUiStateOnToggle(
                                            currentEnabled = autoScrollEnabled,
                                            showReaderUi = showReaderUi,
                                            autoScrollExpanded = autoScrollExpanded,
                                        )
                                        autoScrollEnabled = nextState.autoScrollEnabled
                                        if (!nextState.autoScrollEnabled) {
                                            onCancelAutoScrollHandoff()
                                            autoScrollEndStableFrames = 0
                                            autoScrollEndDwellActive = false
                                        }
                                        onSetShowReaderUi(nextState.showReaderUi)
                                        autoScrollExpanded = nextState.autoScrollExpanded
                                    }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = if (autoScrollEnabled) {
                                        Icons.Outlined.Pause
                                    } else {
                                        Icons.Outlined.PlayArrow
                                    },
                                    contentDescription = stringResource(
                                        if (autoScrollEnabled) {
                                            AYMR.strings.novel_reader_auto_scroll_pause_description
                                        } else {
                                            AYMR.strings.novel_reader_auto_scroll_play_description
                                        },
                                    ),
                                    tint = if (autoScrollEnabled) scheme.onPrimary else scheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(
                                        if (autoScrollEnabled) {
                                            MR.strings.action_pause
                                        } else {
                                            MR.strings.action_start
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (autoScrollEnabled) scheme.onPrimary else scheme.primary,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        appHaptics.tap()
                                        readerPreferences.showAutoScrollFloatingButton().set(
                                            !state.readerSettings.showAutoScrollFloatingButton,
                                        )
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(AYMR.strings.reader_auto_scroll_floating_button),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = state.readerSettings.showAutoScrollFloatingButton,
                                    onCheckedChange = {
                                        appHaptics.tap()
                                        readerPreferences.showAutoScrollFloatingButton().set(it)
                                    },
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = {
                                appHaptics.tap()
                                autoScrollExpanded = !autoScrollExpanded
                            },
                        ) {
                            Icon(
                                imageVector = if (autoScrollExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = if (autoScrollExpanded) {
                                    "Collapse auto-scroll"
                                } else {
                                    "Expand auto-scroll"
                                },
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }

            // Bottom navigation in LNReader-like style
            AnimatedVisibility(
                visible = showReaderUi,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = panelSlideSpec,
                ) + fadeIn(animationSpec = panelFadeSpec),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = panelSlideSpec,
                ) + fadeOut(animationSpec = panelFadeSpec),
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            panelBackgroundColor,
                            RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                        ),
                ) {
                    if (state.readerSettings.ttsEnabled) {
                        NovelReaderTtsControls(
                            uiState = state.ttsUiState,
                            onTogglePlayback = { onToggleTtsPlayback(currentTtsStartRequest) },
                            onStop = onStopTtsPlayback,
                            onSkipPrevious = onSkipPreviousTts,
                            onSkipNext = onSkipNextTts,
                            onSetEnginePackage = onSetTtsEnginePackage,
                            onSetVoiceId = onSetTtsVoiceId,
                            onSetLocaleTag = onSetTtsLocaleTag,
                            onSetSpeechRate = onSetTtsSpeechRate,
                            onSetPitch = onSetTtsPitch,
                            onDisableTts = onDisableTts,
                            onPreviewVoice = onPreviewTtsVoice,
                            onStopVoicePreview = onStopTtsVoicePreview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = MaterialTheme.padding.medium,
                                    end = MaterialTheme.padding.medium,
                                    top = MaterialTheme.padding.medium,
                                ),
                        )

                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(top = MaterialTheme.padding.medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.small,
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = { openPreviousChapterFromReader() },
                            enabled = state.previousChapterId != null && onOpenPreviousChapter != null,
                        ) {
                            Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                appHaptics.tap()
                                showChapterList = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = stringResource(MR.strings.chapters),
                            )
                        }
                        IconButton(
                            onClick = {
                                val chapterUrl =
                                    state.chapterWebUrl
                                        ?: state.chapter.url.takeIf { it.startsWith("http", ignoreCase = true) }
                                        ?: state.novel.url.takeIf { it.startsWith("http", ignoreCase = true) }
                                if (!chapterUrl.isNullOrBlank()) {
                                    context.startActivity(
                                        WebViewActivity.newIntent(
                                            context = context,
                                            url = chapterUrl,
                                            sourceId = state.novel.source,
                                            title = state.novel.title,
                                        ),
                                    )
                                }
                            },
                        ) {
                            Icon(imageVector = Icons.Filled.Public, contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (showWebView) {
                                        webViewInstance?.scrollTo(0, 0)
                                    } else if (usePageReader) {
                                        pagerState.animateScrollToPage(0)
                                    } else {
                                        textListState.animateScrollToItem(0)
                                    }
                                }
                            },
                        ) {
                            Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = null)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                        }
                        if (onOpenDictionaryHistory != null) {
                            val dictionaryQuickAccess by remember {
                                Injekt.get<NovelReaderPreferences>().novelDictionaryQuickAccess()
                            }
                                .collectAsState()
                            if (dictionaryQuickAccess) {
                                IconButton(onClick = { onOpenDictionaryHistory() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = stringResource(
                                            AYMR.strings.novel_reader_dictionary_history,
                                        ),
                                    )
                                }
                            }
                        }
                        LatticeCarrierSlot(LatticeCarrier.NOVEL)
                        if (ttsPlacement.showFooterEntry) {
                            IconButton(onClick = { showTtsBehaviorSettings = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.SettingsVoice,
                                    contentDescription = stringResource(
                                        AYMR.strings.novel_reader_tts_behavior_settings,
                                    ),
                                )
                            }
                        }
                        if (state.readerSettings.geminiEnabled) {
                            IconButton(onClick = { showGeminiDialog = true }) {
                                Text(
                                    text = if (state.isGeminiTranslating) {
                                        stringResource(AYMR.strings.novel_reader_gemini_button_active)
                                    } else {
                                        stringResource(AYMR.strings.novel_reader_gemini_button)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        if (state.readerSettings.googleTranslationEnabled) {
                            IconButton(onClick = { showGoogleDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = stringResource(AYMR.strings.novel_reader_google_translate),
                                    tint = if (state.isGoogleTranslating ||
                                        state.hasGoogleTranslationCache ||
                                        state.isGoogleTranslationVisible
                                    ) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    },
                                )
                            }
                        }
                        IconButton(
                            onClick = { openNextChapterFromReader() },
                            enabled = state.nextChapterId != null && onOpenNextChapter != null,
                        ) {
                            Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                    }
                    Spacer(
                        modifier = Modifier.padding(bottom = with(density) { navigationBarHeight.toDp() }),
                    )
                }
            }

            SelectedTextTranslationOverlay(
                state = state,
                onTranslate = onTranslateSelectedText,
                onRetry = onRetrySelectedTextTranslation,
                onDismiss = onDismissSelectedTextTranslation,
                onLookupDefinition = onLookupSelectedTextDefinition,
                onRetryDictionary = onRetryNovelDictionary,
                onDismissDictionary = onDismissNovelDictionary,
                onPlayPronunciation = onPlaySelectedTextPronunciation,
                modifier = Modifier.align(Alignment.BottomEnd),
            )

            if (autoScrollEnabled) {
                autoScrollWasUsed = true
            }

            if (flashOnPageChange && eInkProfile.isEnabled) {
                DisplayRefreshHost(
                    hostState = displayRefreshHost,
                )
            }

            NovelReaderAutoScrollEndOverlay(
                visible = autoScrollEndDwellActive && autoScrollEnabled && !showReaderUi,
                nextChapterName = state.nextChapterName,
                remainingSeconds = autoScrollEndDwellRemainingSeconds,
                isEInkMode = eInkProfile.isEnabled || isEInkMode,
                onGoNow = {
                    autoScrollEndDwellActive = false
                    handleAutoScrollChapterEnd()
                },
                onStop = {
                    autoScrollEnabled = false
                    autoScrollEndDwellActive = false
                    autoScrollEndStableFrames = 0
                    onCancelAutoScrollHandoff()
                },
                onStay = {
                    autoScrollEnabled = false
                    autoScrollEndDwellActive = false
                    autoScrollEndStableFrames = 0
                    onCancelAutoScrollHandoff()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 72.dp),
            )

            AutoScrollActionFab(
                autoScrollEnabled = autoScrollEnabled,
                showFab = state.readerSettings.showAutoScrollFloatingButton && !showReaderUi,
                contentDescription = stringResource(
                    if (autoScrollEnabled) {
                        AYMR.strings.novel_reader_auto_scroll_pause_description
                    } else {
                        AYMR.strings.novel_reader_auto_scroll_play_description
                    },
                ),
                longClickLabel = stringResource(AYMR.strings.novel_reader_auto_scroll_settings_description),
                onClick = {
                    autoScrollEnabled = !autoScrollEnabled
                    if (autoScrollEnabled) {
                        onSetShowReaderUi(false)
                    } else {
                        onCancelAutoScrollHandoff()
                        autoScrollEndStableFrames = 0
                        autoScrollEndDwellActive = false
                    }
                },
                onLongClick = { autoScrollExpanded = !autoScrollExpanded },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )

            // Dialogs & sheets host
            NovelReaderDialogHost(
                showSettings = showSettings,
                showChapterList = showChapterList,
                showTtsBehaviorSettings = showTtsBehaviorSettings,
                showGeminiDialog = showGeminiDialog,
                showGoogleDialog = showGoogleDialog,
                translationSwitchRequest = translationSwitchRequest,
                state = state,
                showWebView = showWebView,
                usePageReader = usePageReader,
                ttsPlacement = ttsPlacement,
                actions = NovelReaderDialogActions(
                    onDismissSettings = { showSettings = false },
                    onDismissChapterList = { showChapterList = false },
                    onOpenBottomSheet = onOpenBottomSheet,
                    onOpenChapter = onOpenChapter,
                    onDownloadChapter = onDownloadChapter,
                    onDismissTtsBehaviorSettings = { showTtsBehaviorSettings = false },
                    onDismissGeminiDialog = { showGeminiDialog = false },
                    onDismissGoogleDialog = { showGoogleDialog = false },
                    onDismissTranslationSwitchRequest = { translationSwitchRequest = null },
                    requestGeminiTranslationStart = { requestGeminiTranslationStart() },
                    requestGoogleTranslationStart = { requestGoogleTranslationStart() },
                    onPrepareWholeBook = onPrepareWholeBook,
                    onStopGeminiTranslation = onStopGeminiTranslation,
                    onToggleGeminiTranslationVisibility = onToggleGeminiTranslationVisibility,
                    onClearGeminiTranslation = onClearGeminiTranslation,
                    onClearAllGeminiTranslationCache = onClearAllGeminiTranslationCache,
                    onAddAiTranslationLog = onAddAiTranslationLog,
                    onClearGeminiLogs = onClearGeminiLogs,
                    onSetGeminiApiKey = onSetGeminiApiKey,
                    onSetGeminiModel = onSetGeminiModel,
                    onSetGeminiBatchSize = onSetGeminiBatchSize,
                    onSetGeminiConcurrency = onSetGeminiConcurrency,
                    onSetGeminiRelaxedMode = onSetGeminiRelaxedMode,
                    onSetGeminiDisableCache = onSetGeminiDisableCache,
                    onSetGeminiReasoningEffort = onSetGeminiReasoningEffort,
                    onSetGeminiBudgetTokens = onSetGeminiBudgetTokens,
                    onSetGeminiTemperature = onSetGeminiTemperature,
                    onSetGeminiTopP = onSetGeminiTopP,
                    onSetGeminiTopK = onSetGeminiTopK,
                    onSetGeminiPromptMode = onSetGeminiPromptMode,
                    onSetGeminiSourceLang = onSetGeminiSourceLang,
                    onSetGeminiTargetLang = onSetGeminiTargetLang,
                    onSetGeminiStylePreset = onSetGeminiStylePreset,
                    onSetGeminiEnabledPromptModifiers = onSetGeminiEnabledPromptModifiers,
                    onSetGeminiCustomPromptModifier = onSetGeminiCustomPromptModifier,
                    onSetGeminiAutoTranslateEnglishSource = onSetGeminiAutoTranslateEnglishSource,
                    onSetGeminiPrefetchNextChapterTranslation = onSetGeminiPrefetchNextChapterTranslation,
                    onSetGeminiPrivateUnlocked = onSetGeminiPrivateUnlocked,
                    onSetGeminiPrivatePythonLikeMode = onSetGeminiPrivatePythonLikeMode,
                    onSetTranslationProvider = onSetTranslationProvider,
                    onSetOpenRouterBaseUrl = onSetOpenRouterBaseUrl,
                    onSetOpenRouterApiKey = onSetOpenRouterApiKey,
                    onSetOpenRouterModel = onSetOpenRouterModel,
                    onRefreshOpenRouterModels = onRefreshOpenRouterModels,
                    onTestOpenRouterConnection = onTestOpenRouterConnection,
                    onSetDeepSeekBaseUrl = onSetDeepSeekBaseUrl,
                    onSetDeepSeekApiKey = onSetDeepSeekApiKey,
                    onSetDeepSeekModel = onSetDeepSeekModel,
                    onRefreshDeepSeekModels = onRefreshDeepSeekModels,
                    onTestDeepSeekConnection = onTestDeepSeekConnection,
                    onSetMistralBaseUrl = onSetMistralBaseUrl,
                    onSetMistralApiKey = onSetMistralApiKey,
                    onSetMistralModel = onSetMistralModel,
                    onRefreshMistralModels = onRefreshMistralModels,
                    onTestMistralConnection = onTestMistralConnection,
                    onSetNvidiaBaseUrl = onSetNvidiaBaseUrl,
                    onSetNvidiaApiKey = onSetNvidiaApiKey,
                    onSetNvidiaModel = onSetNvidiaModel,
                    onRefreshNvidiaModels = onRefreshNvidiaModels,
                    onTestNvidiaConnection = onTestNvidiaConnection,
                    onSetOllamaCloudBaseUrl = onSetOllamaCloudBaseUrl,
                    onSetOllamaCloudApiKey = onSetOllamaCloudApiKey,
                    onSetOllamaCloudModel = onSetOllamaCloudModel,
                    onRefreshOllamaCloudModels = onRefreshOllamaCloudModels,
                    onTestOllamaCloudConnection = onTestOllamaCloudConnection,
                    onStopGoogleTranslation = onStopGoogleTranslation,
                    onResumeGoogleTranslation = onResumeGoogleTranslation,
                    onToggleGoogleTranslationVisibility = onToggleGoogleTranslationVisibility,
                    onClearGoogleTranslation = onClearGoogleTranslation,
                    onSetGoogleTranslationAutoStart = onSetGoogleTranslationAutoStart,
                    onSetGoogleTranslationSourceLang = onSetGoogleTranslationSourceLang,
                    onSetGoogleTranslationTargetLang = onSetGoogleTranslationTargetLang,
                    onStartGeminiTranslation = onStartGeminiTranslation,
                    onStartGoogleTranslation = onStartGoogleTranslation,
                ),
            )
        }
    }
}

@Composable
private fun NovelReaderAutoScrollEndOverlay(
    visible: Boolean,
    nextChapterName: String?,
    remainingSeconds: Int,
    isEInkMode: Boolean,
    onGoNow: () -> Unit,
    onStop: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (isEInkMode) fadeIn(animationSpec = tween(0)) else fadeIn(),
        exit = if (isEInkMode) fadeOut(animationSpec = tween(0)) else fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            ),
            tonalElevation = 8.dp,
            shadowElevation = if (isEInkMode) 0.dp else 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (isEInkMode) {
                        stringResource(AYMR.strings.novel_reader_auto_scroll_next_static_eink)
                    } else {
                        stringResource(
                            AYMR.strings.novel_reader_auto_scroll_next_countdown,
                            remainingSeconds,
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!nextChapterName.isNullOrBlank()) {
                    Text(
                        text = stringResource(
                            AYMR.strings.novel_reader_auto_scroll_next_chapter_named,
                            nextChapterName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onStay) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_stay_here))
                    }
                    TextButton(onClick = onStop) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_stop_here))
                    }
                    TextButton(onClick = onGoNow) {
                        Text(text = stringResource(AYMR.strings.novel_reader_auto_scroll_go_now))
                    }
                }
            }
        }
    }
}

/**
 * Host for every reader dialog and sheet.
 *
 * Its callbacks travel in [NovelReaderDialogActions] instead of as individual parameters: a
 * composable with ninety parameters makes the compiler emit a change-tracking prologue so large
 * that ART refuses to JIT-compile the method ("Method exceeds compiler instruction limit"), and
 * an interpreted reader is what made scrolling stutter.
 */
@Composable
private fun NovelReaderDialogHost(
    showSettings: Boolean,
    showChapterList: Boolean,
    showTtsBehaviorSettings: Boolean,
    showGeminiDialog: Boolean,
    showGoogleDialog: Boolean,
    translationSwitchRequest: TranslationSwitchRequest?,
    state: NovelReaderScreenModel.State.Success,
    showWebView: Boolean,
    usePageReader: Boolean,
    ttsPlacement: NovelReaderTtsSettingsPlacementSnapshot,
    actions: NovelReaderDialogActions,
) {
    val onDismissSettings = actions.onDismissSettings
    val onDismissChapterList = actions.onDismissChapterList
    val onOpenBottomSheet = actions.onOpenBottomSheet
    val onOpenChapter = actions.onOpenChapter
    val onDownloadChapter = actions.onDownloadChapter
    val onDismissTtsBehaviorSettings = actions.onDismissTtsBehaviorSettings
    val onDismissGeminiDialog = actions.onDismissGeminiDialog
    val onDismissGoogleDialog = actions.onDismissGoogleDialog
    val onDismissTranslationSwitchRequest = actions.onDismissTranslationSwitchRequest
    val requestGeminiTranslationStart = actions.requestGeminiTranslationStart
    val requestGoogleTranslationStart = actions.requestGoogleTranslationStart
    val onPrepareWholeBook = actions.onPrepareWholeBook
    val onStopGeminiTranslation = actions.onStopGeminiTranslation
    val onToggleGeminiTranslationVisibility = actions.onToggleGeminiTranslationVisibility
    val onClearGeminiTranslation = actions.onClearGeminiTranslation
    val onClearAllGeminiTranslationCache = actions.onClearAllGeminiTranslationCache
    val onAddAiTranslationLog = actions.onAddAiTranslationLog
    val onClearGeminiLogs = actions.onClearGeminiLogs
    val onSetGeminiApiKey = actions.onSetGeminiApiKey
    val onSetGeminiModel = actions.onSetGeminiModel
    val onSetGeminiBatchSize = actions.onSetGeminiBatchSize
    val onSetGeminiConcurrency = actions.onSetGeminiConcurrency
    val onSetGeminiRelaxedMode = actions.onSetGeminiRelaxedMode
    val onSetGeminiDisableCache = actions.onSetGeminiDisableCache
    val onSetGeminiReasoningEffort = actions.onSetGeminiReasoningEffort
    val onSetGeminiBudgetTokens = actions.onSetGeminiBudgetTokens
    val onSetGeminiTemperature = actions.onSetGeminiTemperature
    val onSetGeminiTopP = actions.onSetGeminiTopP
    val onSetGeminiTopK = actions.onSetGeminiTopK
    val onSetGeminiPromptMode = actions.onSetGeminiPromptMode
    val onSetGeminiSourceLang = actions.onSetGeminiSourceLang
    val onSetGeminiTargetLang = actions.onSetGeminiTargetLang
    val onSetGeminiStylePreset = actions.onSetGeminiStylePreset
    val onSetGeminiEnabledPromptModifiers = actions.onSetGeminiEnabledPromptModifiers
    val onSetGeminiCustomPromptModifier = actions.onSetGeminiCustomPromptModifier
    val onSetGeminiAutoTranslateEnglishSource = actions.onSetGeminiAutoTranslateEnglishSource
    val onSetGeminiPrefetchNextChapterTranslation = actions.onSetGeminiPrefetchNextChapterTranslation
    val onSetGeminiPrivateUnlocked = actions.onSetGeminiPrivateUnlocked
    val onSetGeminiPrivatePythonLikeMode = actions.onSetGeminiPrivatePythonLikeMode
    val onSetTranslationProvider = actions.onSetTranslationProvider
    val onSetOpenRouterBaseUrl = actions.onSetOpenRouterBaseUrl
    val onSetOpenRouterApiKey = actions.onSetOpenRouterApiKey
    val onSetOpenRouterModel = actions.onSetOpenRouterModel
    val onRefreshOpenRouterModels = actions.onRefreshOpenRouterModels
    val onTestOpenRouterConnection = actions.onTestOpenRouterConnection
    val onSetDeepSeekBaseUrl = actions.onSetDeepSeekBaseUrl
    val onSetDeepSeekApiKey = actions.onSetDeepSeekApiKey
    val onSetDeepSeekModel = actions.onSetDeepSeekModel
    val onRefreshDeepSeekModels = actions.onRefreshDeepSeekModels
    val onTestDeepSeekConnection = actions.onTestDeepSeekConnection
    val onSetMistralBaseUrl = actions.onSetMistralBaseUrl
    val onSetMistralApiKey = actions.onSetMistralApiKey
    val onSetMistralModel = actions.onSetMistralModel
    val onRefreshMistralModels = actions.onRefreshMistralModels
    val onTestMistralConnection = actions.onTestMistralConnection
    val onSetNvidiaBaseUrl = actions.onSetNvidiaBaseUrl
    val onSetNvidiaApiKey = actions.onSetNvidiaApiKey
    val onSetNvidiaModel = actions.onSetNvidiaModel
    val onRefreshNvidiaModels = actions.onRefreshNvidiaModels
    val onTestNvidiaConnection = actions.onTestNvidiaConnection
    val onSetOllamaCloudBaseUrl = actions.onSetOllamaCloudBaseUrl
    val onSetOllamaCloudApiKey = actions.onSetOllamaCloudApiKey
    val onSetOllamaCloudModel = actions.onSetOllamaCloudModel
    val onRefreshOllamaCloudModels = actions.onRefreshOllamaCloudModels
    val onTestOllamaCloudConnection = actions.onTestOllamaCloudConnection
    val onStopGoogleTranslation = actions.onStopGoogleTranslation
    val onResumeGoogleTranslation = actions.onResumeGoogleTranslation
    val onToggleGoogleTranslationVisibility = actions.onToggleGoogleTranslationVisibility
    val onClearGoogleTranslation = actions.onClearGoogleTranslation
    val onSetGoogleTranslationAutoStart = actions.onSetGoogleTranslationAutoStart
    val onSetGoogleTranslationSourceLang = actions.onSetGoogleTranslationSourceLang
    val onSetGoogleTranslationTargetLang = actions.onSetGoogleTranslationTargetLang
    val onStartGeminiTranslation = actions.onStartGeminiTranslation
    val onStartGoogleTranslation = actions.onStartGoogleTranslation
    if (showSettings) {
        NovelReaderSettingsDialog(
            sourceId = state.novel.source,
            currentWebViewActive = showWebView,
            currentPageReaderActive = usePageReader,
            onDismissRequest = onDismissSettings,
            onPrepareBook = if (state.bookMode.isEnabled) onPrepareWholeBook else null,
            bookModeActive = state.bookMode.isEnabled,
            prepareBookInProgress = state.bookMode.isPreparingWholeBook,
            preparedChapterCount = state.bookMode.preparedChapterCount,
            totalChapterCount = state.bookMode.totalChapterCount,
        )
    }
    if (showChapterList) {
        LaunchedEffect(Unit) {
            onOpenBottomSheet()
        }
        val currentChapterId = state.chapter.id
        val chapters = if (state.fullChapterOrderList.isNotEmpty()) {
            state.fullChapterOrderList
        } else {
            state.chapterOrderList
        }
        val chapterListItems = chapters.map { chapter ->
            ReaderChapterListItem(
                id = chapter.id,
                title = chapter.name,
                dateText = chapter.dateUpload.takeIf { it > 0 }?.let {
                    relativeDateTimeText(it)
                },
                scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
                isCurrent = chapter.id == currentChapterId,
            )
        }
        ReaderChapterListSheet(
            items = chapterListItems,
            onDismissRequest = onDismissChapterList,
            onChapterClick = { chapterId ->
                onDismissChapterList()
                if (chapterId != state.chapter.id) {
                    onOpenChapter?.invoke(chapterId)
                }
            },
            onDownloadClick = { chapterId ->
                onDownloadChapter?.invoke(chapterId)
            },
        )
    }
    if (showTtsBehaviorSettings && ttsPlacement.showFooterEntry) {
        NovelReaderTtsBehaviorSettingsDialog(
            sourceId = state.novel.source,
            onDismissRequest = onDismissTtsBehaviorSettings,
        )
    }
    if (showGeminiDialog && state.readerSettings.geminiEnabled) {
        GeminiTranslationDialog(
            readerSettings = state.readerSettings,
            isTranslating = state.isGeminiTranslating,
            translationProgress = state.geminiTranslationProgress,
            isVisible = state.isGeminiTranslationVisible,
            hasCache = state.hasGeminiTranslationCache,
            logs = state.geminiLogs,
            onStart = requestGeminiTranslationStart,
            onStop = onStopGeminiTranslation,
            onToggleVisibility = onToggleGeminiTranslationVisibility,
            onClear = onClearGeminiTranslation,
            onClearAllCache = onClearAllGeminiTranslationCache,
            onAddLog = onAddAiTranslationLog,
            onClearLogs = onClearGeminiLogs,
            onSetGeminiApiKey = onSetGeminiApiKey,
            onSetGeminiModel = onSetGeminiModel,
            onSetGeminiBatchSize = onSetGeminiBatchSize,
            onSetGeminiConcurrency = onSetGeminiConcurrency,
            onSetGeminiRelaxedMode = onSetGeminiRelaxedMode,
            onSetGeminiDisableCache = onSetGeminiDisableCache,
            onSetGeminiReasoningEffort = onSetGeminiReasoningEffort,
            onSetGeminiBudgetTokens = onSetGeminiBudgetTokens,
            onSetGeminiTemperature = onSetGeminiTemperature,
            onSetGeminiTopP = onSetGeminiTopP,
            onSetGeminiTopK = onSetGeminiTopK,
            onSetGeminiPromptMode = onSetGeminiPromptMode,
            onSetGeminiSourceLang = onSetGeminiSourceLang,
            onSetGeminiTargetLang = onSetGeminiTargetLang,
            onSetGeminiStylePreset = onSetGeminiStylePreset,
            onSetGeminiEnabledPromptModifiers = onSetGeminiEnabledPromptModifiers,
            onSetGeminiCustomPromptModifier = onSetGeminiCustomPromptModifier,
            onSetGeminiAutoTranslateEnglishSource = onSetGeminiAutoTranslateEnglishSource,
            onSetGeminiPrefetchNextChapterTranslation = onSetGeminiPrefetchNextChapterTranslation,
            onSetGeminiPrivateUnlocked = onSetGeminiPrivateUnlocked,
            onSetGeminiPrivatePythonLikeMode = onSetGeminiPrivatePythonLikeMode,
            onSetTranslationProvider = onSetTranslationProvider,
            onSetOpenRouterBaseUrl = onSetOpenRouterBaseUrl,
            onSetOpenRouterApiKey = onSetOpenRouterApiKey,
            onSetOpenRouterModel = onSetOpenRouterModel,
            onRefreshOpenRouterModels = onRefreshOpenRouterModels,
            onTestOpenRouterConnection = onTestOpenRouterConnection,
            onSetDeepSeekBaseUrl = onSetDeepSeekBaseUrl,
            onSetDeepSeekApiKey = onSetDeepSeekApiKey,
            onSetDeepSeekModel = onSetDeepSeekModel,
            onRefreshDeepSeekModels = onRefreshDeepSeekModels,
            onTestDeepSeekConnection = onTestDeepSeekConnection,
            onSetMistralBaseUrl = onSetMistralBaseUrl,
            onSetMistralApiKey = onSetMistralApiKey,
            onSetMistralModel = onSetMistralModel,
            onRefreshMistralModels = onRefreshMistralModels,
            onTestMistralConnection = onTestMistralConnection,
            onSetNvidiaBaseUrl = onSetNvidiaBaseUrl,
            onSetNvidiaApiKey = onSetNvidiaApiKey,
            onSetNvidiaModel = onSetNvidiaModel,
            onRefreshNvidiaModels = onRefreshNvidiaModels,
            onTestNvidiaConnection = onTestNvidiaConnection,
            onSetOllamaCloudBaseUrl = onSetOllamaCloudBaseUrl,
            onSetOllamaCloudApiKey = onSetOllamaCloudApiKey,
            onSetOllamaCloudModel = onSetOllamaCloudModel,
            onRefreshOllamaCloudModels = onRefreshOllamaCloudModels,
            onTestOllamaCloudConnection = onTestOllamaCloudConnection,
            openRouterModels = state.openRouterModelIds,
            isOpenRouterModelsLoading = state.isOpenRouterModelsLoading,
            isTestingOpenRouterConnection = state.isTestingOpenRouterConnection,
            openRouterApiTestStatus = state.openRouterApiTestStatus,
            openRouterApiTestMessage = state.openRouterApiTestMessage,
            deepSeekModels = state.deepSeekModelIds,
            isDeepSeekModelsLoading = state.isDeepSeekModelsLoading,
            isTestingDeepSeekConnection = state.isTestingDeepSeekConnection,
            deepSeekApiTestStatus = state.deepSeekApiTestStatus,
            deepSeekApiTestMessage = state.deepSeekApiTestMessage,
            mistralModels = state.mistralModelIds,
            isMistralModelsLoading = state.isMistralModelsLoading,
            isTestingMistralConnection = state.isTestingMistralConnection,
            mistralApiTestStatus = state.mistralApiTestStatus,
            mistralApiTestMessage = state.mistralApiTestMessage,
            nvidiaModels = state.nvidiaModelIds,
            isNvidiaModelsLoading = state.isNvidiaModelsLoading,
            isTestingNvidiaConnection = state.isTestingNvidiaConnection,
            nvidiaApiTestStatus = state.nvidiaApiTestStatus,
            nvidiaApiTestMessage = state.nvidiaApiTestMessage,
            ollamaCloudModels = state.ollamaCloudModelIds,
            isOllamaCloudModelsLoading = state.isOllamaCloudModelsLoading,
            isTestingOllamaCloudConnection = state.isTestingOllamaCloudConnection,
            ollamaCloudApiTestStatus = state.ollamaCloudApiTestStatus,
            ollamaCloudApiTestMessage = state.ollamaCloudApiTestMessage,
            onDismiss = onDismissGeminiDialog,
        )
    }
    if (showGoogleDialog && state.readerSettings.googleTranslationEnabled) {
        GoogleTranslationDialog(
            readerSettings = state.readerSettings,
            isTranslating = state.isGoogleTranslating,
            translationProgress = state.googleTranslationProgress,
            translationPhase = state.translationPhase,
            isVisible = state.isGoogleTranslationVisible,
            hasCache = state.hasGoogleTranslationCache,
            onStart = requestGoogleTranslationStart,
            onStop = onStopGoogleTranslation,
            onResume = onResumeGoogleTranslation,
            onToggleVisibility = onToggleGoogleTranslationVisibility,
            onClear = onClearGoogleTranslation,
            onSetAutoStart = onSetGoogleTranslationAutoStart,
            onSetSourceLang = onSetGoogleTranslationSourceLang,
            onSetTargetLang = onSetGoogleTranslationTargetLang,
            onDismiss = onDismissGoogleDialog,
        )
    }
    translationSwitchRequest?.let { switchRequest ->
        val fromLabel = when (switchRequest.from) {
            TranslationKind.Gemini -> "Gemini"
            TranslationKind.Google -> stringResource(AYMR.strings.novel_reader_google_translate)
        }
        val toLabel = when (switchRequest.to) {
            TranslationKind.Gemini -> "Gemini"
            TranslationKind.Google -> stringResource(AYMR.strings.novel_reader_google_translate)
        }
        AlertDialog(
            onDismissRequest = onDismissTranslationSwitchRequest,
            title = {
                Text(
                    text = stringResource(
                        AYMR.strings.novel_reader_google_translate_switch_confirm,
                        toLabel,
                        fromLabel,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (switchRequest.from) {
                            TranslationKind.Gemini -> onClearGeminiTranslation()
                            TranslationKind.Google -> onClearGoogleTranslation()
                        }
                        onDismissTranslationSwitchRequest()
                        when (switchRequest.to) {
                            TranslationKind.Gemini -> onStartGeminiTranslation()
                            TranslationKind.Google -> onStartGoogleTranslation()
                        }
                    },
                ) {
                    Text(text = stringResource(AYMR.strings.novel_reader_ai_translator_switch))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissTranslationSwitchRequest) {
                    Text(text = stringResource(AYMR.strings.novel_reader_ai_translator_cancel))
                }
            },
        )
    }
}

@Composable
private fun rememberBatteryLevel(context: Context): State<Int> {
    val batteryLevelState = remember(context) { mutableIntStateOf(readBatteryLevel(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                batteryLevelState.intValue = readBatteryLevel(context, intent)
            }
        }
        val stickyIntent = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        batteryLevelState.intValue = readBatteryLevel(context, stickyIntent)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return batteryLevelState
}

@Composable
private fun rememberCurrentTimeText(context: Context): State<String> {
    val timeState = remember(context) { mutableStateOf(currentTimeString(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                timeState.value = currentTimeString(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        timeState.value = currentTimeString(context)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return timeState
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> safeEnum(value: Any?, fallback: T): T {
    return if (value != null && fallback::class.java.isInstance(value)) {
        value as T
    } else {
        fallback
    }
}

/**
 * Every callback the reader dialogs need, grouped into one parameter.
 *
 * Grouping keeps both the host and its caller inside the compiler's per-method instruction
 * budget; passing these one by one is what pushed [NovelReaderScreen] out of it.
 */
@Immutable
internal data class NovelReaderDialogActions(
    val onDismissSettings: () -> Unit,
    val onDismissChapterList: () -> Unit,
    val onOpenBottomSheet: () -> Unit,
    val onOpenChapter: ((Long) -> Unit)?,
    val onDownloadChapter: ((Long) -> Unit)?,
    val onDismissTtsBehaviorSettings: () -> Unit,
    val onDismissGeminiDialog: () -> Unit,
    val onDismissGoogleDialog: () -> Unit,
    val onDismissTranslationSwitchRequest: () -> Unit,
    val requestGeminiTranslationStart: () -> Unit,
    val requestGoogleTranslationStart: () -> Unit,
    val onPrepareWholeBook: () -> Unit,
    val onStopGeminiTranslation: () -> Unit,
    val onToggleGeminiTranslationVisibility: () -> Unit,
    val onClearGeminiTranslation: () -> Unit,
    val onClearAllGeminiTranslationCache: () -> Unit,
    val onAddAiTranslationLog: (String) -> Unit,
    val onClearGeminiLogs: () -> Unit,
    val onSetGeminiApiKey: (String) -> Unit,
    val onSetGeminiModel: (String) -> Unit,
    val onSetGeminiBatchSize: (Int) -> Unit,
    val onSetGeminiConcurrency: (Int) -> Unit,
    val onSetGeminiRelaxedMode: (Boolean) -> Unit,
    val onSetGeminiDisableCache: (Boolean) -> Unit,
    val onSetGeminiReasoningEffort: (String) -> Unit,
    val onSetGeminiBudgetTokens: (Int) -> Unit,
    val onSetGeminiTemperature: (Float) -> Unit,
    val onSetGeminiTopP: (Float) -> Unit,
    val onSetGeminiTopK: (Int) -> Unit,
    val onSetGeminiPromptMode: (GeminiPromptMode) -> Unit,
    val onSetGeminiSourceLang: (String) -> Unit,
    val onSetGeminiTargetLang: (String) -> Unit,
    val onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit,
    val onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit,
    val onSetGeminiCustomPromptModifier: (String) -> Unit,
    val onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit,
    val onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit,
    val onSetGeminiPrivateUnlocked: (Boolean) -> Unit,
    val onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit,
    val onSetTranslationProvider: (NovelTranslationProvider) -> Unit,
    val onSetOpenRouterBaseUrl: (String) -> Unit,
    val onSetOpenRouterApiKey: (String) -> Unit,
    val onSetOpenRouterModel: (String) -> Unit,
    val onRefreshOpenRouterModels: () -> Unit,
    val onTestOpenRouterConnection: () -> Unit,
    val onSetDeepSeekBaseUrl: (String) -> Unit,
    val onSetDeepSeekApiKey: (String) -> Unit,
    val onSetDeepSeekModel: (String) -> Unit,
    val onRefreshDeepSeekModels: () -> Unit,
    val onTestDeepSeekConnection: () -> Unit,
    val onSetMistralBaseUrl: (String) -> Unit,
    val onSetMistralApiKey: (String) -> Unit,
    val onSetMistralModel: (String) -> Unit,
    val onRefreshMistralModels: () -> Unit,
    val onTestMistralConnection: () -> Unit,
    val onSetNvidiaBaseUrl: (String) -> Unit,
    val onSetNvidiaApiKey: (String) -> Unit,
    val onSetNvidiaModel: (String) -> Unit,
    val onRefreshNvidiaModels: () -> Unit,
    val onTestNvidiaConnection: () -> Unit,
    val onSetOllamaCloudBaseUrl: (String) -> Unit,
    val onSetOllamaCloudApiKey: (String) -> Unit,
    val onSetOllamaCloudModel: (String) -> Unit,
    val onRefreshOllamaCloudModels: () -> Unit,
    val onTestOllamaCloudConnection: () -> Unit,
    val onStopGoogleTranslation: () -> Unit,
    val onResumeGoogleTranslation: () -> Unit,
    val onToggleGoogleTranslationVisibility: () -> Unit,
    val onClearGoogleTranslation: () -> Unit,
    val onSetGoogleTranslationAutoStart: (Boolean) -> Unit,
    val onSetGoogleTranslationSourceLang: (String) -> Unit,
    val onSetGoogleTranslationTargetLang: (String) -> Unit,
    val onStartGeminiTranslation: () -> Unit,
    val onStartGoogleTranslation: () -> Unit,
)

/**
 * Every callback [NovelReaderScreen] needs, grouped into a single parameter.
 *
 * Compose emits a change-tracking prologue for each parameter, and with more than a hundred of
 * them the generated method grew past the runtime's per-method instruction budget: ART logged
 * "Method exceeds compiler instruction limit" and ran the whole reader interpreted, which is what
 * made scrolling stutter and reloads visible.
 */
@Immutable
data class NovelReaderScreenActions(
    val onBack: () -> Unit,
    val onReadingProgress: (currentIndex: Int, totalItems: Int, persistedProgress: Long?) -> Unit,
    val onSeekBookModeProgress: (Float) -> Unit = {},
    val onToggleBookmark: () -> Unit = {},
    val onOpenDictionaryHistory: (() -> Unit)? = null,
    val onStartGeminiTranslation: () -> Unit = {},
    val onStopGeminiTranslation: () -> Unit = {},
    val onToggleGeminiTranslationVisibility: () -> Unit = {},
    val onClearGeminiTranslation: () -> Unit = {},
    val onClearAllGeminiTranslationCache: () -> Unit = {},
    val onAddAiTranslationLog: (String) -> Unit = {},
    val onClearGeminiLogs: () -> Unit = {},
    val onSetGeminiApiKey: (String) -> Unit = {},
    val onSetGeminiModel: (String) -> Unit = {},
    val onSetGeminiBatchSize: (Int) -> Unit = {},
    val onSetGeminiConcurrency: (Int) -> Unit = {},
    val onSetGeminiRelaxedMode: (Boolean) -> Unit = {},
    val onSetGeminiDisableCache: (Boolean) -> Unit = {},
    val onSetGeminiReasoningEffort: (String) -> Unit = {},
    val onSetGeminiBudgetTokens: (Int) -> Unit = {},
    val onSetGeminiTemperature: (Float) -> Unit = {},
    val onSetGeminiTopP: (Float) -> Unit = {},
    val onSetGeminiTopK: (Int) -> Unit = {},
    val onSetGeminiPromptMode: (GeminiPromptMode) -> Unit = {},
    val onSetGeminiSourceLang: (String) -> Unit = {},
    val onSetGeminiTargetLang: (String) -> Unit = {},
    val onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit = {},
    val onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit = {},
    val onSetGeminiCustomPromptModifier: (String) -> Unit = {},
    val onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit = {},
    val onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit = {},
    val onSetGeminiPrivateUnlocked: (Boolean) -> Unit = {},
    val onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit = {},
    val onSetTranslationProvider: (NovelTranslationProvider) -> Unit = {},
    val onSetOpenRouterBaseUrl: (String) -> Unit = {},
    val onSetOpenRouterApiKey: (String) -> Unit = {},
    val onSetOpenRouterModel: (String) -> Unit = {},
    val onRefreshOpenRouterModels: () -> Unit = {},
    val onTestOpenRouterConnection: () -> Unit = {},
    val onSetDeepSeekBaseUrl: (String) -> Unit = {},
    val onSetDeepSeekApiKey: (String) -> Unit = {},
    val onSetDeepSeekModel: (String) -> Unit = {},
    val onRefreshDeepSeekModels: () -> Unit = {},
    val onTestDeepSeekConnection: () -> Unit = {},
    val onSetMistralBaseUrl: (String) -> Unit = {},
    val onSetMistralApiKey: (String) -> Unit = {},
    val onSetMistralModel: (String) -> Unit = {},
    val onRefreshMistralModels: () -> Unit = {},
    val onTestMistralConnection: () -> Unit = {},
    val onSetNvidiaBaseUrl: (String) -> Unit = {},
    val onSetNvidiaApiKey: (String) -> Unit = {},
    val onSetNvidiaModel: (String) -> Unit = {},
    val onRefreshNvidiaModels: () -> Unit = {},
    val onTestNvidiaConnection: () -> Unit = {},
    val onSetOllamaCloudBaseUrl: (String) -> Unit = {},
    val onSetOllamaCloudApiKey: (String) -> Unit = {},
    val onSetOllamaCloudModel: (String) -> Unit = {},
    val onRefreshOllamaCloudModels: () -> Unit = {},
    val onTestOllamaCloudConnection: () -> Unit = {},
    val onStartGoogleTranslation: () -> Unit = {},
    val onStopGoogleTranslation: () -> Unit = {},
    val onResumeGoogleTranslation: () -> Unit = {},
    val onToggleGoogleTranslationVisibility: () -> Unit = {},
    val onClearGoogleTranslation: () -> Unit = {},
    val onSetGoogleTranslationEnabled: (Boolean) -> Unit = {},
    val onSetGoogleTranslationAutoStart: (Boolean) -> Unit = {},
    val onSetGoogleTranslationSourceLang: (String) -> Unit = {},
    val onSetGoogleTranslationTargetLang: (String) -> Unit = {},
    val onToggleTtsPlayback: (NovelTtsPlaybackStartRequest) -> Unit = {},
    val onStopTtsPlayback: () -> Unit = {},
    val onSkipPreviousTts: () -> Unit = {},
    val onSkipNextTts: () -> Unit = {},
    val onPauseTtsForManualNavigation: (NovelTtsPlaybackStartRequest) -> Unit = {},
    val onSetTtsEnginePackage: (String) -> Unit = {},
    val onSetTtsVoiceId: (String) -> Unit = {},
    val onSetTtsLocaleTag: (String) -> Unit = {},
    val onSetTtsSpeechRate: (Float) -> Unit = {},
    val onSetTtsPitch: (Float) -> Unit = {},
    val onDisableTts: () -> Unit = {},
    val onPreviewTtsVoice: (String) -> Unit = {},
    val onStopTtsVoicePreview: () -> Unit = {},
    val onOpenPreviousChapter: ((Long) -> Unit)? = null,
    val onOpenNextChapter: ((Long) -> Unit)? = null,
    val onPrepareAutoScrollHandoff: (targetChapterId: Long, speed: Int) -> Unit = { _, _ -> },
    val onConsumeAutoScrollHandoff: (chapterId: Long) -> NovelAutoScrollHandoffState? = { null },
    val onCancelAutoScrollHandoff: () -> Unit = {},
    val onRequestAutoScrollNextChapterPrefetch: () -> Unit = {},
    val onOpenChapter: ((Long) -> Unit)? = null,
    val onDownloadChapter: ((Long) -> Unit)? = null,
    val onSetShowReaderUi: (Boolean) -> Unit,
    val onOpenBottomSheet: () -> Unit = {},
    val onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    val onTranslateSelectedText: () -> Unit = {},
    val onRetrySelectedTextTranslation: () -> Unit = onTranslateSelectedText,
    val onDismissSelectedTextTranslation: () -> Unit = {},
    val onLookupSelectedTextDefinition: () -> Unit = {},
    val onRetryNovelDictionary: () -> Unit = onLookupSelectedTextDefinition,
    val onDismissNovelDictionary: () -> Unit = {},
    val onPlaySelectedTextPronunciation: (String) -> Unit = {},
    val loadBookEngineDocument: (suspend (NovelBookSection) -> NovelBookDocument)? = null,
    val onBookEngineLocationChanged: (NovelBookLocation) -> Unit = {},
    val onBookEngineSectionMeasured: (chapterId: Long, charCount: Int) -> Unit = { _, _ -> },
    val onBookModeCommandsExecuted: (List<Long>) -> Unit = {},
    val onBookModeScroll: (sectionIndex: Int, sectionFraction: Float) -> Unit = { _, _ -> },
    val onBookModeSectionMeasured: (chapterId: Long, charCount: Int) -> Unit = { _, _ -> },
    val onBookModeRetrySection: (sectionIndex: Int) -> Unit = {},
    val onBookModeDocumentReady: () -> Unit = {},
    val onPrepareWholeBook: () -> Unit = {},
    /**
     * Pre-compiled blocks of a book section, or null when the compiled book has none.
     *
     * Supplied by the screen model from the book artifact. When present, the native renderer
     * skips parsing the section HTML entirely, which is what makes opening and scrolling a
     * 50-100 chapter book instant instead of running Jsoup over a 200k character window on
     * every append.
     */
    val nativeBookBlocksForSection: (sectionIndex: Int) -> List<NovelRichContentBlock>? = { null },
)

/**
 * Appends the book renderer's own layout overrides to [baseCss].
 *
 * The stylesheet used to be assembled with an inline `buildString` inside `NovelReaderScreen`, so
 * every line of it counted towards that composable's own bytecode. The method grew past the size ART
 * is willing to JIT-compile, which left the reader running interpreted for its first frames - exactly
 * when the book is being restored and the user is looking at it. The literal lives here instead and
 * is only re-joined when the base stylesheet changes.
 */
private fun withNovelBookReaderContentOverrides(baseCss: String): String =
    baseCss + NOVEL_BOOK_READER_CONTENT_OVERRIDES_CSS

private val NOVEL_BOOK_READER_CONTENT_OVERRIDES_CSS = """

#an-book-content {
  padding-top: var(--an-reader-padding-top) !important;
  padding-bottom: var(--an-reader-padding-bottom) !important;
  padding-left: var(--an-reader-padding-left) !important;
  padding-right: var(--an-reader-padding-right) !important;
  background: var(--an-reader-bg) !important;
  color: var(--an-reader-fg) !important;
}
""".trimIndent()

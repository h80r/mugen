package eu.kanade.presentation.entries.novel

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun NovelScreen(
    state: NovelScreenModel.State.Success,
    isFromSource: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartReading: (() -> Unit)?,
    isReading: Boolean,
    onToggleFavorite: () -> Unit,
    onEditCategoryClicked: (() -> Unit)? = null,
    onEditNotesClicked: (() -> Unit)? = null,
    onClickEditInfo: (() -> Unit)? = null,
    onRefresh: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onGenresSearch: ((List<String>) -> Unit)? = null,
    onPosterLongClicked: (() -> Unit)? = null,
    onToggleAllChaptersRead: () -> Unit,
    onShare: (() -> Unit)?,
    onWebView: (() -> Unit)?,
    onSourceSettings: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    trackingCount: Int,
    onOpenBatchDownloadDialog: (() -> Unit)?,
    onOpenTranslatedDownloadDialog: (() -> Unit)?,
    onOpenEpubExportDialog: (() -> Unit)?,
    onChapterClick: (Long) -> Unit,
    onChapterTranslateClick: (Long) -> Unit,
    onChapterTranslateLongClick: (Long) -> Unit,
    onChapterTranslatedDownloadClick: (Long) -> Unit,
    onChapterTranslatedDownloadLongClick: (Long) -> Unit,
    onChapterTranslatedDownloadOpenFolder: (Long) -> Unit,
    onChapterReadToggle: (Long) -> Unit,
    onChapterBookmarkToggle: (Long) -> Unit,
    onChapterDownloadToggle: (Long) -> Unit,
    chapterSwipeStartAction: LibraryPreferences.NovelSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.NovelSwipeAction,
    onChapterSwipe: (Long, LibraryPreferences.NovelSwipeAction) -> Unit,
    onFilterButtonClicked: () -> Unit,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    chapterPageEnabled: Boolean,
    chapterPageCurrent: Int,
    chapterPageTotal: Int,
    chapterPageLoading: Boolean,
    onChapterPageChange: (Int) -> Unit,
    onChapterLongClick: (Long) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onMultiBookmarkClicked: (Boolean) -> Unit,
    onMultiMarkAsReadClicked: (Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (NovelChapter) -> Unit,
    onMultiDownloadClicked: () -> Unit,
    onMultiDeleteClicked: () -> Unit,
    onSaveScrollPosition: (Int, Int) -> Unit = { _, _ -> },
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
    onMakeBookClicked: (() -> Unit)? = null,
    onAppendBookClicked: (() -> Unit)? = null,
    onDeleteBookSourceChaptersClicked: (() -> Unit)? = null,
    onDeleteBookClicked: (() -> Unit)? = null,
    onToggleReadAsBook: ((Boolean) -> Unit)? = null,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val autoJumpToNextEnabled by uiPreferences.entryAutoJumpToNextNovel().collectAsState()
    val autoJumpToNextLabel = stringResource(
        if (autoJumpToNextEnabled) {
            AYMR.strings.action_disable_auto_jump_next_chapter
        } else {
            AYMR.strings.action_enable_auto_jump_next_chapter
        },
    )

    val onToggleAutoJumpToNext = {
        uiPreferences.entryAutoJumpToNextNovel().set(!autoJumpToNextEnabled)
    }

    val navigator = cafe.adriel.voyager.navigator.LocalNavigator.current

    NovelScreenAuroraImpl(
        state = state,
        isFromSource = isFromSource,
        snackbarHostState = snackbarHostState,
        nextUpdate = state.novel.expectedNextUpdate,
        onBack = { if (state.selectedChapterIds.isNotEmpty()) onAllChapterSelected(false) else onBack() },
        onStartReading = onStartReading,
        isReading = isReading,
        onToggleFavorite = onToggleFavorite,
        onEditCategoryClicked = onEditCategoryClicked,
        onEditNotesClicked = onEditNotesClicked,
        onClickEditInfo = onClickEditInfo,
        onRefresh = onRefresh,
        onSearch = onSearch,
        onSuggestionClick = onSuggestionClick,
        onGenreClick = onGenreClick,
        onGenreLongClick = onGenreLongClick,
        onGenresSearch = onGenresSearch,
        onPosterLongClicked = onPosterLongClicked,
        onShare = onShare,
        onWebView = onWebView,
        onClickOmniBuilder = if (state.novel.source == eu.kanade.tachiyomi.source.novel.OmniSource.OMNI_SOURCE_ID) {
            { navigator?.push(eu.kanade.tachiyomi.ui.entries.novel.OmniBuilderScreen(state.novel.url)) }
        } else {
            null
        },
        onMigrateClicked = onMigrateClicked,
        onTrackingClicked = onTrackingClicked,
        trackingCount = trackingCount,
        onOpenBatchDownloadDialog = onOpenBatchDownloadDialog,
        onOpenTranslatedDownloadDialog = onOpenTranslatedDownloadDialog,
        onOpenEpubExportDialog = onOpenEpubExportDialog,
        onChapterClick = onChapterClick,
        onChapterTranslateClick = onChapterTranslateClick,
        onChapterTranslateLongClick = onChapterTranslateLongClick,
        onChapterTranslatedDownloadClick = onChapterTranslatedDownloadClick,
        onChapterTranslatedDownloadLongClick = onChapterTranslatedDownloadLongClick,
        onChapterTranslatedDownloadOpenFolder = onChapterTranslatedDownloadOpenFolder,
        onChapterLongClick = onChapterLongClick,
        onChapterReadToggle = onChapterReadToggle,
        onChapterBookmarkToggle = onChapterBookmarkToggle,
        onChapterDownloadToggle = onChapterDownloadToggle,
        chapterSwipeStartAction = chapterSwipeStartAction,
        chapterSwipeEndAction = chapterSwipeEndAction,
        onChapterSwipe = onChapterSwipe,
        onFilterButtonClicked = onFilterButtonClicked,
        scanlatorChapterCounts = scanlatorChapterCounts,
        selectedScanlator = selectedScanlator,
        onScanlatorSelected = onScanlatorSelected,
        chapterPageEnabled = chapterPageEnabled,
        chapterPageCurrent = chapterPageCurrent,
        chapterPageTotal = chapterPageTotal,
        chapterPageLoading = chapterPageLoading,
        onChapterPageChange = onChapterPageChange,
        onToggleAllSelection = onAllChapterSelected,
        onInvertSelection = onInvertSelection,
        onMultiBookmarkClicked = onMultiBookmarkClicked,
        onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
        onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
        onMultiDownloadClicked = onMultiDownloadClicked,
        onMultiDeleteClicked = onMultiDeleteClicked,
        isAutoJumpToNextEnabled = autoJumpToNextEnabled,
        autoJumpToNextLabel = autoJumpToNextLabel,
        onToggleAutoJumpToNext = onToggleAutoJumpToNext,
        onRetrySuggestions = onRetrySuggestions,
        onOpenSuggestions = onOpenSuggestions,
        onMakeBookClicked = onMakeBookClicked,
        onAppendBookClicked = onAppendBookClicked,
        onDeleteBookSourceChaptersClicked = onDeleteBookSourceChaptersClicked,
        onDeleteBookClicked = onDeleteBookClicked,
        onToggleReadAsBook = onToggleReadAsBook,
    )
}

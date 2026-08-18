package eu.kanade.presentation.entries.manga

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
fun MangaScreen(
    state: MangaScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    navigateUp: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterList.Item>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onGenresSearch: ((List<String>) -> Unit)? = null,

    onFilterButtonClicked: () -> Unit,
    showScanlatorSelector: Boolean,
    scanlatorChapterCounts: Map<String, Int>,
    selectedScanlator: String?,
    onScanlatorSelected: (String?) -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    onClickEditInfo: (() -> Unit)? = null,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,

    // For chapter swipe
    onChapterSwipe: (ChapterList.Item, LibraryPreferences.ChapterSwipeAction) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
) {
    val navigator = LocalNavigator.currentOrThrow
    val onSettingsClicked: (() -> Unit)? = {
        navigator.push(MangaSourcePreferencesScreen(state.source.id))
    }.takeIf { state.source is ConfigurableSource }

    val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
    val autoJumpToNextEnabled by uiPreferences.entryAutoJumpToNextManga().collectAsStateWithLifecycle()
    val autoJumpToNextLabel = stringResource(
        if (autoJumpToNextEnabled) {
            AYMR.strings.action_disable_auto_jump_next_chapter
        } else {
            AYMR.strings.action_enable_auto_jump_next_chapter
        },
    )
    val onToggleAutoJumpToNext = {
        uiPreferences.entryAutoJumpToNextManga().set(!autoJumpToNextEnabled)
    }

    MangaScreenAuroraImpl(
        state = state,
        snackbarHostState = snackbarHostState,
        nextUpdate = nextUpdate,
        isTabletUi = isTabletUi,
        chapterSwipeStartAction = chapterSwipeStartAction,
        chapterSwipeEndAction = chapterSwipeEndAction,
        navigateUp = navigateUp,
        onChapterClicked = onChapterClicked,
        onDownloadChapter = onDownloadChapter,
        onAddToLibraryClicked = onAddToLibraryClicked,
        onWebViewClicked = onWebViewClicked,
        onWebViewLongClicked = onWebViewLongClicked,
        onTrackingClicked = onTrackingClicked,
        onTagSearch = onTagSearch,
        onGenreClick = onGenreClick,
        onGenreLongClick = onGenreLongClick,
        onGenresSearch = onGenresSearch,
        onFilterButtonClicked = onFilterButtonClicked,
        showScanlatorSelector = showScanlatorSelector,
        scanlatorChapterCounts = scanlatorChapterCounts,
        selectedScanlator = selectedScanlator,
        onScanlatorSelected = onScanlatorSelected,
        onRefresh = onRefresh,
        onContinueReading = onContinueReading,
        onSearch = onSearch,
        onSuggestionClick = onSuggestionClick,
        onCoverClicked = onCoverClicked,
        onShareClicked = onShareClicked,
        onDownloadActionClicked = onDownloadActionClicked,
        onEditCategoryClicked = onEditCategoryClicked,
        onEditFetchIntervalClicked = onEditFetchIntervalClicked,
        onEditNotesClicked = onEditNotesClicked,
        onMigrateClicked = onMigrateClicked,
        onClickEditInfo = onClickEditInfo,
        onMultiBookmarkClicked = onMultiBookmarkClicked,
        onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
        onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
        onMultiDeleteClicked = onMultiDeleteClicked,
        onChapterSwipe = onChapterSwipe,
        onChapterSelected = onChapterSelected,
        onAllChapterSelected = onAllChapterSelected,
        onInvertSelection = onInvertSelection,
        onSettingsClicked = onSettingsClicked,
        isAutoJumpToNextEnabled = autoJumpToNextEnabled,
        autoJumpToNextLabel = autoJumpToNextLabel,
        onToggleAutoJumpToNext = onToggleAutoJumpToNext,
        onRetrySuggestions = onRetrySuggestions,
        onOpenSuggestions = onOpenSuggestions,
    )
}

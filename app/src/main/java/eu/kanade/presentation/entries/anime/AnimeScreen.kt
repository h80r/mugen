package eu.kanade.presentation.entries.anime

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import aniyomi.domain.anime.SeasonAnime
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AnimeScreen(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (episode: Episode, alt: Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,

    // For tags menu
    onTagSearch: (String) -> Unit,
    onGenreClick: ((String) -> Unit)? = null,
    onGenreLongClick: ((String) -> Unit)? = null,
    onGenresSearch: ((List<String>) -> Unit)? = null,

    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
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
    changeAnimeSkipIntro: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,

    // For episode swipe
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,

    // Episode selection
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,

    // Season clicked
    onSeasonClicked: (SeasonAnime) -> Unit,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,

    // Dubbing selection
    onDubbingClicked: (() -> Unit)? = null,
    selectedDubbing: String? = null,
    onDownloadLongClick: ((Episode) -> Unit)? = null,

    // Metadata retry (Anilist/Shikimori)
    onRetryMetadata: () -> Unit,

    onClickEditInfo: (() -> Unit)? = null,
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
) {
    val uiPreferences = Injekt.get<eu.kanade.domain.ui.UiPreferences>()
    val autoJumpToNextEnabled by uiPreferences.entryAutoJumpToNextAnime().collectAsState()
    val autoJumpToNextLabel = stringResource(
        if (autoJumpToNextEnabled) {
            AYMR.strings.action_disable_auto_jump_next_episode
        } else {
            AYMR.strings.action_enable_auto_jump_next_episode
        },
    )
    val onToggleAutoJumpToNext = {
        uiPreferences.entryAutoJumpToNextAnime().set(!autoJumpToNextEnabled)
    }

    val navigator = LocalNavigator.currentOrThrow
    val onSettingsClicked: (() -> Unit)? = {
        navigator.push(AnimeSourcePreferencesScreen(state.source.id))
    }.takeIf { state.source is ConfigurableAnimeSource }

    AnimeScreenAuroraImpl(
        state = state,
        snackbarHostState = snackbarHostState,
        nextUpdate = nextUpdate,
        isTabletUi = isTabletUi,
        episodeSwipeStartAction = episodeSwipeStartAction,
        episodeSwipeEndAction = episodeSwipeEndAction,
        showNextEpisodeAirTime = showNextEpisodeAirTime,
        alwaysUseExternalPlayer = alwaysUseExternalPlayer,
        navigateUp = navigateUp,
        onEpisodeClicked = onEpisodeClicked,
        onDownloadEpisode = onDownloadEpisode,
        onAddToLibraryClicked = onAddToLibraryClicked,
        onWebViewClicked = onWebViewClicked,
        onWebViewLongClicked = onWebViewLongClicked,
        onTrackingClicked = onTrackingClicked,
        onTagSearch = onTagSearch,
        onGenreClick = onGenreClick,
        onGenreLongClick = onGenreLongClick,
        onGenresSearch = onGenresSearch,
        onFilterButtonClicked = onFilterButtonClicked,
        onRefresh = onRefresh,
        onContinueWatching = onContinueWatching,
        onSearch = onSearch,
        onSuggestionClick = onSuggestionClick,
        onCoverClicked = onCoverClicked,
        onShareClicked = onShareClicked,
        onDownloadActionClicked = onDownloadActionClicked,
        onEditCategoryClicked = onEditCategoryClicked,
        onEditFetchIntervalClicked = onEditFetchIntervalClicked,
        onEditNotesClicked = onEditNotesClicked,
        onMigrateClicked = onMigrateClicked,
        changeAnimeSkipIntro = changeAnimeSkipIntro,
        onMultiBookmarkClicked = onMultiBookmarkClicked,
        onMultiFillermarkClicked = onMultiFillermarkClicked,
        onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
        onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
        onMultiDeleteClicked = onMultiDeleteClicked,
        onEpisodeSwipe = onEpisodeSwipe,
        onEpisodeSelected = onEpisodeSelected,
        onAllEpisodeSelected = onAllEpisodeSelected,
        onInvertSelection = onInvertSelection,
        onSeasonClicked = onSeasonClicked,
        onContinueWatchingClicked = onContinueWatchingClicked,
        onDubbingClicked = onDubbingClicked,
        selectedDubbing = selectedDubbing,
        onDownloadLongClick = onDownloadLongClick,
        onRetryMetadata = onRetryMetadata,
        onSettingsClicked = onSettingsClicked,
        isAutoJumpToNextEnabled = autoJumpToNextEnabled,
        autoJumpToNextLabel = autoJumpToNextLabel,
        onToggleAutoJumpToNext = onToggleAutoJumpToNext,
        onClickEditInfo = onClickEditInfo,
        onRetrySuggestions = onRetrySuggestions,
        onOpenSuggestions = onOpenSuggestions,
    )
}

fun formatTime(milliseconds: Long, useDayFormat: Boolean = false): String {
    return if (useDayFormat) {
        String.format(
            Locale.getDefault(),
            "Airing in %02dd %02dh %02dm %02ds",
            TimeUnit.MILLISECONDS.toDays(milliseconds),
            TimeUnit.MILLISECONDS.toHours(milliseconds) -
                TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS.toDays(milliseconds)),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else if (milliseconds > 3600000L) {
        String.format(
            Locale.getDefault(),
            "%d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(milliseconds),
            TimeUnit.MILLISECONDS.toMinutes(milliseconds) -
                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(milliseconds)),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    } else {
        String.format(
            Locale.getDefault(),
            "%d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(milliseconds),
            TimeUnit.MILLISECONDS.toSeconds(milliseconds) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(milliseconds)),
        )
    }
}

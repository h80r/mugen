package eu.kanade.tachiyomi.ui.stats

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.h80r.mugen.R
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.stats.anime.animeStatsTab
import eu.kanade.tachiyomi.ui.stats.general.generalStatsTab
import eu.kanade.tachiyomi.ui.stats.manga.mangaStatsTab
import eu.kanade.tachiyomi.ui.stats.novel.novelStatsTab
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object StatsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 8u,
                title = stringResource(MR.strings.label_stats),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val canNavigateUp = remember { navigator.canPop }
        val navigateUp = if (canNavigateUp) {
            {
                navigator.pop()
                Unit
            }
        } else {
            null
        }

        val tabs = statsContentTabs()
            .map { tab ->
                when (tab) {
                    StatsContentTab.GENERAL -> generalStatsTab().copy(navigateUp = navigateUp)
                    StatsContentTab.ANIME -> animeStatsTab().copy(navigateUp = navigateUp)
                    StatsContentTab.MANGA -> mangaStatsTab().copy(navigateUp = navigateUp)
                    StatsContentTab.NOVEL -> novelStatsTab().copy(navigateUp = navigateUp)
                }
            }
            .toPersistentList()
        val state = rememberPagerState { tabs.size }

        TabbedScreenAurora(
            titleRes = MR.strings.label_stats,
            tabs = tabs,
            state = state,
            isMangaTab = { it == 2 },
            scrollable = false,
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

internal enum class StatsContentTab {
    GENERAL,
    ANIME,
    MANGA,
    NOVEL,
}

internal fun statsContentTabs(): List<StatsContentTab> {
    return listOf(
        StatsContentTab.GENERAL,
        StatsContentTab.ANIME,
        StatsContentTab.MANGA,
        StatsContentTab.NOVEL,
    )
}

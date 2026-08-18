package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.more.stats.SharedActivityStatsScreenModel

/**
 * Media-agnostic streak/comparison/yearly-activity block, shared identically
 * across the Anime/Manga/Novel Stats tabs.
 */
@Composable
fun SharedActivityStatsSection(
    screenModel: SharedActivityStatsScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by screenModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SharedActivityStreakCard(
            currentStreak = state.currentStreak,
            modifier = Modifier.fillMaxWidth(),
        )

        SharedActivityComparisonCard(
            currentMonth = state.currentMonthStats,
            previousMonth = state.previousMonthStats,
            modifier = Modifier.fillMaxWidth(),
        )

        SharedYearlyActivityGraph(
            yearlyStats = state.yearlyStats,
        )
    }
}

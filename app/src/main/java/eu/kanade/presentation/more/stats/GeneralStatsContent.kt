package eu.kanade.presentation.more.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.stats.components.SharedActivityStatsSection

@Composable
fun GeneralStatsContent(
    paddingValues: PaddingValues,
    activityStatsScreenModel: SharedActivityStatsScreenModel,
) {
    val layoutDirection = LocalLayoutDirection.current
    val lazyColumnContentPadding = remember(paddingValues, layoutDirection) {
        PaddingValues(
            start = paddingValues.calculateStartPadding(layoutDirection),
            top = paddingValues.calculateTopPadding() + 16.dp,
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = paddingValues.calculateBottomPadding(),
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = lazyColumnContentPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item {
                SharedActivityStatsSection(screenModel = activityStatsScreenModel)
            }
        }
    }
}

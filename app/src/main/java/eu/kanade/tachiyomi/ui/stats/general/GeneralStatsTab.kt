package eu.kanade.tachiyomi.ui.stats.general

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.stats.GeneralStatsContent
import eu.kanade.presentation.more.stats.SharedActivityStatsScreenModel
import tachiyomi.i18n.MR

@Composable
fun Screen.generalStatsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow

    val activityStatsScreenModel = rememberScreenModel { SharedActivityStatsScreenModel() }

    return TabContent(
        titleRes = MR.strings.pref_category_general,
        content = { contentPadding, _ ->
            GeneralStatsContent(
                paddingValues = contentPadding,
                activityStatsScreenModel = activityStatsScreenModel,
            )
        },
        navigateUp = navigator::pop,
    )
}

package eu.kanade.tachiyomi.ui.reader.viewer.curl

import android.graphics.PointF
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.GridNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Resolves a tap position (0..1 normalized) into a [NavigationRegion] for the manga curl viewer,
 * reusing the exact same `ViewerNavigation` machinery the legacy pager uses — mode selection,
 * `pagerNavInverted` inversion and custom tap zones — so tap zones behave identically under the
 * curl. This is the standalone equivalent of `PagerConfig.updateNavigation` (the pager builds its
 * navigator inside `PagerConfig`, which is `PagerViewer`-typed and not reusable here).
 */
class MangaCurlNavigation(
    private val readerPreferences: ReaderPreferences,
    private val isVertical: Boolean,
    scope: CoroutineScope,
) {

    private var invertMode: ReaderPreferences.TappingInvertMode =
        readerPreferences.pagerNavInverted().get()

    private var customTapZoneActions: String = readerPreferences.customTapZoneActions().get()

    private var navigation: ViewerNavigation = buildNavigation(readerPreferences.navigationModePager().get())

    init {
        readerPreferences.navigationModePager().changes()
            .onEach { navigation = buildNavigation(it) }
            .launchIn(scope)
        readerPreferences.pagerNavInverted().changes()
            .onEach {
                invertMode = it
                navigation.invertMode = it
            }
            .launchIn(scope)
        readerPreferences.customTapZoneActions().changes()
            .onEach {
                customTapZoneActions = it
                navigation = buildNavigation(readerPreferences.navigationModePager().get())
            }
            .launchIn(scope)
    }

    /** [x] and [y] are 0..1 fractions of the viewport. */
    fun getAction(x: Float, y: Float): NavigationRegion = navigation.getAction(PointF(x, y))

    private fun buildNavigation(mode: Int): ViewerNavigation {
        val nav = when (mode) {
            0 -> defaultNavigation()
            1 -> LNavigation()
            2 -> KindlishNavigation()
            3 -> EdgeNavigation()
            4 -> RightAndLeftNavigation()
            5 -> DisabledNavigation()
            6 -> GridNavigation(customTapZoneActions)
            else -> defaultNavigation()
        }
        nav.invertMode = invertMode
        return nav
    }

    private fun defaultNavigation(): ViewerNavigation =
        if (isVertical) LNavigation() else RightAndLeftNavigation()
}

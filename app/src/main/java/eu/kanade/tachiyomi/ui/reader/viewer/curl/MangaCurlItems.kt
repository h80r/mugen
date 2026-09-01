package eu.kanade.tachiyomi.ui.reader.viewer.curl

import android.content.res.Configuration
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.model.shouldShowChapterTransitionInfo
import eu.kanade.tachiyomi.ui.reader.viewer.calculateVisibleChapterGap
import eu.kanade.tachiyomi.ui.reader.viewer.pager.groupPagesForDoublePage

/**
 * Flags the curl item builder needs, so it does not depend on `PagerConfig` (which belongs to the
 * legacy pager viewer). Values come from `ReaderPreferences` in [MangaCurlViewer].
 */
data class MangaCurlItemConfig(
    val joinDoublePages: Boolean,
    val shiftDoublePages: Boolean,
    val alwaysShowChapterTransition: Boolean,
)

/**
 * Builds the curl viewer's flat item list the same way [PagerViewerAdapter.setChapters] does:
 * last two pages of the previous chapter, an optional [ChapterTransition.Prev], the current
 * chapter grouped through [groupPagesForDoublePage], an optional [ChapterTransition.Next], and
 * the first two pages of the next chapter. For right-to-left the whole list is reversed at the
 * end, exactly as the adapter does.
 *
 * The double-page pairing itself is delegated verbatim to the shared [groupPagesForDoublePage];
 * no new grouping rules are introduced here.
 *
 * @return the item list plus the [ChapterTransition.Next] built for the current chapter (mirrors
 *   `PagerViewerAdapter.nextTransition`, used for preload requests in task 2.11).
 */
fun buildMangaCurlItems(
    chapters: ViewerChapters,
    config: MangaCurlItemConfig,
    direction: ReadingDirection,
    orientation: Int,
): MangaCurlItems {
    val forceTransition = config.alwaysShowChapterTransition
    val items = mutableListOf<Any>()

    val prevHasMissingChapters = calculateVisibleChapterGap(
        chapters.currChapter,
        chapters.prevChapter,
        chapters.allChapters,
    ) > 0
    val nextHasMissingChapters = calculateVisibleChapterGap(
        chapters.nextChapter,
        chapters.currChapter,
        chapters.allChapters,
    ) > 0

    // Previous chapter: only the last couple of pages are needed — landing on one of them
    // promotes it to the current chapter.
    chapters.prevChapter?.pages?.let { items.addAll(it.takeLast(2)) }

    if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
        items.add(
            ChapterTransition.Prev(
                from = chapters.currChapter,
                to = chapters.prevChapter,
                showInfo = shouldShowChapterTransitionInfo(
                    alwaysShowChapterTransition = forceTransition,
                    hasMissingChapters = prevHasMissingChapters,
                    destinationChapter = chapters.prevChapter,
                ),
            ),
        )
    }

    chapters.currChapter.pages?.let { currPages ->
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val grouped = groupPagesForDoublePage(
            pages = currPages,
            joinDoublePages = config.joinDoublePages,
            shiftDoublePages = config.shiftDoublePages,
            isLandscape = isLandscape,
            isR2L = direction.isR2L,
        )
        items.addAll(grouped)
    }

    val nextTransition = ChapterTransition.Next(
        from = chapters.currChapter,
        to = chapters.nextChapter,
        showInfo = shouldShowChapterTransitionInfo(
            alwaysShowChapterTransition = forceTransition,
            hasMissingChapters = nextHasMissingChapters,
            destinationChapter = chapters.nextChapter,
        ),
    )
    if (nextHasMissingChapters || forceTransition || chapters.nextChapter?.state !is ReaderChapter.State.Loaded) {
        items.add(nextTransition)
    }

    chapters.nextChapter?.pages?.let { items.addAll(it.take(2)) }

    if (direction.isR2L) {
        items.reverse()
    }

    return MangaCurlItems(items = items, nextTransition = nextTransition)
}

data class MangaCurlItems(
    val items: List<Any>,
    val nextTransition: ChapterTransition.Next,
)

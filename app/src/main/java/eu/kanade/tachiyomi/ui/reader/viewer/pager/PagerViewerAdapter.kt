package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.model.shouldShowChapterTransitionInfo
import eu.kanade.tachiyomi.ui.reader.viewer.calculateVisibleChapterGap
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * List of currently set items.
     */
    var items: MutableList<Any> = mutableListOf()
        private set

    /**
     * Holds preprocessed items so they don't get removed when changing chapter
     */
    private var preprocessed: MutableMap<Int, InsertPage> = mutableMapOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    var currentChapter: ReaderChapter? = null

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext = viewer.activity.createReaderThemeContext()

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<Any>()

        // Forces chapter transition if there is missing chapters
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

        // Add previous chapter pages and transition.
        if (chapters.prevChapter != null) {
            // We only need to add the last few pages of the previous chapter, because it'll be
            // selected as the current chapter when one of those pages is selected.
            val prevPages = chapters.prevChapter.pages
            if (prevPages != null) {
                newItems.addAll(prevPages.takeLast(2))
            }
        }

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            newItems.add(
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

        var insertPageLastPage: InsertPage? = null

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            val pages = currPages.toMutableList()

            val lastPage = pages.last()

            // Insert preprocessed pages into current page list
            preprocessed.keys.sortedDescending()
                .forEach { key ->
                    if (lastPage.index == key) {
                        insertPageLastPage = preprocessed[key]
                    }
                    preprocessed[key]?.let {
                        if (key + 1 <= pages.size) {
                            pages.add(key + 1, it)
                        }
                    }
                }

            val isLandscape =
                viewer.activity.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val grouped = groupPagesForDoublePage(
                pages = pages,
                joinDoublePages = viewer.config.joinDoublePages,
                shiftDoublePages = viewer.config.shiftDoublePages,
                isLandscape = isLandscape,
                isR2L = viewer is R2LPagerViewer,
            )
            newItems.addAll(grouped)
        }

        currentChapter = chapters.currChapter

        // Add next chapter transition and pages.
        nextTransition = ChapterTransition.Next(
            from = chapters.currChapter,
            to = chapters.nextChapter,
            showInfo = shouldShowChapterTransitionInfo(
                alwaysShowChapterTransition = forceTransition,
                hasMissingChapters = nextHasMissingChapters,
                destinationChapter = chapters.nextChapter,
            ),
        )
            .also {
                if (nextHasMissingChapters ||
                    forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded
                ) {
                    newItems.add(it)
                }
            }

        if (chapters.nextChapter != null) {
            // Add at most two pages, because this chapter will be selected before the user can
            // swap more pages.
            val nextPages = chapters.nextChapter.pages
            if (nextPages != null) {
                newItems.addAll(nextPages.take(2))
            }
        }

        // Resets double-page splits, else insert pages get misplaced
        items.filterIsInstance<InsertPage>().also { items.removeAll(it) }

        if (viewer is R2LPagerViewer) {
            newItems.reverse()
        }

        preprocessed = mutableMapOf()
        items = newItems
        notifyDataSetChanged()

        // Will skip insert page otherwise
        if (insertPageLastPage != null) {
            viewer.moveToPage(insertPageLastPage)
        }
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return items.size
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        return when (val item = items[position]) {
            is JoinedReaderPage -> JoinedPagerPageHolder(readerThemedContext, viewer, item)
            is ReaderPage -> PagerPageHolder(readerThemedContext, viewer, item)
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Returns the adapter position of [page], resolving pages that have been merged into a
     * [JoinedReaderPage] spread. Returns the direct index when [page] is a standalone item, else the
     * index of the first [JoinedReaderPage] whose [JoinedReaderPage.firstPage] or
     * [JoinedReaderPage.secondPage] is identity-equal to [page], else -1.
     */
    fun indexOfPageOrJoined(page: ReaderPage): Int {
        val result = indexOfPageOrJoined(items, page)
        when {
            result == -1 -> doublePageLog(LogPriority.WARN) {
                "indexOfPageOrJoined(page=${page.number}): NOT FOUND in ${items.size} items"
            }
            items[result] === page -> doublePageLog {
                "indexOfPageOrJoined(page=${page.number}): resolved directly at $result"
            }
            else -> doublePageLog {
                "indexOfPageOrJoined(page=${page.number}): resolved via joined spread at $result"
            }
        }
        return result
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val position = items.indexOf(view.item)
            if (position != -1) {
                return position
            }
            // A ReaderPage merged into a JoinedReaderPage is never directly in items; resolve it via
            // joined membership so the joined holder survives notifyDataSetChanged on chapter append
            // instead of being destroyed and recreated.
            val item = view.item
            if (item is ReaderPage) {
                val joinedPosition = indexOfPageOrJoined(item)
                if (joinedPosition != -1) {
                    return joinedPosition
                }
            }
            logcat { "Position for ${view.item} not found" }
        }
        return POSITION_NONE
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage) {
        if (currentPage !is ReaderPage) return

        val currentIndex = items.indexOf(currentPage)

        // Put aside preprocessed pages for next chapter so they don't get removed when changing chapter
        if (currentPage.chapter.chapter.id != currentChapter?.chapter?.id) {
            preprocessed[newPage.index] = newPage
            return
        }

        val placeAtIndex = when (viewer) {
            is L2RPagerViewer,
            is VerticalPagerViewer,
            -> currentIndex + 1
            else -> currentIndex
        }

        // It will enter a endless cycle of insert pages
        if (viewer is R2LPagerViewer && placeAtIndex - 1 >= 0 && items[placeAtIndex - 1] is InsertPage) {
            return
        }

        // Same here it will enter a endless cycle of insert pages
        if (placeAtIndex < items.size && items[placeAtIndex] is InsertPage) {
            return
        }

        items.add(placeAtIndex, newPage)

        notifyDataSetChanged()
    }

    fun cleanupPageSplit() {
        val insertPages = items.filterIsInstance(InsertPage::class.java)
        items.removeAll(insertPages)
        notifyDataSetChanged()
    }

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext()
    }
}

/**
 * Returns the index of [page] in [items], resolving pages merged into a [JoinedReaderPage] spread.
 * Returns the direct index when [page] is a standalone item, else the index of the first
 * [JoinedReaderPage] whose [JoinedReaderPage.firstPage] or [JoinedReaderPage.secondPage] is
 * identity-equal to [page], else -1.
 */
internal fun indexOfPageOrJoined(items: List<Any>, page: ReaderPage): Int {
    val direct = items.indexOf(page)
    if (direct != -1) return direct
    return items.indexOfFirst {
        it is JoinedReaderPage && (it.firstPage === page || it.secondPage === page)
    }
}

internal fun groupPagesForDoublePage(
    pages: List<ReaderPage>,
    joinDoublePages: Boolean,
    shiftDoublePages: Boolean = false,
    isLandscape: Boolean,
    isR2L: Boolean,
): List<Any> {
    doublePageLog {
        "groupPagesForDoublePage(joinDoublePages=$joinDoublePages, shiftDoublePages=$shiftDoublePages, " +
            "isLandscape=$isLandscape, isR2L=$isR2L, pageCount=${pages.size})"
    }
    if (!joinDoublePages || !isLandscape) {
        return pages
    }

    val result = mutableListOf<Any>()
    var i = 0
    if (shiftDoublePages && pages.isNotEmpty()) {
        result.add(pages[0])
        i = 1
    }
    while (i < pages.size) {
        val currentPage = pages[i]
        if (currentPage.isWide) {
            result.add(currentPage)
            i++
            continue
        }

        val nextPage = pages.getOrNull(i + 1)
        if (nextPage != null && !nextPage.isWide) {
            val first = if (isR2L) nextPage else currentPage
            val second = if (isR2L) currentPage else nextPage
            result.add(JoinedReaderPage(first, second))
            i += 2
        } else {
            result.add(currentPage)
            i++
        }
    }
    doublePageLog {
        val joined = result.count { it is JoinedReaderPage }
        val plain = result.count { it is ReaderPage && it !is JoinedReaderPage }
        val other = result.size - joined - plain
        "groupPagesForDoublePage produced ${result.size} items: joined=$joined, single=$plain, other=$other"
    }
    return result
}

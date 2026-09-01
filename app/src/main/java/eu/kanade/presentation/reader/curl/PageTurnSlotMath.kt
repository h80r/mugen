package eu.kanade.presentation.reader.curl

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Which adjacent chapter, if any, the curl's virtual boundary slots point at. The virtual page space a
 * page-turn renderer lays out is `[previous chapter?] + content pages + [next chapter?]`, so the first and
 * last slots can be synthetic handoff pages rather than real content.
 *
 * Content-agnostic on purpose: the novel reader maps this onto its own `HorizontalChapterSwipeAction`, the
 * manga curl viewer onto a chapter-preload request. Neither meaning lives here.
 */
enum class PageTurnBoundaryTarget {
    NONE,
    PREVIOUS,
    NEXT,
}

/**
 * Real content-page index a page-turn renderer's [currentPage] (a virtual page index) maps to, clamped into
 * `0 until contentPageCount`. With no [hasPreviousChapter] handoff slot the two indices coincide.
 */
fun resolvePageTurnRendererProgressPageIndex(
    currentPage: Int,
    contentPageCount: Int = Int.MAX_VALUE,
    hasPreviousChapter: Boolean = false,
): Int {
    val safeContentPageCount = contentPageCount.coerceAtLeast(1)
    val offset = if (hasPreviousChapter) 1 else 0
    return (currentPage - offset).coerceIn(0, safeContentPageCount - 1)
}

/** Total virtual pages: the content pages plus up to one synthetic handoff slot on each side. */
fun resolvePageTurnRendererVirtualPageCount(
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): Int {
    return contentPageCount.coerceAtLeast(1) +
        (if (hasPreviousChapter) 1 else 0) +
        (if (hasNextChapter) 1 else 0)
}

/** Virtual page index a real [actualPageIndex] sits at, shifted past the leading handoff slot when present. */
fun resolvePageTurnRendererVirtualPageIndex(
    actualPageIndex: Int,
    hasPreviousChapter: Boolean,
): Int {
    return actualPageIndex.coerceAtLeast(0) + if (hasPreviousChapter) 1 else 0
}

/** Which adjacent chapter [currentPage] (a virtual page index) lands on, or [PageTurnBoundaryTarget.NONE]. */
fun resolvePageTurnRendererBoundaryChapterTarget(
    currentPage: Int,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): PageTurnBoundaryTarget {
    val virtualPageCount = resolvePageTurnRendererVirtualPageCount(
        contentPageCount = contentPageCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
    )
    return when {
        hasPreviousChapter && currentPage <= 0 -> PageTurnBoundaryTarget.PREVIOUS
        hasNextChapter && currentPage >= virtualPageCount - 1 -> PageTurnBoundaryTarget.NEXT
        else -> PageTurnBoundaryTarget.NONE
    }
}

/**
 * Same as [resolvePageTurnRendererBoundaryChapterTarget] but only reports a target once the fold has settled
 * (`abs(progress) <= 0.001f`), so a chapter is not opened mid-drag.
 */
fun resolvePageTurnRendererSettledBoundaryChapterTarget(
    currentPage: Int,
    progress: Float,
    contentPageCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
): PageTurnBoundaryTarget {
    val boundaryTarget = resolvePageTurnRendererBoundaryChapterTarget(
        currentPage = currentPage,
        contentPageCount = contentPageCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
    )
    return if (boundaryTarget != PageTurnBoundaryTarget.NONE && abs(progress) <= 0.001f) {
        boundaryTarget
    } else {
        PageTurnBoundaryTarget.NONE
    }
}

/**
 * Number of pager slots [contentPageCount] single-column pages collapse into when every slot shows
 * [columnsPerSpread] of them side by side. A trailing odd page still gets its own slot (rendered with an
 * empty second column), the same way a physical book's last page can be a lone right-hand page facing nothing.
 */
fun resolveSpreadSlotCount(contentPageCount: Int, columnsPerSpread: Int): Int {
    val safeColumns = columnsPerSpread.coerceAtLeast(1)
    return ceil(contentPageCount.coerceAtLeast(1).toDouble() / safeColumns).toInt().coerceAtLeast(1)
}

/** First single-column page index shown in spread slot [spreadSlot]. */
fun resolveSpreadSlotFirstPageIndex(spreadSlot: Int, columnsPerSpread: Int): Int {
    return spreadSlot.coerceAtLeast(0) * columnsPerSpread.coerceAtLeast(1)
}

/** Which spread slot [pageIndex] (a single-column page index) is shown in. */
fun resolveSpreadSlotForPageIndex(pageIndex: Int, columnsPerSpread: Int): Int {
    val safeColumns = columnsPerSpread.coerceAtLeast(1)
    return pageIndex.coerceAtLeast(0) / safeColumns
}
